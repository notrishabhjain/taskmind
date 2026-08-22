package com.notrishabhjain.taskmind.data.db.dao

import androidx.room.RawQuery
import com.notrishabhjain.taskmind.data.db.entity.TaskEntity
import com.notrishabhjain.taskmind.data.db.entity.TaskTagCrossRef
import org.junit.Assert.assertTrue
import org.junit.Test

class RawQueryObservationContractTest {

    @Test
    fun `task list observation reacts to task and tag-link changes`() {
        val method = TaskDao::class.java.methods.first { it.name == "observeTasks" }
        val rawQuery = method.getAnnotation(RawQuery::class.java)
            ?: throw AssertionError("observeTasks must be annotated with @RawQuery")

        val observed = rawQuery.observedEntities.toSet()

        assertTrue(TaskEntity::class.java in observed)
        assertTrue(
            "Tag-link changes must re-emit the observable task list",
            TaskTagCrossRef::class.java in observed
        )
    }
}
