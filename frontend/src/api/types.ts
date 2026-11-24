export interface User {
  id: number
  name: string
  email: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface Project {
  id: number
  name: string
  description: string | null
  ownerId: number
  createdAt: string
}

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface Task {
  id: number
  title: string
  description: string | null
  status: TaskStatus
  priority: TaskPriority
  dueDate: string | null // ISO date, no time
  assignee: { id: number; name: string } | null
  projectId: number
  createdAt: string
  updatedAt: string
  overdue: boolean
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Summary {
  todo: number
  inProgress: number
  done: number
  overdue: number
  total: number
}

export interface UserOption {
  id: number
  name: string
}
