package com.taskflow.task

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Order
import jakarta.persistence.criteria.Root
import java.time.LocalDate

/**
 * Strategy pattern: each supported sort field is one ordering rule. Every strategy ends with id
 * ascending, so pages never repeat or skip rows when the sort key ties.
 */
sealed interface TaskSortStrategy {
    val defaultAscending: Boolean

    fun orders(root: Root<Task>, cb: CriteriaBuilder, ascending: Boolean): List<Order>
}

/** Due date; tasks without a date always come last, in both directions. */
object DueDateSort : TaskSortStrategy {
    override val defaultAscending = true

    override fun orders(root: Root<Task>, cb: CriteriaBuilder, ascending: Boolean): List<Order> {
        val due = root.get<LocalDate>("dueDate")
        val nullsLast = cb.selectCase<Int>().`when`(cb.isNull(due), 1).otherwise(0)
        return listOf(cb.asc(nullsLast), if (ascending) cb.asc(due) else cb.desc(due), cb.asc(root.get<Long>("id")))
    }
}

/** Priority by rank HIGH > MEDIUM > LOW (never alphabetical: the text order would be HIGH, LOW, MEDIUM). */
object PrioritySort : TaskSortStrategy {
    override val defaultAscending = false // highest first

    override fun orders(root: Root<Task>, cb: CriteriaBuilder, ascending: Boolean): List<Order> {
        val p = root.get<TaskPriority>("priority")
        val rank = cb.selectCase<Int>()
            .`when`(cb.equal(p, TaskPriority.HIGH), 3)
            .`when`(cb.equal(p, TaskPriority.MEDIUM), 2)
            .otherwise(1)
        return listOf(if (ascending) cb.asc(rank) else cb.desc(rank), cb.asc(root.get<Long>("id")))
    }
}

/** The values accepted for the sortBy query parameter. */
enum class TaskSortField(val param: String, val strategy: TaskSortStrategy) {
    DUE_DATE("dueDate", DueDateSort),
    PRIORITY("priority", PrioritySort),
    ;

    companion object {
        fun parse(value: String) = entries.firstOrNull { it.param == value }
    }
}
