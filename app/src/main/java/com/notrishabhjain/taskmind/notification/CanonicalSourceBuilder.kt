package com.notrishabhjain.taskmind.notification

/**
 * Deterministic canonical-source builder over [NotificationSnapshot].
 *
 * Guarantees:
 * - fixed block order: CONVERSATION, HISTORIC messages, MSG messages,
 *   TITLE, TEXT, BIG_TEXT, SUB_TEXT, INFO_TEXT, SUMMARY;
 * - values are inserted verbatim (outer-trimmed only; never lower-cased or
 *   paraphrased) so evidence validation remains mechanical substring matching;
 * - explicit sentinels keep sender/message boundaries machine-readable;
 * - duplicate suppression: a presentation field is omitted when its content is
 *   already covered by the messaging block, and TEXT is omitted when BIG_TEXT
 *   fully contains it (messaging apps repeat the newest message in both);
 * - blank/null fields emit nothing;
 * - identical snapshots always produce byte-identical output;
 * - an all-empty snapshot produces an empty string.
 */
object CanonicalSourceBuilder {

    private val WHITESPACE = Regex("\\s+")

    fun build(snapshot: NotificationSnapshot): String {
        val lines = mutableListOf<String>()
        val messageTexts = mutableListOf<String>()

        snapshot.conversation?.let { conversation ->
            conversation.title?.trim()?.takeIf { it.isNotEmpty() }?.let { title ->
                val groupMarker = if (conversation.isGroup) " [GROUP]" else ""
                lines += "CONVERSATION: $title$groupMarker"
            }
            conversation.messages.filter { it.historic }.forEach { message ->
                appendMessage(lines, messageTexts, "HISTORIC", message)
            }
            conversation.messages.filterNot { it.historic }.forEach { message ->
                appendMessage(lines, messageTexts, "MSG", message)
            }
        }

        addField(lines, messageTexts, "TITLE", snapshot.title)

        snapshot.text?.let { text ->
            if (!coveredByMessages(messageTexts, text) && !containedIn(bigOf(snapshot), text)) {
                appendField(lines, "TEXT", text)
            }
        }
        snapshot.bigText?.let { bigText ->
            if (!coveredByMessages(messageTexts, bigText)) {
                appendField(lines, "BIG_TEXT", bigText)
            }
        }

        addField(lines, messageTexts, "SUB_TEXT", snapshot.subText)
        addField(lines, messageTexts, "INFO_TEXT", snapshot.infoText)
        addField(lines, messageTexts, "SUMMARY", snapshot.summaryText)

        return lines.joinToString("\n")
    }

    private fun appendMessage(
        lines: MutableList<String>,
        messageTexts: MutableList<String>,
        prefix: String,
        message: MessageEntry
    ) {
        val senderPart = message.sender?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it: " } ?: ""
        val timePart = message.timestampMs?.toString()?.let { "$it " } ?: ""
        lines += "$prefix $timePart$senderPart${message.text.trim()}"
        messageTexts += normalize(message.text)
    }

    private fun addField(
        lines: MutableList<String>,
        messageTexts: List<String>,
        label: String,
        value: String?
    ) {
        value?.let { v ->
            if (!coveredByMessages(messageTexts, v)) appendField(lines, label, v)
        }
    }

    /** A presentation value already present inside the messaging block adds nothing new. */
    private fun coveredByMessages(messageTexts: List<String>, value: String): Boolean =
        messageTexts.any { it.contains(normalize(value)) }

    private fun bigOf(snapshot: NotificationSnapshot): String? =
        snapshot.bigText?.trim()?.ifBlank { null }

    private fun containedIn(containerNormalized: String?, value: String): Boolean =
        containerNormalized?.contains(normalize(value)) == true

    private fun appendField(lines: MutableList<String>, label: String, value: String) {
        lines += "$label: ${value.trim()}"
    }

    private fun normalize(value: String): String = value.replace(WHITESPACE, " ").trim().lowercase()
}
