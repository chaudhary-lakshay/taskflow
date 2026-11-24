import { request } from './http'
import type { Page, Summary, Task, TaskPriority, TaskStatus } from './types'

export interface TaskListParams {
  status: TaskStatus
  priority?: TaskPriority
  assigneeId?: number
  direction: 'asc' | 'desc'
  page: number
  size: number
}

export interface CreateTaskBody {
  title: string
  description?: string
  priority: TaskPriority
  dueDate?: string
  assigneeId?: number
}

/** PUT replaces everything editable; null clears description, dueDate and assignee. */
export interface UpdateTaskBody {
  title: string
  description: string | null
  status: TaskStatus
  priority: TaskPriority
  dueDate: string | null
  assigneeId: number | null
}

export const listTasks = (projectId: number, p: TaskListParams) => {
  const q = new URLSearchParams({ status: p.status, sortBy: 'dueDate', direction: p.direction, page: String(p.page), size: String(p.size) })
  if (p.priority) q.set('priority', p.priority)
  if (p.assigneeId !== undefined) q.set('assigneeId', String(p.assigneeId))
  return request<Page<Task>>('GET', '/projects/' + projectId + '/tasks?' + q.toString())
}

export const getSummary = (projectId: number) => request<Summary>('GET', '/projects/' + projectId + '/summary')

export const createTask = (projectId: number, body: CreateTaskBody) => request<Task>('POST', '/projects/' + projectId + '/tasks', body)

export const updateTask = (id: number, body: UpdateTaskBody) => request<Task>('PUT', '/tasks/' + id, body)

export const changeStatus = (id: number, status: TaskStatus) => request<Task>('PATCH', '/tasks/' + id + '/status', { status })

export const deleteTask = (id: number) => request<void>('DELETE', '/tasks/' + id)
