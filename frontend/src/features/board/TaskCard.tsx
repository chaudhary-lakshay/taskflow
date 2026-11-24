import type { Task, TaskStatus } from '../../api/types'
import Button from '../../components/Button'
import { isOverdue } from './dates'

export const STATUS_LABEL: Record<TaskStatus, string> = { TODO: 'To Do', IN_PROGRESS: 'In Progress', DONE: 'Done' }
export const PRIORITY_LABEL = { LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' } as const

interface Props {
  task: Task
  /** ISO date (UTC) used for the overdue rule; passed in so it can be tested. */
  today: string
  busy?: boolean
  error?: string
  onStatusChange: (status: TaskStatus) => void
  onRetry: () => void
  onEdit: () => void
  onDelete: () => void
}

/** One task. Overdue is shown with a text label as well as a colour, and only for open tasks due before today. */
export default function TaskCard({ task, today, busy, error, onStatusChange, onRetry, onEdit, onDelete }: Props) {
  const overdue = isOverdue(task.dueDate, task.status, today)
  return (
    <li className={'task-card' + (overdue ? ' overdue' : '')}>
      <h3>{task.title}</h3>
      <p className="task-meta">
        <span className={'badge badge-' + task.priority.toLowerCase()}>{PRIORITY_LABEL[task.priority]} priority</span>
        {overdue && <span className="badge badge-overdue">Overdue</span>}
      </p>
      <p className="task-meta">{task.dueDate ? 'Due ' + task.dueDate : 'No due date'}</p>
      <p className="task-meta">{task.assignee ? 'Assigned to ' + task.assignee.name : 'Unassigned'}</p>
      <div className="task-status">
        <label>
          <span>Status of {task.title}</span>
          <select value={task.status} disabled={busy} onChange={(e) => onStatusChange(e.target.value as TaskStatus)}>
            {(Object.keys(STATUS_LABEL) as TaskStatus[]).map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </select>
        </label>
        {busy && <span role="status">Saving…</span>}
      </div>
      {error && (
        <div role="alert" className="form-error">
          <p>Could not change the status: {error}</p>
          <Button variant="secondary" onClick={onRetry}>
            Retry
          </Button>
        </div>
      )}
      <div className="task-actions">
        <Button variant="secondary" onClick={onEdit} aria-label={'Edit ' + task.title}>
          Edit
        </Button>
        <Button variant="secondary" onClick={onDelete} aria-label={'Delete ' + task.title}>
          Delete
        </Button>
      </div>
    </li>
  )
}
