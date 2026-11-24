package com.taskflow.task

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.taskflow.support.ApiIT
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** GET /api/projects/{id}/tasks: filters, the two sort strategies, paging, and parameter validation. */
class TaskListIT : ApiIT() {
    private val a by lazy { person("Alice") }
    private val helper by lazy { person("Helper") }
    private val other by lazy { person("Other") }
    private val mapper = ObjectMapper()

    private fun list(p: Long, vararg params: Pair<String, Any>): JsonNode =
        mapper.readTree(get("/api/projects/$p/tasks", a, *params).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun ids(n: JsonNode) = n["content"].map { it["id"].asLong() }

    // ---- filters ----

    @Test
    fun `status, priority and assignee filters work alone and combined`() {
        val p = project(a)
        val t = today()
        val x1 = task(p, "x1", "TODO", "HIGH", t, helper)
        val x2 = task(p, "x2", "TODO", "HIGH", t, other)
        val x3 = task(p, "x3", "TODO", "LOW", t, helper)
        val x4 = task(p, "x4", "DONE", "HIGH", t, helper)
        val x5 = task(p, "x5", "IN_PROGRESS", "MEDIUM", null, null)

        assertEquals(setOf(x1, x2, x3), ids(list(p, "status" to "TODO")).toSet())
        assertEquals(setOf(x1, x2, x4), ids(list(p, "priority" to "HIGH")).toSet())
        assertEquals(setOf(x1, x3, x4), ids(list(p, "assigneeId" to helper.id)).toSet())
        assertEquals(setOf(x1, x2), ids(list(p, "status" to "TODO", "priority" to "HIGH")).toSet())
        assertEquals(setOf(x1, x3), ids(list(p, "status" to "TODO", "assigneeId" to helper.id)).toSet())
        assertEquals(listOf(x1), ids(list(p, "status" to "TODO", "priority" to "HIGH", "assigneeId" to helper.id)))
        assertEquals(emptyList<Long>(), ids(list(p, "status" to "DONE", "priority" to "LOW")))
        assertEquals(5, list(p)["totalElements"].asInt())
        assertTrue(x5 > 0)
    }

    @Test
    fun `filters never leak tasks from another project`() {
        val p = project(a)
        val q = project(a)
        val mine = task(p, "mine", "TODO", "HIGH")
        task(q, "other project", "TODO", "HIGH")
        assertEquals(listOf(mine), ids(list(p, "status" to "TODO", "priority" to "HIGH")))
    }

    // ---- ordering ----

    @Test
    fun `priority sorts by rank HIGH, MEDIUM, LOW (desc default) and the reverse with asc, not alphabetically`() {
        val p = project(a)
        val low = task(p, "low", priority = "LOW")
        val med = task(p, "med", priority = "MEDIUM")
        val high = task(p, "high", priority = "HIGH")
        assertEquals(listOf(high, med, low), ids(list(p, "sortBy" to "priority")))
        assertEquals(listOf(high, med, low), ids(list(p, "sortBy" to "priority", "direction" to "desc")))
        assertEquals(listOf(low, med, high), ids(list(p, "sortBy" to "priority", "direction" to "asc")))
    }

    @Test
    fun `due date sorts ascending by default and descending on request, with undated tasks always last`() {
        val p = project(a)
        val t = today()
        val none1 = task(p, "none1", due = null)
        val d3 = task(p, "d3", due = t.plusDays(3))
        val d1 = task(p, "d1", due = t.plusDays(1))
        val none2 = task(p, "none2", due = null)
        val d2 = task(p, "d2", due = t.plusDays(2))
        assertEquals(listOf(d1, d2, d3, none1, none2), ids(list(p, "sortBy" to "dueDate")))
        assertEquals(listOf(d1, d2, d3, none1, none2), ids(list(p))) // dueDate asc is the default
        assertEquals(listOf(d1, d2, d3, none1, none2), ids(list(p, "sortBy" to "dueDate", "direction" to "ASC")))
        assertEquals(listOf(d3, d2, d1, none1, none2), ids(list(p, "sortBy" to "dueDate", "direction" to "desc")))
    }

    @Test
    fun `ties are broken by id ascending`() {
        val p = project(a)
        val same = LocalDate.now(clock).plusDays(4)
        val first = task(p, "t1", priority = "HIGH", due = same)
        val second = task(p, "t2", priority = "HIGH", due = same)
        val third = task(p, "t3", priority = "HIGH", due = same)
        assertEquals(listOf(first, second, third), ids(list(p, "sortBy" to "priority")))
        assertEquals(listOf(first, second, third), ids(list(p, "sortBy" to "dueDate", "direction" to "desc")))
    }

    @Test
    fun `sorting works together with filters`() {
        val p = project(a)
        val t = today()
        val a1 = task(p, "a1", "TODO", "LOW", t.plusDays(2))
        val a2 = task(p, "a2", "TODO", "HIGH", t.plusDays(5))
        task(p, "skipped", "DONE", "HIGH", t.plusDays(1))
        assertEquals(listOf(a2, a1), ids(list(p, "status" to "TODO", "sortBy" to "priority")))
        assertEquals(listOf(a1, a2), ids(list(p, "status" to "TODO", "sortBy" to "dueDate")))
    }

    // ---- paging ----

    private fun expectedOrder(rows: List<Triple<Long, String, LocalDate?>>, sortBy: String, asc: Boolean): List<Long> {
        val rank = mapOf("HIGH" to 3, "MEDIUM" to 2, "LOW" to 1)
        val cmp: Comparator<Triple<Long, String, LocalDate?>> =
            if (sortBy == "priority") {
                (if (asc) compareBy<Triple<Long, String, LocalDate?>> { rank[it.second]!! } else compareByDescending { rank[it.second]!! }).thenBy { it.first }
            } else {
                val nullsLast = compareBy<Triple<Long, String, LocalDate?>> { it.third == null }
                val byDate = Comparator<Triple<Long, String, LocalDate?>> { x, y ->
                    if (x.third == null || y.third == null) 0 else if (asc) x.third!!.compareTo(y.third!!) else y.third!!.compareTo(x.third!!)
                }
                nullsLast.then(byDate).thenBy { it.first }
            }
        return rows.sortedWith(cmp).map { it.first }
    }

    @Test
    fun `paging 45 tasks with many ties returns every task exactly once, in the expected order, for every sort`() {
        val p = project(a)
        val t = today()
        val rows = (1..45).map { i ->
            val pr = listOf("LOW", "MEDIUM", "HIGH")[i % 3]
            val due = if (i % 7 == 0) null else t.plusDays((i % 4).toLong()) // few distinct dates: lots of ties
            Triple(task(p, "t$i", priority = pr, due = due), pr, due)
        }
        listOf(Triple("priority", "asc", true), Triple("priority", "desc", false), Triple("dueDate", "asc", true), Triple("dueDate", "desc", false)).forEach { (sortBy, dir, asc) ->
            val pages = (0..2).map { list(p, "sortBy" to sortBy, "direction" to dir, "page" to it, "size" to 20) }
            assertEquals(listOf(20, 20, 5), pages.map { it["content"].size() }, "$sortBy $dir page sizes")
            val all = pages.flatMap { ids(it) }
            assertEquals(45, all.toSet().size, "$sortBy $dir: no duplicates, none missing")
            assertEquals(expectedOrder(rows, sortBy, asc), all, "$sortBy $dir order")
        }
    }

    @Test
    fun `combined filters with paging return each matching task once, in order, and never a non-matching one`() {
        val p = project(a)
        val t = today()
        val rows = (1..60).map { i ->
            val status = listOf("TODO", "IN_PROGRESS", "DONE")[i % 3]
            val priority = listOf("LOW", "HIGH")[i % 2]
            val who = if (i % 5 == 0) helper else other
            val due = if (i % 7 == 0) null else t.plusDays((i % 3).toLong()) // many ties and some undated
            val id = task(p, "t$i", status, priority, due, who)
            listOf(id, status, priority, who.id, due)
        }
        val expected = rows.filter { it[1] == "TODO" && it[2] == "HIGH" && it[3] == other.id }
            .sortedWith(compareBy<List<Any?>> { it[4] == null }.thenBy { it[4] as LocalDate? }.thenBy { it[0] as Long })
            .map { it[0] as Long }
        assertTrue(expected.size > 6, "the fixture must span several pages")
        val pages = (0..expected.size / 4).map {
            list(p, "status" to "TODO", "priority" to "HIGH", "assigneeId" to other.id, "sortBy" to "dueDate", "direction" to "asc", "page" to it, "size" to 4)
        }
        assertEquals(expected, pages.flatMap { ids(it) })
        pages.forEach { assertEquals(expected.size, it["totalElements"].asInt()) }
        assertEquals(0, list(p, "status" to "TODO", "priority" to "HIGH", "assigneeId" to other.id, "page" to 50)["content"].size())
    }

    @Test
    fun `page metadata and boundaries`() {
        val p = project(a)
        repeat(40) { task(p, "t$it") }
        val first = list(p) // defaults: page 0, size 20
        assertEquals(0, first["page"].asInt()); assertEquals(20, first["size"].asInt())
        assertEquals(40, first["totalElements"].asInt()); assertEquals(2, first["totalPages"].asInt())
        assertEquals(20, list(p, "page" to 1)["content"].size())
        val beyond = list(p, "page" to 2) // exact multiple: the page after the last is empty, not an error
        assertEquals(0, beyond["content"].size()); assertEquals(40, beyond["totalElements"].asInt())
        assertEquals(0, list(p, "page" to 99)["content"].size())
        assertEquals(40, list(p, "size" to 100)["content"].size())
        assertEquals(1, list(p, "size" to 1)["content"].size())
        assertEquals(40, list(p, "size" to 1)["totalPages"].asInt())
        // an empty project
        val empty = list(project(a))
        assertEquals(0, empty["totalElements"].asInt()); assertEquals(0, empty["totalPages"].asInt())
    }

    // ---- parameter validation ----

    @Test
    fun `invalid sort, direction, page and size are 400 field errors`() {
        val p = project(a)
        fun bad(field: String, vararg params: Pair<String, Any>) =
            get("/api/projects/$p/tasks", a, *params).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors." + field).exists())
        bad("sortBy", "sortBy" to "title")
        bad("sortBy", "sortBy" to "DUEDATE")
        bad("direction", "direction" to "sideways")
        bad("size", "size" to 0)
        bad("size", "size" to 101)
        bad("size", "size" to -5)
        bad("page", "page" to -1)
        bad("size", "size" to "many")
        bad("status", "status" to "BLOCKED")
        bad("priority", "priority" to "urgent")
        bad("assigneeId", "assigneeId" to "me")
        get("/api/projects/$p/tasks", a, "size" to 100).andExpect(status().isOk) // the maximum itself is fine
    }

    @Test
    fun `several invalid parameters are all reported together`() {
        val p = project(a)
        get("/api/projects/$p/tasks", a, "sortBy" to "x", "size" to 500, "page" to -2).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.fieldErrors.sortBy").exists()).andExpect(jsonPath("$.fieldErrors.size").exists()).andExpect(jsonPath("$.fieldErrors.page").exists())
    }
}
