import { useState } from 'react'
import type { Task, TaskStatus } from '../../api/types'
import Button from '../../components/Button'
import type { BoardFilters } from './filters'
import { filtersActive } from './filters'
import TaskCard, { STATUS_LABEL } from './TaskCard'
import { useChangeStatus, useColumnTasks } from './useBoard'

interface CardProps {
  task: Task
  projectId: number
  today: string
  onEdit: (t: Task) => void
  onDelete: (t: Task) => void
}

/** Owns the status change of one card: on failure the card keeps its old status and shows an error with Retry. */
function ConnectedCard({ task, projectId, today, onEdit, onDelete }: CardProps) {
  const change = useChangeStatus(projectId)
  const [wanted, setWanted] = useState<TaskStatus>(task.status)
  const send = (status: TaskStatus) => {
    setWanted(status)
    change.mutate({ id: task.id, status })
  }
  return (
    <TaskCard
      task={task}
      today={today}
      busy={change.isPending}
      error={change.isError ? change.error.message : undefined}
      onStatusChange={send}
      onRetry={() => send(wanted)}
      onEdit={() => onEdit(task)}
      onDelete={() => onDelete(task)}
    />
  )
}

interface Props {
  projectId: number
  status: TaskStatus
  filters: BoardFilters
  today: string
  onEdit: (t: Task) => void
  onDelete: (t: Task) => void
}

export default function BoardColumn({ projectId, status, filters, today, onEdit, onDelete }: Props) {
  const [page, setPage] = useState(0)
  const tasks = useColumnTasks(projectId, status, filters, page)
  const data = tasks.data

  // If the last task of the last page was moved or deleted, step back instead of showing an empty page.
  // (Adjusting state while rendering is React's documented alternative to an effect for this.)
  if (data && data.content.length === 0 && page > 0 && !tasks.isFetching) setPage(Math.max(0, data.totalPages - 1))

  const label = STATUS_LABEL[status]
  return (
    <section className="column" aria-labelledby={'col-' + status}>
      <h2 id={'col-' + status}>
        {label}
        {data && <span className="count"> ({data.totalElements})</span>}
      </h2>

      {tasks.isPending && <p role="status">Loading {label} tasks…</p>}

      {tasks.isError && (
        <div role="alert" className="state-box">
          <p>Could not load {label} tasks: {tasks.error.message}</p>
          <Button onClick={() => tasks.refetch()}>Retry</Button>
        </div>
      )}

      {data && data.totalElements === 0 && <p className="muted">{filtersActive(filters) ? 'No tasks match the filters.' : 'No tasks here yet.'}</p>}

      {data && data.content.length > 0 && (
        <ul className="task-list">
          {data.content.map((t) => (
            <ConnectedCard key={t.id} task={t} projectId={projectId} today={today} onEdit={onEdit} onDelete={onDelete} />
          ))}
        </ul>
      )}

      {data && data.totalPages > 1 && (
        <nav className="pager" aria-label={label + ' pages'}>
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
          <span>Page {data.page + 1} of {data.totalPages}</span>
          <Button variant="secondary" disabled={page >= data.totalPages - 1} onClick={() => setPage(page + 1)}>Next</Button>
        </nav>
      )}
    </section>
  )
}
