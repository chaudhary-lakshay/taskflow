package com.taskflow.task

import com.taskflow.project.Project
import com.taskflow.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.time.LocalDate

@Entity
@Table(name = "tasks")
class Task(
    @Column(nullable = false, length = 120)
    var title: String,
    @Column(columnDefinition = "text")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: TaskStatus = TaskStatus.TODO,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var priority: TaskPriority = TaskPriority.MEDIUM,
    @Column(name = "due_date")
    var dueDate: LocalDate? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    var assignee: User? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    val project: Project,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS) // Postgres timestamptz keeps microseconds

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS) // Postgres timestamptz keeps microseconds
    }
}
