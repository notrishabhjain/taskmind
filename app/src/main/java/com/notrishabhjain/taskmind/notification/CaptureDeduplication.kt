package com.notrishabhjain.taskmind.notification

import com.notrishabhjain.taskmind.domain.model.NotificationCapture

enum class CaptureRelation {
    FRESH_CAPTURE,
    EXACT_DUPLICATE,
    NEW_VERSION
}

object CaptureDeduplication {

    /**
     * Classifies an incoming capture against the most recent capture that shares
     * the same logical identity (sourcePackage + notificationKey).
     *
     * - No prior row                      -> FRESH_CAPTURE
     * - Same canonical content hash       -> EXACT_DUPLICATE (idempotent redelivery)
     * - Same identity, changed hash       -> NEW_VERSION (notification was updated)
     *
     * The caller is responsible for identity-scoped lookup; passing a row with a
     * different logical identity yields FRESH_CAPTURE by definition.
     */
    fun classify(existing: NotificationCapture?, incoming: NotificationCapture): CaptureRelation {
        if (existing == null) return CaptureRelation.FRESH_CAPTURE
        if (existing.sourcePackage != incoming.sourcePackage) return CaptureRelation.FRESH_CAPTURE
        if (existing.notificationKey != incoming.notificationKey) return CaptureRelation.FRESH_CAPTURE

        return if (existing.contentHash == incoming.contentHash) {
            CaptureRelation.EXACT_DUPLICATE
        } else {
            CaptureRelation.NEW_VERSION
        }
    }
}
