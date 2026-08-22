package com.notrishabhjain.taskmind.data.repository

import com.notrishabhjain.taskmind.data.db.dao.ProjectTagDao
import com.notrishabhjain.taskmind.data.db.entity.ProjectEntity
import com.notrishabhjain.taskmind.data.db.entity.TagEntity
import com.notrishabhjain.taskmind.data.mapper.toDomain
import com.notrishabhjain.taskmind.domain.intake.TitleNormalizer
import com.notrishabhjain.taskmind.domain.model.Tag
import com.notrishabhjain.taskmind.domain.repository.ProjectTagRepository
import com.notrishabhjain.taskmind.domain.time.TimeProvider

class RoomProjectTagRepository(
    private val projectTagDao: ProjectTagDao,
    private val timeProvider: TimeProvider
) : ProjectTagRepository {

    override suspend fun ensureTags(names: List<String>): List<Long> = names
        .map { raw -> TitleNormalizer.normalize(raw) }
        .filter { it.isNotBlank() }
        .map { name -> name to TitleNormalizer.titleKey(name) }
        .distinctBy { (_, key) -> key }
        .map { (name, nameKey) -> ensureTag(name, nameKey).id }

    private suspend fun ensureTag(name: String, nameKey: String): Tag {
        val existing = projectTagDao.findTag(nameKey)
        if (existing != null) return existing.toDomain()

        projectTagDao.insertTag(TagEntity(name = name, nameKey = nameKey))
        return requireNotNull(projectTagDao.findTag(nameKey)) {
            "Tag row missing after insert: $nameKey"
        }.toDomain()
    }

    override suspend fun ensureProject(name: String): Long {
        val trimmed = TitleNormalizer.normalize(name)
        if (trimmed.isBlank()) return NO_PROJECT

        val nameKey = TitleNormalizer.titleKey(trimmed)
        projectTagDao.findProject(nameKey)?.let { return it.id }

        projectTagDao.insertProject(
            ProjectEntity(
                name = trimmed,
                nameKey = nameKey,
                createdAt = timeProvider.now().toEpochMilli()
            )
        )
        return requireNotNull(projectTagDao.findProject(nameKey)) {
            "Project row missing after insert: $nameKey"
        }.id
    }

    private companion object {
        const val NO_PROJECT = 0L
    }
}
