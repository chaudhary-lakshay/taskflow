package com.taskflow.project

import com.taskflow.support.ApiIT
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ProjectApiIT : ApiIT() {
    private val a by lazy { person("Alice") }

    @Test
    fun `create returns 201 with the owner taken from the token, and the list shows it`() {
        val r = post("/api/projects", a, json("name" to "  Launch ", "description" to "Plan"))
            .andExpect(status().isCreated).andExpect(jsonPath("$.name").value("Launch"))
            .andExpect(jsonPath("$.description").value("Plan")).andExpect(jsonPath("$.ownerId").value(a.id))
            .andExpect(jsonPath("$.createdAt").exists()).andReturn()
        val id = Regex("\"id\":(\\d+)").find(r.response.contentAsString)!!.groupValues[1].toLong()
        get("/api/projects", a).andExpect(status().isOk).andExpect(jsonPath("$[?(@.id==$id)].name").value("Launch"))
    }

    @Test
    fun `name is required and limited to 120 characters, description to 2000`() {
        post("/api/projects", a, json("name" to "x".repeat(120))).andExpect(status().isCreated)
        post("/api/projects", a, json("name" to "x".repeat(121))).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors.name").exists())
        post("/api/projects", a, json("name" to "  ")).andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.name").exists())
        post("/api/projects", a, "{}").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.name").exists())
        post("/api/projects", a, json("name" to "ok", "description" to "d".repeat(2001))).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.description").exists())
    }

    // ---- summary (R06, D03) ----

    @Test
    fun `summary counts statuses and overdue by the rule - before today and not DONE`() {
        val p = project(a)
        val t = today()
        task(p, status = "TODO", due = t.minusDays(1)) // overdue
        task(p, status = "IN_PROGRESS", due = t.minusDays(30)) // overdue
        task(p, status = "DONE", due = t.minusDays(1)) // finished: not overdue
        task(p, status = "TODO", due = t) // due today: not overdue
        task(p, status = "TODO", due = t.plusDays(1)) // future
        task(p, status = "DONE", due = null)
        task(p, status = "IN_PROGRESS", due = null) // no date: never overdue
        get("/api/projects/$p/summary", a).andExpect(status().isOk)
            .andExpect(jsonPath("$.todo").value(3)).andExpect(jsonPath("$.inProgress").value(2))
            .andExpect(jsonPath("$.done").value(2)).andExpect(jsonPath("$.overdue").value(2)).andExpect(jsonPath("$.total").value(7))
    }

    @Test
    fun `summary follows status transitions and deletes, including the overdue count`() {
        val p = project(a)
        val t = today()
        val late = task(p, "late", status = "TODO", due = t.minusDays(2))
        task(p, "open", status = "TODO", due = t.plusDays(2))
        fun expect(todo: Int, inProgress: Int, done: Int, overdue: Int) =
            get("/api/projects/$p/summary", a).andExpect(status().isOk)
                .andExpect(jsonPath("$.todo").value(todo)).andExpect(jsonPath("$.inProgress").value(inProgress))
                .andExpect(jsonPath("$.done").value(done)).andExpect(jsonPath("$.overdue").value(overdue))
                .andExpect(jsonPath("$.total").value(todo + inProgress + done))
        expect(2, 0, 0, 1)
        patch("/api/tasks/$late/status", a, """{"status":"IN_PROGRESS"}""").andExpect(status().isOk)
        expect(1, 1, 0, 1) // still open, still overdue
        patch("/api/tasks/$late/status", a, """{"status":"DONE"}""").andExpect(status().isOk)
        expect(1, 0, 1, 0) // done is no longer overdue
        patch("/api/tasks/$late/status", a, """{"status":"TODO"}""").andExpect(status().isOk)
        expect(2, 0, 0, 1) // reopened: overdue again
        delete("/api/tasks/$late", a).andExpect(status().isNoContent)
        expect(1, 0, 0, 0)
        assertEquals(1, taskCount(p))
    }

    @Test
    fun `summary of an empty project is all zeros, and ignores board filters`() {
        val p = project(a)
        get("/api/projects/$p/summary", a).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.overdue").value(0))
        task(p, status = "DONE", priority = "LOW")
        task(p, status = "TODO", priority = "HIGH")
        // the same filter on the list narrows it, but the summary still counts everything
        get("/api/projects/$p/tasks", a, "status" to "DONE").andExpect(jsonPath("$.totalElements").value(1))
        get("/api/projects/$p/summary", a, "status" to "DONE", "priority" to "LOW").andExpect(jsonPath("$.total").value(2))
    }

    @Test
    fun `summary overdue count equals the number of overdue flags in the task list`() {
        val p = project(a)
        val t = today()
        task(p, status = "TODO", due = t.minusDays(3))
        task(p, status = "DONE", due = t.minusDays(3))
        task(p, status = "IN_PROGRESS", due = t)
        task(p, status = "TODO", due = null)
        val flagged = get("/api/projects/$p/tasks", a).andReturn().response.contentAsString.split("\"overdue\":true").size - 1
        val summary = get("/api/projects/$p/summary", a).andReturn().response.contentAsString
        assertEquals(flagged, Regex("\"overdue\":(\\d+)").find(summary)!!.groupValues[1].toInt())
    }

    // ---- user directory (D02, extra endpoint) ----

    @Test
    fun `user lookup returns only id and name, matches names case-insensitively, never emails`() {
        val tag = UUID.randomUUID().toString().take(8)
        val u = person("Zelda-$tag")
        val r = get("/api/users", a, "query" to "zelda-$tag").andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(u.id)).andExpect(jsonPath("$[0].name").value("Zelda-$tag"))
            .andExpect(jsonPath("$[0].email").doesNotExist()).andExpect(jsonPath("$[0].passwordHash").doesNotExist()).andReturn()
        assertEquals(setOf("id", "name"), com.fasterxml.jackson.databind.ObjectMapper().readTree(r.response.contentAsString)[0].fieldNames().asSequence().toSet())
        // emails are not searchable: the person's email contains "p-", their name does not
        get("/api/users", a, "query" to "@example.test").andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
    }

    @Test
    fun `user lookup needs at least 2 characters, returns at most 20, and treats wildcards literally`() {
        get("/api/users", a, "query" to "a").andExpect(status().isBadRequest).andExpect(jsonPath("$.fieldErrors.query").exists())
        get("/api/users", a, "query" to " a ").andExpect(status().isBadRequest)
        get("/api/users", a).andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors.query").exists())
        val tag = "Many" + UUID.randomUUID().toString().take(6)
        repeat(25) { person("$tag-$it") }
        get("/api/users", a, "query" to tag).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(20))
        get("/api/users", a, "query" to "%%").andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
        get("/api/users", a, "query" to "__").andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
    }
}
