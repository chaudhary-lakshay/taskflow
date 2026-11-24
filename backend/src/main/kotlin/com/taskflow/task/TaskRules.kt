package com.taskflow.task

import java.time.LocalDate

object TaskRules {
    /** Overdue = due before today (UTC calendar day) and not DONE. Today is not overdue; no date is never overdue. */
    fun isOverdue(dueDate: LocalDate?, status: TaskStatus, today: LocalDate) =
        dueDate != null && dueDate.isBefore(today) && status != TaskStatus.DONE
}
