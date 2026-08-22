package com.notrishabhjain.taskmind.domain.repository

interface ProjectTagRepository {

    suspend fun ensureTags(names: List<String>): List<Long>

    suspend fun ensureProject(name: String): Long
}
