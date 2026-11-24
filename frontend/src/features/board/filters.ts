import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import type { TaskPriority } from '../../api/types'

export interface BoardFilters {
  priority?: TaskPriority
  assignee?: { id: number; name: string }
  direction: 'asc' | 'desc'
}

const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH']

/** Filters live in the URL (?priority=HIGH&assignee=5&assigneeName=Bob&dir=desc): they survive a reload and can be shared. */
export function useBoardFilters(): [BoardFilters, (next: BoardFilters) => void] {
  const [params, setParams] = useSearchParams()
  const priorityParam = params.get('priority')
  const assigneeId = Number(params.get('assignee'))
  const assigneeName = params.get('assigneeName')
  const dir = params.get('dir')

  const filters = useMemo<BoardFilters>(
    () => ({
      priority: PRIORITIES.find((p) => p === priorityParam),
      assignee: Number.isInteger(assigneeId) && assigneeId > 0 ? { id: assigneeId, name: assigneeName ?? 'User ' + assigneeId } : undefined,
      direction: dir === 'desc' ? 'desc' : 'asc',
    }),
    [priorityParam, assigneeId, assigneeName, dir],
  )

  const setFilters = useCallback(
    (next: BoardFilters) => {
      const p = new URLSearchParams()
      if (next.priority) p.set('priority', next.priority)
      if (next.assignee) {
        p.set('assignee', String(next.assignee.id))
        p.set('assigneeName', next.assignee.name)
      }
      if (next.direction === 'desc') p.set('dir', 'desc')
      setParams(p, { replace: true })
    },
    [setParams],
  )
  return [filters, setFilters]
}

export const filtersActive = (f: BoardFilters) => f.priority !== undefined || f.assignee !== undefined
