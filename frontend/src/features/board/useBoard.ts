import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { changeStatus, createTask, deleteTask, getSummary, listTasks, updateTask, type CreateTaskBody, type UpdateTaskBody } from '../../api/tasks'
import type { TaskStatus } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import type { BoardFilters } from './filters'

export const PAGE_SIZE = 10

/** Every board query starts with the user and the project, so nothing leaks between users or projects. */
export const boardKey = (userId: number | undefined, projectId: number) => ['user', userId, 'project', projectId] as const

export function useSummary(projectId: number) {
  const { user } = useAuth()
  return useQuery({ queryKey: [...boardKey(user?.id, projectId), 'summary'], queryFn: () => getSummary(projectId), enabled: !!user })
}

/** One column = one query: the status filter, the shared filters and its own page. */
export function useColumnTasks(projectId: number, status: TaskStatus, filters: BoardFilters, page: number) {
  const { user } = useAuth()
  return useQuery({
    queryKey: [...boardKey(user?.id, projectId), 'tasks', status, filters.priority ?? null, filters.assignee?.id ?? null, filters.direction, page],
    queryFn: () =>
      listTasks(projectId, { status, priority: filters.priority, assigneeId: filters.assignee?.id, direction: filters.direction, page, size: PAGE_SIZE }),
    enabled: !!user,
    placeholderData: keepPreviousData,
  })
}

/** After any change, refetch every column and the summary of this project (a superset of the affected ones). */
function useRefreshBoard(projectId: number) {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: boardKey(user?.id, projectId) })
}

export function useCreateTask(projectId: number) {
  const refresh = useRefreshBoard(projectId)
  return useMutation({ mutationFn: (body: CreateTaskBody) => createTask(projectId, body), onSuccess: refresh })
}

export function useUpdateTask(projectId: number, taskId: number) {
  const refresh = useRefreshBoard(projectId)
  return useMutation({ mutationFn: (body: UpdateTaskBody) => updateTask(taskId, body), onSuccess: refresh })
}

export function useChangeStatus(projectId: number) {
  const refresh = useRefreshBoard(projectId)
  return useMutation({ mutationFn: (v: { id: number; status: TaskStatus }) => changeStatus(v.id, v.status), onSuccess: refresh })
}

export function useDeleteTask(projectId: number) {
  const refresh = useRefreshBoard(projectId)
  return useMutation({ mutationFn: (id: number) => deleteTask(id), onSuccess: refresh })
}
