package com.notrishabhjain.taskmind.data.db.dao

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RawQueryObservationContractTest {

    @Test
    fun `task list observation reacts to task and tag-link changes`() {
        val source = readDaoSource()
        val annotationStart = source.indexOf("@RawQuery")
        assertTrue("observeTasks must declare @RawQuery", annotationStart >= 0)

        val segment = source.substring(annotationStart, source.indexOf("observeTasks", annotationStart))

        assertTrue(
            "Task changes must re-emit the observable task list",
            "TaskEntity::class" in segment
        )
        assertTrue(
            "Tag-link changes must re-emit the observable task list",
            "TaskTagCrossRef::class" in segment
        )
    }

    private fun readDaoSource(): String {
        val relative = "src/main/java/com/notrishabhjain/taskmind/data/db/dao/TaskDao.kt"
        val file = sequenceOf(File(relative), File("app").resolve(relative))
            .firstOrNull { it.exists() }
            ?: throw AssertionError(
                "TaskDao.kt is not reachable from the working directory; the RawQuery " +
                    "observation contract is verified statically because @RawQuery uses class retention"
            )
        return file.readText()
    }
}
