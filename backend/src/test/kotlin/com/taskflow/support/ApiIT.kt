package com.taskflow.support

import com.taskflow.security.TokenService
import com.taskflow.user.User
import com.taskflow.user.UserRepository
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request

/** Base for HTTP-level tests: real PostgreSQL, full security chain, real signed JWTs for real users. */
@AutoConfigureMockMvc
abstract class ApiIT : PostgresIT() {
    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var userRepo: UserRepository
    @Autowired lateinit var tokens: TokenService
    @Autowired lateinit var clock: Clock
    @Autowired lateinit var jdbc: JdbcTemplate

    class Person(val id: Long, val name: String, val token: String)

    fun today(): LocalDate = LocalDate.now(clock)

    fun person(name: String = "Person"): Person {
        val u = userRepo.save(User(name, "p-${UUID.randomUUID()}@example.test", "hash"))
        return Person(u.id!!, name, tokens.issue(u.id!!).value)
    }

    // Rows are inserted with SQL so tests can create states the API forbids (e.g. a past due date).
    fun project(owner: Person, name: String = "P"): Long =
        jdbc.queryForObject("insert into projects(name, owner_id) values (?, ?) returning id", Long::class.java, name, owner.id)!!

    fun task(
        projectId: Long,
        title: String = "T",
        status: String = "TODO",
        priority: String = "MEDIUM",
        due: LocalDate? = null,
        assignee: Person? = null,
    ): Long = jdbc.queryForObject(
        "insert into tasks(title, status, priority, due_date, assignee_id, project_id) values (?, ?, ?, ?, ?, ?) returning id",
        Long::class.java, title, status, priority, due, assignee?.id, projectId,
    )!!

    fun api(method: HttpMethod, path: String, who: Person?, body: String? = null, vararg params: Pair<String, Any>): ResultActions {
        val req = request(method, path)
        if (who != null) req.header("Authorization", "Bearer " + who.token)
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body)
        params.forEach { (k, v) -> req.param(k, v.toString()) }
        return mvc.perform(req)
    }

    fun get(path: String, who: Person?, vararg params: Pair<String, Any>) = api(HttpMethod.GET, path, who, null, *params)
    fun post(path: String, who: Person?, body: String) = api(HttpMethod.POST, path, who, body)
    fun put(path: String, who: Person?, body: String) = api(HttpMethod.PUT, path, who, body)
    fun patch(path: String, who: Person?, body: String) = api(HttpMethod.PATCH, path, who, body)
    fun delete(path: String, who: Person?) = api(HttpMethod.DELETE, path, who)

    /** Tiny JSON object builder: numbers and booleans raw, null as null, everything else quoted. */
    fun json(vararg kv: Pair<String, Any?>) = kv.joinToString(",", "{", "}") { (k, v) ->
        "\"" + k + "\":" + when (v) {
            null -> "null"
            is Number, is Boolean -> v.toString()
            else -> "\"" + v.toString().replace("\"", "\\\"") + "\""
        }
    }

    fun taskTitle(id: Long): String? = jdbc.queryForList("select title from tasks where id = ?", String::class.java, id).firstOrNull()
    fun taskCount(projectId: Long): Int = jdbc.queryForObject("select count(*) from tasks where project_id = ?", Int::class.java, projectId)!!
}
