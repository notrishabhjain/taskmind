package com.notrishabhjain.taskmind.domain.service

import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Rule-based, fully local extraction engine. Every decision is a named,
 * unit-tested rule; confidence is a deterministic heuristic score, not an AI
 * estimate. Documented weights:
 *
 *  +0.30 explicit action verb or payment-due cue
 *  +0.25 reminder language ("remind me", "reminder", "don't forget")
 *  +0.20 recognized due date expression
 *  +0.10 recognized clock time expression
 *  +0.10 clear task text (>= 2 meaningful words after cleanup)
 *  +0.05 imperative sentence start
 *  -0.15 very short content (< 15 characters of canonical text)
 *  -0.20 question form
 *  -0.10 boilerplate-heavy input (> 40% of the sentence stripped)
 *
 * Routing thresholds are owned exclusively by the intake funnel's
 * ConfidenceGate; this class never decides create/review/reject itself.
 */
class DeterministicNotificationTaskExtractor : NotificationTaskExtractor {

    override fun extract(
        capture: NotificationCapture,
        now: Instant,
        zone: ZoneId
    ): NotificationExtractionOutcome {
        val raw = capture.canonicalSourceText.trim()
        if (raw.isBlank()) return NotificationExtractionOutcome.NotActionable(REASON_EMPTY)

        val collapsed = collapse(raw)
        val lower = collapsed.lowercase()

        UNCONDITIONAL_RULES.firstOrNull { it.regex.containsMatchIn(lower) }?.let { rule ->
            return NotificationExtractionOutcome.NotActionable(rule.reason)
        }
        val hasVerb = ACTION_VERB_REGEX.containsMatchIn(lower)
        val reminderLanguage = REMINDER_REGEX.containsMatchIn(lower)
        val paymentCue = PAYMENT_DUE_REGEX.containsMatchIn(lower) || NOUN_CUE_REGEX.containsMatchIn(lower)
        if (!hasVerb && !reminderLanguage && !paymentCue) {
            GUARDED_RULES.firstOrNull { it.regex.containsMatchIn(lower) }?.let { rule ->
                return NotificationExtractionOutcome.NotActionable(rule.reason)
            }
        }

        val sentences = splitSentences(raw)
        val today = LocalDate.ofInstant(now, zone)

        var bestIndex = -1
        var bestSignal = SentenceSignal.NONE
        sentences.forEachIndexed { index, sentence ->
            val signal = analyzeSentence(sentence.cleaned.lowercase())
            if (signal.score > bestSignal.score) {
                bestSignal = signal
                bestIndex = index
            }
        }
        if (bestIndex < 0) return NotificationExtractionOutcome.NotActionable(REASON_NO_SIGNAL)

        val chosenRaw = sentences[bestIndex].raw
        val chosenLower = sentences[bestIndex].cleaned.lowercase()

        val due = resolveDue(chosenLower, today, zone) ?: resolveDue(lower, today, zone) ?: DueResolution.EMPTY
        val title = deriveTitle(chosenRaw, due.spans)
        if (title.words < MIN_TITLE_WORDS) {
            return NotificationExtractionOutcome.NotActionable(REASON_NO_SIGNAL)
        }

        var score = 0.0
        if (bestSignal.hasVerb) score += WEIGHT_VERB
        if (bestSignal.hasReminder || reminderLanguage) score += WEIGHT_REMINDER
        if (due.dateFound) score += WEIGHT_DUE_DATE
        if (due.timeFound) score += WEIGHT_TIME
        score += WEIGHT_CLEAR_TEXT
        if (title.startsWithVerb) score += WEIGHT_IMPERATIVE
        if (collapsed.length < SHORT_CONTENT_CHARS) score -= WEIGHT_SHORT_CONTENT
        if (title.questionForm) score -= WEIGHT_QUESTION_FORM
        if (title.strippedRatio > BOILERPLATE_RATIO) score -= WEIGHT_BOILERPLATE

        val confidence = Math.round(score.coerceIn(0.0, 1.0) * 100) / 100.0

        val reasons = buildList {
            title.detectedVerb?.let { add("action '$it'") }
            if (bestSignal.hasReminder || reminderLanguage) add("reminder language")
            due.description?.let { add("due '$it'") }
        }
        val reasoning = if (reasons.isEmpty()) "Actionable notification detected." else reasons.joinToString("; ", "Detected ", ".")

        return NotificationExtractionOutcome.Actionable(
            NotificationExtraction(
                title = title.value,
                notes = null,
                dueAt = due.instant,
                confidence = confidence,
                evidence = chosenRaw,
                reasoning = reasoning,
                modelId = MODEL_ID
            )
        )
    }

    private data class SentenceSignal(
        val score: Int,
        val hasVerb: Boolean,
        val hasReminder: Boolean
    ) {
        companion object {
            val NONE = SentenceSignal(0, hasVerb = false, hasReminder = false)
        }
    }

    private fun analyzeSentence(sentenceLower: String): SentenceSignal {
        val hasVerb = ACTION_VERB_REGEX.containsMatchIn(sentenceLower) ||
            PAYMENT_DUE_REGEX.containsMatchIn(sentenceLower) ||
            NOUN_CUE_REGEX.containsMatchIn(sentenceLower)
        val hasReminder = REMINDER_REGEX.containsMatchIn(sentenceLower)
        val hasDue = DUE_HINT_REGEX.containsMatchIn(sentenceLower)
        val score = (listOf(hasVerb, hasReminder, hasDue).count { it }) + 1
        return SentenceSignal(score, hasVerb, hasReminder)
    }

    private data class TitleDerivation(
        val value: String,
        val words: Int,
        val strippedRatio: Double,
        val startsWithVerb: Boolean,
        val questionForm: Boolean,
        val detectedVerb: String?
    )

    private data class TitleToken(val orig: String, val lower: String)

    private fun deriveTitle(rawSentence: String, dueSpans: List<IntRange>): TitleDerivation {
        val withoutUrls = URL_REGEX.replace(rawSentence, " ")
        val cleaned = collapse(withoutUrls)
        val cleanedLower = cleaned.lowercase()
        val questionForm = cleaned.endsWith("?") || QUESTION_STARTERS.any { cleanedLower.startsWith(it) }

        var tokens = cleaned.split(' ').filter { it.isNotBlank() }.map { TitleToken(it, it.lowercase()) }
        tokens = tokens.withIndex()
            .filterNot { (index, token) -> tokenInAnySpan(index, token.orig, cleanedLower, dueSpans) }
            .map { it.value }
        tokens = stripLeadingPhrases(tokens)
        tokens = tokens.dropWhile { it.lower in LEADING_DROP_TOKENS }
        while (tokens.lastOrNull()?.lower in TRAILING_DROP_TOKENS) {
            tokens = tokens.dropLast(1)
        }

        if (tokens.isEmpty()) {
            return TitleDerivation("", 0, 1.0, startsWithVerb = false, questionForm = questionForm, detectedVerb = null)
        }

        val explicitVerb = ACTION_VERBS_SORTED.firstOrNull { verb ->
            tokens.any { it.lower == verb || it.lower.startsWith(verb) }
        }
        val paymentTemplate = explicitVerb == null && PAYMENT_OBJECT_REGEX.containsMatchIn(cleanedLower)
        if (paymentTemplate) {
            tokens = listOf(TitleToken("Pay", "pay")) + tokens.filter { token ->
                token.lower !in PAYMENT_NON_OBJECT_TOKENS && !AMOUNT_REGEX.matches(token.lower)
            }
        }

        val title = tokens.joinToString(" ") { it.orig }
            .replaceFirstChar { it.uppercaseChar() }
            .trimEnd(*TITLE_EDGE_CHARS)
        val ratio = 1.0 - title.length.toDouble() / cleaned.length.coerceAtLeast(1)
        val detectedVerb = explicitVerb ?: if (paymentTemplate) "pay" else null
        return TitleDerivation(
            value = title,
            words = tokens.size,
            strippedRatio = ratio,
            startsWithVerb = explicitVerb != null || paymentTemplate,
            questionForm = questionForm,
            detectedVerb = detectedVerb
        )
    }

    /** Span containment ignores trailing punctuation ("tomorrow." still matches "tomorrow"). */
    private fun tokenInAnySpan(
        tokenIndex: Int,
        token: String,
        sentenceLower: String,
        spans: List<IntRange>
    ): Boolean {
        val coreLength = token.trimEnd(*TITLE_EDGE_CHARS).length
        if (coreLength == 0) return false
        var offset = 0
        var current = -1
        for (candidate in sentenceLower.split(' ')) {
            current++
            val coreRange = offset until (offset + coreLength)
            if (spans.any { coreRange.first >= it.first && coreRange.last <= it.last } && current == tokenIndex) {
                return true
            }
            offset += candidate.length + 1
        }
        return false
    }

    private fun stripLeadingPhrases(tokens: List<TitleToken>): List<TitleToken> {
        var current = tokens
        while (true) {
            val phrase = LEADING_PREFIX_PHRASES.firstOrNull { candidate ->
                candidate.size <= current.size && current.take(candidate.size).map { it.lower } == candidate
            } ?: return current
            current = current.drop(phrase.size)
        }
    }

    private data class DueResolution(
        val instant: Instant?,
        val description: String?,
        val dateFound: Boolean,
        val timeFound: Boolean,
        val spans: List<IntRange>
    ) {
        companion object {
            val EMPTY = DueResolution(null, null, dateFound = false, timeFound = false, spans = emptyList())
        }
    }

    private fun resolveDue(sentenceLower: String, today: LocalDate, zone: ZoneId): DueResolution? {
        val date = resolveDate(sentenceLower, today)
        val time = resolveTime(sentenceLower)
        val partOfDay = PART_OF_DAY_REGEX.find(sentenceLower)
        if (date == null && time == null && partOfDay == null) return null

        val localDate = date?.localDate(today) ?: today
        val defaultFromPart = when (partOfDay?.value) {
            "this morning", "morning" -> MORNING_TIME
            "this afternoon", "afternoon" -> AFTERNOON_TIME
            else -> EVENING_TIME
        }
        val localTime = when {
            time != null -> time.localTime
            partOfDay != null -> defaultFromPart
            date?.kind == DateKind.TONIGHT -> TONIGHT_TIME
            else -> DEFAULT_TIME
        }

        val parts = buildList {
            date?.label?.let(::add)
            time?.label?.let(::add)
        }
        val description = when {
            parts.isEmpty() -> partOfDay?.value
            partOfDay != null && time == null -> parts.joinToString(" ") + " " + partOfDay.value
            else -> parts.joinToString(" at ")
        }
        val spans = buildList {
            date?.span?.let(::add)
            time?.span?.let(::add)
            partOfDay?.range?.let(::add)
        }
        val instant = ZonedDateTime.of(localDate, localTime, zone).toInstant()
        return DueResolution(instant, description, dateFound = date != null, timeFound = time != null, spans = spans)
    }

    private enum class DateKind { TODAY, TONIGHT, TOMORROW, DAY_AFTER_TOMORROW, NEXT_WEEK, WEEKDAY_PLAIN, WEEKDAY_NEXT, EXPLICIT_DATE }

    private data class ResolvedDate(val kind: DateKind, val label: String, val span: IntRange?, val weekdayValue: Int? = null, val explicit: LocalDate? = null)

    private fun ResolvedDate.localDate(today: LocalDate): LocalDate = when (kind) {
        DateKind.TODAY, DateKind.TONIGHT -> today
        DateKind.TOMORROW -> today.plusDays(1)
        DateKind.DAY_AFTER_TOMORROW -> today.plusDays(2)
        DateKind.NEXT_WEEK -> today.plusDays(NEXT_WEEK_DAYS)
        DateKind.WEEKDAY_PLAIN -> nextWeekday(today, weekdayValue!!, extraWeeks = 0)
        DateKind.WEEKDAY_NEXT -> nextWeekday(today, weekdayValue!!, extraWeeks = 1)
        DateKind.EXPLICIT_DATE -> explicit!!
    }

    private fun resolveDate(sentenceLower: String, today: LocalDate): ResolvedDate? {
        DAY_AFTER_TOMORROW_REGEX.find(sentenceLower)?.let {
            return ResolvedDate(DateKind.DAY_AFTER_TOMORROW, it.value, it.range)
        }
        TONIGHT_REGEX.find(sentenceLower)?.let {
            return ResolvedDate(DateKind.TONIGHT, it.value, it.range)
        }
        TODAY_REGEX.find(sentenceLower)?.let {
            return ResolvedDate(DateKind.TODAY, it.value, it.range)
        }
        TOMORROW_REGEX.find(sentenceLower)?.let {
            return ResolvedDate(DateKind.TOMORROW, it.value, it.range)
        }
        NEXT_WEEK_REGEX.find(sentenceLower)?.let {
            return ResolvedDate(DateKind.NEXT_WEEK, it.value, it.range)
        }
        NEXT_WEEKDAY_REGEX.find(sentenceLower)?.let { match ->
            return ResolvedDate(
                DateKind.WEEKDAY_NEXT,
                match.value,
                match.range,
                weekdayValue = WEEKDAY_VALUES[match.groupValues[1]]
            )
        }
        WEEKDAY_REGEX.find(sentenceLower)?.let { match ->
            return ResolvedDate(
                DateKind.WEEKDAY_PLAIN,
                match.value,
                match.range,
                weekdayValue = WEEKDAY_VALUES[match.groupValues[1]]
            )
        }
        resolveExplicitDate(sentenceLower, today)?.let { return it }
        return null
    }

    private fun resolveExplicitDate(sentenceLower: String, today: LocalDate): ResolvedDate? {
        DMY_DATE_REGEX.find(sentenceLower)?.let { match ->
            val month = MONTHS[match.groupValues[2]] ?: return null
            val day = match.groupValues[1].toIntOrNull() ?: return null
            val date = buildExplicitDate(month, day, match.groupValues[3].toIntOrNull(), today) ?: return null
            return ResolvedDate(DateKind.EXPLICIT_DATE, match.value.trim(), match.range, explicit = date)
        }
        MDY_DATE_REGEX.find(sentenceLower)?.let { match ->
            val month = MONTHS[match.groupValues[1]] ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val date = buildExplicitDate(month, day, match.groupValues[3].toIntOrNull(), today) ?: return null
            return ResolvedDate(DateKind.EXPLICIT_DATE, match.value.trim(), match.range, explicit = date)
        }
        return null
    }

    private data class ResolvedTime(val localTime: LocalTime, val label: String, val span: IntRange)

    private fun resolveTime(sentenceLower: String): ResolvedTime? {
        TIME_12H_REGEX.find(sentenceLower)?.let { match ->
            val hour12 = match.groupValues[1].toIntOrNull()
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            if (hour12 == null || hour12 !in 1..12) return@let
            val hour = when (match.groupValues[3]) {
                "pm" -> if (hour12 == 12) 12 else hour12 + 12
                else -> if (hour12 == 12) 0 else hour12
            }
            return ResolvedTime(LocalTime.of(hour, minute), match.value.trim(), match.range)
        }
        NOON_MIDNIGHT_REGEX.find(sentenceLower)?.let { match ->
            val time = if (match.value == "noon") LocalTime.NOON else LocalTime.MIDNIGHT
            return ResolvedTime(time, match.value, match.range)
        }
        TIME_24H_REGEX.find(sentenceLower)?.let { match ->
            return ResolvedTime(LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt()), match.value, match.range)
        }
        return null
    }

    private fun nextWeekday(today: LocalDate, weekdayValue: Int, extraWeeks: Int): LocalDate {
        var daysAhead = ((weekdayValue - today.dayOfWeek.value) + DAYS_PER_WEEK) % DAYS_PER_WEEK
        if (daysAhead == 0) daysAhead = DAYS_PER_WEEK
        return today.plusDays((daysAhead + extraWeeks * DAYS_PER_WEEK).toLong())
    }

    private fun buildExplicitDate(month: Month, day: Int, year: Int?, today: LocalDate): LocalDate? {
        if (day !in 1..month.maxLength()) return null
        val resolvedYear = year ?: run {
            val attempt = LocalDate.of(today.year, month, day)
            if (attempt.isBefore(today)) today.year + 1 else today.year
        }
        return runCatching { LocalDate.of(resolvedYear, month, day) }.getOrNull()
    }

    private data class Sentence(val raw: String, val cleaned: String)

    private fun splitSentences(raw: String): List<Sentence> =
        raw.split('\n')
            .flatMap { it.split(SENTENCE_SPLIT_REGEX) }
            .map { Sentence(raw = collapse(it), cleaned = collapse(URL_REGEX.replace(it, " "))) }
            .filter { it.cleaned.length >= MIN_SENTENCE_CHARS || it.raw.length >= MIN_SENTENCE_CHARS }

    private fun collapse(value: String): String = value.replace(WHITESPACE, " ").trim()

    private class NonActionableRule(val regex: Regex, val reason: String)

    companion object {
        const val MODEL_ID = "deterministic-notification-rules-v1"

        private const val REASON_EMPTY = "empty content"
        private const val REASON_NO_SIGNAL = "no actionable signal"

        private const val WEIGHT_VERB = 0.30
        private const val WEIGHT_REMINDER = 0.25
        private const val WEIGHT_DUE_DATE = 0.20
        private const val WEIGHT_TIME = 0.10
        private const val WEIGHT_CLEAR_TEXT = 0.10
        private const val WEIGHT_IMPERATIVE = 0.05
        private const val WEIGHT_SHORT_CONTENT = 0.15
        private const val WEIGHT_QUESTION_FORM = 0.20
        private const val WEIGHT_BOILERPLATE = 0.10

        private const val MIN_SENTENCE_CHARS = 4
        private const val MIN_TITLE_WORDS = 2
        private const val SHORT_CONTENT_CHARS = 15
        private const val BOILERPLATE_RATIO = 0.40
        private const val DAYS_PER_WEEK = 7
        private const val NEXT_WEEK_DAYS = 7L

        private val DEFAULT_TIME = LocalTime.of(9, 0)
        private val MORNING_TIME = LocalTime.of(9, 0)
        private val AFTERNOON_TIME = LocalTime.of(14, 0)
        private val EVENING_TIME = LocalTime.of(18, 0)
        private val TONIGHT_TIME = LocalTime.of(20, 0)

        private val WHITESPACE = Regex("\\s+")
        private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?])\\s+|;")
        private val URL_REGEX = Regex("""https?://\S+|\bwww\.\S+""")
        private val AMOUNT_REGEX = Regex("""(?:₹|$|€|£)\s*[\d,]+(?:\.\d+)?|rs\.?\s*[\d,]+(?:\.\d+)?""")

        private val OTP_REGEX = Regex("""\b(?:otp|one[\s-]?time\s+(?:password|code|pin)|verification\s+code|security\s+code)\b""")
        private val PROMO_REGEX = Regex(
            """\b(?:sale|discount|offers?|deals?|coupons?|cashback|promo(?:tion)?|shop\s+now|buy\s+now|limited\s+(?:time|period)|flat\s+\d{1,3}\s*%|\d{1,3}\s*%\s*off)\b"""
        )
        private val SECURITY_ALERT_REGEX = Regex(
            """\b(?:login\s+alert|sign[- ]?in\s+alert|security\s+alert|new\s+device|new\s+(?:login|sign[\s-]?in)|(?:password|pin)\s+(?:changed|reset)|unusual\s+(?:sign[- ]?in|login|activity))\b"""
        )
        private val MONEY_INFO_REGEX = Regex(
            """\b(?:balance|credited|debited|deposited|statement|refund|paid\s+successfully|payment\s+successful|transaction\s+(?:successful|failed))\b"""
        )
        private val DELIVERY_STATUS_REGEX = Regex(
            """\b(?:delivered|shipped|dispatched|out\s+for\s+delivery|arrived|arriving)\b"""
        )
        private val UNCONDITIONAL_RULES = listOf(
            NonActionableRule(OTP_REGEX, "otp"),
            NonActionableRule(PROMO_REGEX, "promotional"),
            NonActionableRule(SECURITY_ALERT_REGEX, "security alert without action")
        )
        private val GUARDED_RULES = listOf(
            NonActionableRule(MONEY_INFO_REGEX, "informational money update"),
            NonActionableRule(DELIVERY_STATUS_REGEX, "delivery status without action")
        )

        private val REMINDER_REGEX = Regex("""\b(?:reminder|remind(?:\s+me)?|don'?t\s+forget|remember\s+to)\b""")
        private val ACTION_VERBS = setOf(
            "call", "send", "email", "reply", "submit", "pay", "buy", "book",
            "schedule", "attend", "complete", "finish", "review", "check",
            "collect", "fetch", "renew", "register", "confirm", "respond",
            "return", "order", "pick", "file", "sign"
        )

        /** Sorted so regex alternation and verb detection are deterministic. */
        private val ACTION_VERBS_SORTED = ACTION_VERBS.toList().sorted()
        private val ACTION_VERB_REGEX = Regex("\\b(?:" + ACTION_VERBS_SORTED.joinToString("|") + ")\\w*\\b")
        private val NOUN_CUE_REGEX = Regex(
            """\b(?:meeting|appointment|interview|session|deadline|bill|invoice)\b"""
        )
        private val PAYMENT_DUE_REGEX = Regex(
            """\b(?:bill|payment|premium|emi|recharge|subscription|fees?|invoice)\b[^.!?]*\b(?:due|overdue|expir\w*|renew)\b""" +
                """|\b(?:due|overdue)\b[^.!?]*\b(?:bill|payment|premium|emi|recharge|subscription|fees?|invoice)\b"""
        )
        private val PAYMENT_OBJECT_REGEX = Regex("""\b(?:bill|premium|emi|recharge|subscription|invoice|fees?)\b""")
        private val PAYMENT_NON_OBJECT_TOKENS = setOf(
            "payment", "due", "overdue", "is", "are", "was", "were", "has",
            "been", "the", "a", "an", "of", "and", "for", "to", "in", "on",
            "at", "by", "from", "with", "your", "my", "our", "please"
        )
        private val QUESTION_STARTERS = listOf(
            "is ", "are ", "am ", "can ", "could ", "would ", "should ",
            "do ", "does ", "did ", "will ", "shall "
        )
        private val LEADING_PREFIX_PHRASES = listOf(
            listOf("remind", "me", "to"),
            listOf("don't", "forget", "to"),
            listOf("dont", "forget", "to"),
            listOf("remember", "to"),
            listOf("please"),
            listOf("kindly"),
            listOf("pls"),
            listOf("plz")
        )
        private val LEADING_DROP_TOKENS = setOf("your", "my", "our", "the", "a", "an")
        private val TRAILING_DROP_TOKENS = setOf(
            "at", "on", "by", "to", "before", "for", "and", "of", "please",
            "due", "is", "are", "was", "were", "has", "been", "overdue"
        )
        private val TITLE_EDGE_CHARS = charArrayOf('.', ',', '!', '?', ':', ';', '"', '\'')

        private val TODAY_REGEX = Regex("""\btoday\b""")
        private val TONIGHT_REGEX = Regex("""\btonight\b""")
        private val TOMORROW_REGEX = Regex("""\b(?:tomorrow|tmr)\b""")
        private val DAY_AFTER_TOMORROW_REGEX = Regex("""\bday\s+after\s+tomorrow\b""")
        private val NEXT_WEEK_REGEX = Regex("""\bnext\s+week\b""")
        private val PART_OF_DAY_REGEX = Regex("""\b(?:this\s+)?(?:morning|afternoon|evening|night)\b""")
        private val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        private val WEEKDAY_VALUES = WEEKDAYS.withIndex().associate { (index, name) -> name to index + 1 }
        private val WEEKDAY_ALTERNATION = WEEKDAYS.joinToString("|")
        private val WEEKDAY_REGEX = Regex("""\b($WEEKDAY_ALTERNATION)\b""")
        private val NEXT_WEEKDAY_REGEX = Regex("""\bnext\s+($WEEKDAY_ALTERNATION)\b""")
        private val MONTHS = mapOf(
            "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mar" to Month.MARCH,
            "apr" to Month.APRIL, "may" to Month.MAY, "jun" to Month.JUNE,
            "jul" to Month.JULY, "aug" to Month.AUGUST, "sep" to Month.SEPTEMBER,
            "oct" to Month.OCTOBER, "nov" to Month.NOVEMBER, "dec" to Month.DECEMBER
        )
        private val MONTH_ALTERNATION = MONTHS.keys.joinToString("|")
        private val DMY_DATE_REGEX = Regex(
            """\b(\d{1,2})(?:st|nd|rd|th)?\s+($MONTH_ALTERNATION)[a-z]*(?:\.|,)?(?:\s+(\d{4}))?\b"""
        )
        private val MDY_DATE_REGEX = Regex(
            """\b($MONTH_ALTERNATION)[a-z]*(?:\.|,)?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b"""
        )
        private val TIME_12H_REGEX = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""")
        private val TIME_24H_REGEX = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
        private val NOON_MIDNIGHT_REGEX = Regex("""\b(noon|midnight)\b""")
        private val DUE_HINT_REGEX = Regex(
            """\b(?:today|tonight|tomorrow|tmr|next\s+(?:week|$WEEKDAY_ALTERNATION)|$WEEKDAY_ALTERNATION|(?:$MONTH_ALTERNATION)[a-z]*)\b|\b\d{1,2}(?::\d{2})?\s*(?:am|pm)\b|\b[01]?\d:[0-5]\d\b|\b(?:noon|midnight)\b"""
        )
    }
}
