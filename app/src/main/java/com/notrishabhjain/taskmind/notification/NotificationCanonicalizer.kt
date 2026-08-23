package com.notrishabhjain.taskmind.notification

import com.notrishabhjain.taskmind.domain.model.CaptureState
import com.notrishabhjain.taskmind.domain.model.NotificationCapture
import java.security.MessageDigest
import java.time.Instant

data class RawNotificationInput(
    val sourcePackage: String,
    val notificationKey: String,
    val postTimeMillis: Long,
    val title: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val infoText: String? = null,
    val conversationTitle: String? = null,
    val category: String? = null,
    val appLabel: String? = null
)

object NotificationCanonicalizer {

    fun isSelfNotification(sourcePackage: String, ownPackageName: String): Boolean =
        sourcePackage == ownPackageName

    fun canonicalSourceText(input: RawNotificationInput): String = listOf(
        input.conversationTitle,
        input.title,
        input.text,
        input.bigText,
        input.subText,
        input.infoText
    )
        .filterNotNull()
        .joinToString("\n")

    fun contentHash(input: RawNotificationInput): String =
        sha256(canonicalSourceText(input))

    fun idempotencyKey(input: RawNotificationInput): String =
        sha256(
            "${input.sourcePackage}|${input.notificationKey}|${input.postTimeMillis}|" +
                canonicalSourceText(input)
        )

    fun sourceRef(input: RawNotificationInput): String =
        "notification:${input.sourcePackage}:${input.notificationKey}"

    fun toCapture(
        input: RawNotificationInput,
        capturedAt: Instant
    ): NotificationCapture = NotificationCapture(
        idempotencyKey = idempotencyKey(input),
        sourcePackage = input.sourcePackage,
        sourceAppLabel = input.appLabel,
        notificationKey = input.notificationKey,
        postTime = Instant.ofEpochMilli(input.postTimeMillis),
        title = input.title,
        text = input.text,
        bigText = input.bigText,
        subText = input.subText,
        infoText = input.infoText,
        conversationTitle = input.conversationTitle,
        category = input.category,
        canonicalSourceText = canonicalSourceText(input),
        contentHash = contentHash(input),
        sourceRef = sourceRef(input),
        state = CaptureState.CAPTURED,
        createdAt = capturedAt,
        updatedAt = capturedAt
    )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
