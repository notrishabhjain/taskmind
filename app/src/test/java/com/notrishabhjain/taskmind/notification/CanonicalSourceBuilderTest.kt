package com.notrishabhjain.taskmind.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalSourceBuilderTest {

    private fun snapshot(
        title: String? = null,
        text: String? = null,
        bigText: String? = null,
        subText: String? = null,
        infoText: String? = null,
        summaryText: String? = null,
        conversation: ConversationContext? = null
    ) = NotificationSnapshot(
        packageName = "com.whatsapp",
        notificationKey = "k1",
        notificationId = 7,
        tag = null,
        postTimeMs = 100L,
        appLabel = "WhatsApp",
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        infoText = infoText,
        summaryText = summaryText,
        conversation = conversation,
        category = "msg",
        channelId = "ch-1",
        style = NotificationStyle.MESSAGING,
        isOngoing = false,
        groupKey = null,
        flags = 0,
        extrasCensus = ExtrasCensus(emptyList())
    )

    private fun message(
        sender: String?,
        text: String,
        timestampMs: Long? = null,
        historic: Boolean = false
    ) = MessageEntry(sender = sender, text = text, timestampMs = timestampMs, historic = historic)

    @Test
    fun `all presentation fields are emitted verbatim in fixed order`() {
        val canonical = CanonicalSourceBuilder.build(
            snapshot(
                title = "Rishabh Jain",
                text = "Please call tomorrow",
                bigText = "Expanded body",
                subText = "sub",
                infoText = "info",
                summaryText = "summary"
            )
        )
        assertEquals(
            listOf(
                "TITLE: Rishabh Jain",
                "TEXT: Please call tomorrow",
                "BIG_TEXT: Expanded body",
                "SUB_TEXT: sub",
                "INFO_TEXT: info",
                "SUMMARY: summary"
            ).joinToString("\n"),
            canonical
        )
    }

    @Test
    fun `blank and null fields emit nothing`() {
        assertEquals("TITLE: hi", CanonicalSourceBuilder.build(snapshot(title = "hi", text = "  ")))
    }

    @Test
    fun `fully empty snapshot produces empty canonical`() {
        assertEquals("", CanonicalSourceBuilder.build(snapshot()))
    }

    @Test
    fun `single messaging message keeps sender and boundaries`() {
        val canonical = CanonicalSourceBuilder.build(
            snapshot(conversation = ConversationContext("Rahul", isGroup = false, messages = listOf(message(null, "call me now"))))
        )
        assertTrue(canonical.contains("\nMSG call me now"))
        assertTrue(!canonical.startsWith("MSG"))
    }

    @Test
    fun `conversation title precedes messages with group marker`() {
        val canonical = CanonicalSourceBuilder.build(
            snapshot(conversation = ConversationContext("Family", isGroup = true, messages = listOf(message("Mom", "dinner at 8"))))
        )
        assertEquals("CONVERSATION: Family [GROUP]\nMSG Mom: dinner at 8", canonical)
    }

    @Test
    fun `historic messages are marked and ordered before delivered ones`() {
        val canonical = CanonicalSourceBuilder.build(
            snapshot(
                conversation = ConversationContext(
                    null,
                    isGroup = false,
                    messages = listOf(message("Rahul", "newest"), message("Rahul", "older", historic = true))
                )
            )
        )
        val lines = canonical.split("\n")
        assertTrue(lines[0].startsWith("HISTORIC"))
        assertTrue(lines[0].contains("older"))
        assertTrue(lines[1].startsWith("MSG"))
        assertTrue(lines[1].contains("newest"))
    }

    @Test
    fun `message timestamps are preserved when present`() {
        val canonical = CanonicalSourceBuilder.build(
            snapshot(conversation = ConversationContext(null, false, listOf(message(null, "ping", timestampMs = 1234L))))
        )
        assertTrue(canonical.contains("MSG 1234 ping"))
    }

    @Test
    fun `presentation text already contained in messaging block is suppressed`() {
        val canonical = CanonicalSourceBuilder.build(
            snapshot(
                text = "call me now",
                conversation = ConversationContext("Rahul", false, listOf(message("Rahul", "Call me now")))
            )
        )
        assertTrue(!canonical.contains("TEXT:"))
        assertTrue(canonical.contains("CONVERSATION: Rahul"))
        assertTrue(canonical.contains("MSG Rahul: Call me now"))
    }

    @Test
    fun `text subsumed by big text drops the text line but keeps big text`() {
        val canonical = CanonicalSourceBuilder.build(
            snapshot(text = "short version", bigText = "This is the long short version with more detail")
        )
        assertTrue(!canonical.contains("TEXT:"))
        assertTrue(canonical.contains("BIG_TEXT: This is the long short version with more detail"))
    }

    @Test
    fun `distinct big text keeps both lines`() {
        val canonical = CanonicalSourceBuilder.build(
            snapshot(text = "summary line", bigText = "completely different expansion")
        )
        assertTrue(canonical.contains("TEXT: summary line"))
        assertTrue(canonical.contains("BIG_TEXT: completely different expansion"))
    }

    @Test
    fun `verbatim casing punctuation and spacing are preserved`() {
        val weird = "Mixed CASE!!  double   spaces... ok?"
        val canonical = CanonicalSourceBuilder.build(snapshot(text = weird))
        assertEquals("TEXT: $weird", canonical)
    }

    @Test
    fun `output is deterministic across repeated builds`() {
        val s = snapshot(
            title = "T",
            text = "body",
            conversation = ConversationContext("C", true, listOf(message("A", "one"), message("B", "two")))
        )
        assertEquals(CanonicalSourceBuilder.build(s), CanonicalSourceBuilder.build(s))
    }
}
