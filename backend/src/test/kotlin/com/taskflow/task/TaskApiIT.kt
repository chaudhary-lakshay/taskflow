package com.taskflow.task

import com.taskflow.support.ApiIT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Create, update, status change and delete of tasks, with the validation rules of R04, R06, R07, D03-D05. */
class TaskApiIT : ApiIT() {
    private val a by lazy { person("Alice") }
    private val helper by lazy { person("Helper") }
    private val p by lazy { project(a) }

    private fun create(vararg kv: Pair<String, Any?>) = post("/api/projects/$p/tasks", a, json(*kv))

    @Test
    fun `create applies defaults TODO, MEDIUM, no assignee and returns the full DTO`() {
        create("title" to "  Write docs ").andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("Write docs")).andExpect(jsonPath("$.status").value("TODO"))
            .andExpect(jsonPath("$.priority").value("MEDIUM")).andExpect(jsonPath("$.assignee").doesNotExist())
            .andExpect(jsonPath("$.dueDate").doesNotExist()).andExpect(jsonPath("$.projectId").value(p))
            .andExpect(jsonPath("$.overdue").value(false)).andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.updatedAt").exists())
    }

    @Test
    fun `create accepts every field, including an optional status and an existing assignee`() {
        create("title" to "Full", "description" to "d", "priority" to "HIGH", "status" to "IN_PROGRESS",
            "dueDate" to today().plusDays(2), "assigneeId" to helper.id)
            .andExpect(status().isCreated).andExpect(jsonPath("$.status").value("IN_PROGRESS")).andExpect(jsonPath("$.priority").value("HIGH"))
            .andExpect(jsonPath("$.dueDate").value(today().plusDays(2).toString()))
            .andExpect(jsonPath("$.assignee.id").value(helper.id)).andExpect(jsonPath("$.assignee.name").value("Helper"))
    }

    @Test
    fun `title is required, trimmed, and at most 120 characters`() {
        create("title" to "x".repeat(120)).andExpect(status().isCreated)
        create("title" to "x".repeat(121)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors.title").exists())
        create("title" to "   ").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.title").exists())
        post("/api/projects/$p/tasks", a, "{}").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.title").exists())
        assertEquals(1, taskCount(p))
    }

    @Test
    fun `dueDate on create may be today, future or absent, but not in the past`() {
        create("title" to "today", "dueDate" to today()).andExpect(status().isCreated)
        create("title" to "future", "dueDate" to today().plusDays(30)).andExpect(status().isCreated)
        create("title" to "none").andExpect(status().isCreated)
        create("title" to "none-null", "dueDate" to null).andExpect(status().isCreated)
        create("title" to "past", "dueDate" to today().minusDays(1)).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.dueDate").exists())
        assertEquals(4, taskCount(p))
    }

    @Test
    fun `an unknown assignee is a 400 field error and nothing is saved`() {
        create("title" to "x", "assigneeId" to 999999999L).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors.assigneeId").exists())
        assertEquals(0, taskCount(p))
    }

    @Test
    fun `bad enum values, bad dates and bad types are 400 field errors`() {
        create("title" to "x", "priority" to "URGENT").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.priority").exists())
        create("title" to "x", "status" to "BLOCKED").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.status").exists())
        create("title" to "x", "dueDate" to "tomorrow").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.dueDate").exists())
        post("/api/projects/$p/tasks", a, "{nope").andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
        post("/api/projects/abc/tasks", a, json("title" to "x")).andExpect(status().isBadRequest)
    }

    // ---- update (D04) ----

    private fun update(id: Long, vararg kv: Pair<String, Any?>) = put("/api/tasks/$id", a, json(*kv))

    @Test
    fun `update replaces all editable fields and null clears the optional ones`() {
        val id = task(p, "Old", "TODO", "LOW", today().plusDays(5), helper)
        update(id, "title" to "New", "description" to "d", "status" to "DONE", "priority" to "HIGH", "dueDate" to null, "assigneeId" to null)
            .andExpect(status().isOk).andExpect(jsonPath("$.title").value("New")).andExpect(jsonPath("$.status").value("DONE"))
            .andExpect(jsonPath("$.priority").value("HIGH")).andExpect(jsonPath("$.dueDate").doesNotExist())
            .andExpect(jsonPath("$.assignee").doesNotExist()).andExpect(jsonPath("$.id").value(id))
        assertEquals(null, jdbc.queryForObject("select due_date from tasks where id = ?", java.sql.Date::class.java, id))
    }

    @Test
    fun `update keeps an unchanged past due date, rejects a changed past date, allows today and the future`() {
        val past = today().minusDays(10)
        val id = task(p, "Old", due = past)
        update(id, "title" to "Same date", "status" to "TODO", "priority" to "MEDIUM", "dueDate" to past)
            .andExpect(status().isOk).andExpect(jsonPath("$.overdue").value(true))
        update(id, "title" to "x", "status" to "TODO", "priority" to "MEDIUM", "dueDate" to past.plusDays(1))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.dueDate").exists())
        update(id, "title" to "x", "status" to "TODO", "priority" to "MEDIUM", "dueDate" to today()).andExpect(status().isOk)
        update(id, "title" to "x", "status" to "TODO", "priority" to "MEDIUM", "dueDate" to today().plusDays(9)).andExpect(status().isOk)
        update(id, "title" to "x", "status" to "TODO", "priority" to "MEDIUM", "dueDate" to today().minusDays(1)).andExpect(status().isBadRequest)
    }

    @Test
    fun `update validates title, status, priority and assignee`() {
        val id = task(p, "Old")
        update(id, "title" to "x".repeat(121), "status" to "TODO", "priority" to "LOW").andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.title").exists())
        update(id, "status" to "TODO", "priority" to "LOW").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.title").exists())
        update(id, "title" to "x", "priority" to "LOW").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.status").exists())
        update(id, "title" to "x", "status" to "TODO").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.priority").exists())
        update(id, "title" to "x", "status" to "TODO", "priority" to "LOW", "assigneeId" to 999999999L).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.assigneeId").exists())
        assertEquals("Old", taskTitle(id))
    }

    @Test
    fun `update moves updatedAt forward and keeps createdAt`() {
        val created = post("/api/projects/$p/tasks", a, json("title" to "T")).andReturn().response.contentAsString
        val id = Regex("\"id\":(\\d+)").find(created)!!.groupValues[1].toLong()
        val c1 = Regex("\"createdAt\":\"([^\"]+)").find(created)!!.groupValues[1]
        val u1 = Regex("\"updatedAt\":\"([^\"]+)").find(created)!!.groupValues[1]
        Thread.sleep(5)
        val after = update(id, "title" to "T2", "status" to "DONE", "priority" to "LOW").andReturn().response.contentAsString
        assertEquals(c1, Regex("\"createdAt\":\"([^\"]+)").find(after)!!.groupValues[1])
        val u2 = Regex("\"updatedAt\":\"([^\"]+)").find(after)!!.groupValues[1]
        assert(java.time.Instant.parse(u2).isAfter(java.time.Instant.parse(u1))) { "$u1 -> $u2" }
    }

    // ---- status PATCH and delete ----

    @Test
    fun `patch changes only the status - every transition is allowed`() {
        val id = task(p, "T", "TODO", "HIGH", today().plusDays(1), helper)
        listOf("DONE", "TODO", "IN_PROGRESS", "DONE", "IN_PROGRESS").forEach { s ->
            patch("/api/tasks/$id/status", a, json("status" to s)).andExpect(status().isOk).andExpect(jsonPath("$.status").value(s))
                .andExpect(jsonPath("$.title").value("T")).andExpect(jsonPath("$.priority").value("HIGH")).andExpect(jsonPath("$.assignee.id").value(helper.id))
        }
        patch("/api/tasks/$id/status", a, json("status" to "NOPE")).andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.status").exists())
        patch("/api/tasks/$id/status", a, "{}").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.status").exists())
        assertEquals("IN_PROGRESS", jdbc.queryForObject("select status from tasks where id = ?", String::class.java, id))
    }

    @Test
    fun `delete returns 204 with no body, removes only that task, and a second delete is 404`() {
        val id = task(p, "gone")
        val keep = task(p, "keep")
        delete("/api/tasks/$id", a).andExpect(status().isNoContent).andExpect { assertEquals("", it.response.contentAsString) }
        assertEquals(null, taskTitle(id))
        assertEquals("keep", taskTitle(keep))
        delete("/api/tasks/$id", a).andExpect(status().isNotFound)
    }
}
