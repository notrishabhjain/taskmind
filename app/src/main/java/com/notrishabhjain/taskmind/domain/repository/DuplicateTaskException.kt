package com.notrishabhjain.taskmind.domain.repository

import com.notrishabhjain.taskmind.domain.model.SourceType

class DuplicateTaskException(
    val sourceType: SourceType,
    val sourceRef: String,
    val titleKey: String
) : IllegalStateException(
    "Unique constraint violated for logical key ($sourceType, $sourceRef, $titleKey)"
)
