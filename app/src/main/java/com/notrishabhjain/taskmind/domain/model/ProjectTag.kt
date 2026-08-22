package com.notrishabhjain.taskmind.domain.model

data class Project(
    val id: Long = 0L,
    val name: String,
    val nameKey: String
)

data class Tag(
    val id: Long = 0L,
    val name: String,
    val nameKey: String
)
