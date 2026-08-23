package com.notrishabhjain.taskmind.notification

enum class CaptureDecision { ACCEPT, IGNORE }

data class NotificationCapturePolicy(
    val captureEnabled: Boolean = true,
    val selfPackage: String,
    val allowedPackages: Set<String> = emptySet(),
    val blockedPackages: Set<String> = emptySet(),
    val minimumContentChars: Int = DEFAULT_MINIMUM_CONTENT_CHARS
) {
    companion object {
        const val DEFAULT_MINIMUM_CONTENT_CHARS = 3
    }
}

class CaptureFilter(private val policy: NotificationCapturePolicy) {

    fun decide(input: RawNotificationInput): CaptureDecision {
        if (!policy.captureEnabled) return CaptureDecision.IGNORE
        if (NotificationCanonicalizer.isSelfNotification(input.sourcePackage, policy.selfPackage)) {
            return CaptureDecision.IGNORE
        }
        if (input.sourcePackage in policy.blockedPackages) return CaptureDecision.IGNORE

        val allowlist = policy.allowedPackages
        if (allowlist.isNotEmpty() && input.sourcePackage !in allowlist) return CaptureDecision.IGNORE

        if (NotificationCanonicalizer.canonicalSourceText(input).length < policy.minimumContentChars) {
            return CaptureDecision.IGNORE
        }

        return CaptureDecision.ACCEPT
    }
}
