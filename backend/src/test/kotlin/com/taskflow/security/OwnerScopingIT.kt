package com.taskflow.security

import com.taskflow.support.ApiIT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** R03: owner-only access, with two real users and real signed tokens, even when B is assigned a task of A. */
class OwnerScopingIT : ApiIT() {
    private val a by lazy { person("Alice") }
    private val b by lazy { person("Bob") }
    private val pA by lazy { project(a, "A's project") }
    private val tA by lazy { task(pA, "A's task", assignee = b) } // B is assigned, which must grant nothing

    @org.junit.jupiter.api.BeforeEach
    fun createFixtures() {
        check(tA > 0) // creates a, b, pA and tA (lazy, because Spring injects only after construction)
    }

    private fun notFound(r: org.springframework.test.web.servlet.ResultActions) =
        r.andExpect(status().isNotFound).andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.code").value("NOT_FOUND"))

    @Test
    fun `B gets 404 for list, create, update, patch, delete and summary of A's project and task`() {
        notFound(get("/api/projects/$pA/tasks", b))
        notFound(post("/api/projects/$pA/tasks", b, """{"title":"intruder"}"""))
        notFound(put("/api/tasks/$tA", b, """{"title":"hijacked","status":"DONE","priority":"HIGH"}"""))
        notFound(patch("/api/tasks/$tA/status", b, """{"status":"DONE"}"""))
        notFound(delete("/api/tasks/$tA", b))
        notFound(get("/api/projects/$pA/summary", b))

        // nothing changed
        assertEquals("A's task", taskTitle(tA))
        assertEquals(1, taskCount(pA))
        assertEquals("TODO", jdbc.queryForObject("select status from tasks where id = ?", String::class.java, tA))
    }

    @Test
    fun `a foreign project and a missing one answer with the same 404 body`() {
        val foreign = get("/api/projects/$pA/tasks", b).andReturn().response.contentAsString
        val missing = get("/api/projects/999999999/tasks", b).andReturn().response.contentAsString
        assertEquals(missing, foreign)
        val foreignTask = delete("/api/tasks/$tA", b).andReturn().response.contentAsString
        val missingTask = delete("/api/tasks/999999999", b).andReturn().response.contentAsString
        assertEquals(missingTask, foreignTask)
    }

    @Test
    fun `A still has full access to the same project and task`() {
        get("/api/projects/$pA/tasks", a).andExpect(status().isOk).andExpect(jsonPath("$.totalElements").value(1))
        get("/api/projects/$pA/summary", a).andExpect(status().isOk).andExpect(jsonPath("$.total").value(1))
        patch("/api/tasks/$tA/status", a, """{"status":"DONE"}""").andExpect(status().isOk)
    }

    @Test
    fun `project lists show only the caller's projects, and the assignee does not see the project`() {
        val pB = project(b, "B's project")
        get("/api/projects", a).andExpect(status().isOk).andExpect(jsonPath("$[?(@.id==$pA)]").exists())
            .andExpect(jsonPath("$[?(@.id==$pB)]").doesNotExist())
        get("/api/projects", b).andExpect(status().isOk).andExpect(jsonPath("$[?(@.id==$pB)]").exists())
            .andExpect(jsonPath("$[?(@.id==$pA)]").doesNotExist())
    }

    @Test
    fun `B's own project is invisible to A in the same way`() {
        val pB = project(b, "B's project")
        val tB = task(pB, "B's task")
        notFound(get("/api/projects/$pB/tasks", a))
        notFound(put("/api/tasks/$tB", a, """{"title":"x","status":"DONE","priority":"HIGH"}"""))
        notFound(delete("/api/tasks/$tB", a))
        assertEquals("B's task", taskTitle(tB))
    }

    @Test
    fun `owner comes from the token - a client supplied ownerId is ignored`() {
        post("/api/projects", a, """{"name":"Mine","ownerId":${b.id}}""")
            .andExpect(status().isCreated).andExpect(jsonPath("$.ownerId").value(a.id))
        assertEquals(0, jdbc.queryForObject("select count(*) from projects where owner_id = ? and name = 'Mine'", Int::class.java, b.id))
    }

    @Test
    fun `every new endpoint needs a token`() {
        listOf(
            "GET /api/projects", "POST /api/projects", "GET /api/projects/$pA/tasks", "POST /api/projects/$pA/tasks",
            "PUT /api/tasks/$tA", "PATCH /api/tasks/$tA/status", "DELETE /api/tasks/$tA", "GET /api/projects/$pA/summary", "GET /api/users?query=ab",
        ).forEach {
            val (m, p) = it.split(" ")
            api(org.springframework.http.HttpMethod.valueOf(m), p, null, "{}").andExpect(status().isUnauthorized)
        }
    }
}
