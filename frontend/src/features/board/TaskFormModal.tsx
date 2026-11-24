import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/http'
import type { Task, TaskPriority, TaskStatus } from '../../api/types'
import { validateTask, type FieldErrors } from '../../auth/validation'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import Modal from '../../components/Modal'
import SelectField from '../../components/SelectField'
import AssigneePicker, { type Assignee } from './AssigneePicker'
import { utcToday } from './dates'
import { PRIORITY_LABEL, STATUS_LABEL } from './TaskCard'
import { useCreateTask, useUpdateTask } from './useBoard'

interface Props {
  projectId: number
  /** Present when editing. */
  task?: Task
  onClose: () => void
}

const priorityOptions = (Object.keys(PRIORITY_LABEL) as TaskPriority[]).map((p) => ({ value: p, label: PRIORITY_LABEL[p] }))
const statusOptions = (Object.keys(STATUS_LABEL) as TaskStatus[]).map((s) => ({ value: s, label: STATUS_LABEL[s] }))

export default function TaskFormModal({ projectId, task, onClose }: Props) {
  const editing = !!task
  const create = useCreateTask(projectId)
  const update = useUpdateTask(projectId, task?.id ?? 0)
  const [title, setTitle] = useState(task?.title ?? '')
  const [description, setDescription] = useState(task?.description ?? '')
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'MEDIUM')
  const [status, setStatus] = useState<TaskStatus>(task?.status ?? 'TODO')
  const [dueDate, setDueDate] = useState(task?.dueDate ?? '')
  const [assignee, setAssignee] = useState<Assignee | null>(task?.assignee ?? null)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const pending = create.isPending || update.isPending

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError(undefined)
    const found = validateTask({ title, description, dueDate, originalDueDate: task?.dueDate ?? null, editing, today: utcToday() })
    setErrors(found)
    if (Object.keys(found).length > 0) return
    try {
      if (task) {
        await update.mutateAsync({
          title: title.trim(),
          description: description.trim() || null,
          status,
          priority,
          dueDate: dueDate || null,
          assigneeId: assignee?.id ?? null,
        })
      } else {
        await create.mutateAsync({
          title: title.trim(),
          description: description.trim() || undefined,
          priority,
          dueDate: dueDate || undefined,
          assigneeId: assignee?.id,
        })
      }
      onClose()
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) setErrors(err.fieldErrors)
      else setFormError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <Modal title={editing ? 'Edit task' : 'New task'} onClose={onClose}>
      <form onSubmit={onSubmit} noValidate>
        <FormField label="Title" value={title} error={errors.title} onChange={(e) => setTitle(e.target.value)} />
        <FormField label="Description (optional)" multiline value={description} error={errors.description} onChange={(e) => setDescription(e.target.value)} />
        <SelectField label="Priority" options={priorityOptions} value={priority} error={errors.priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} />
        {editing && <SelectField label="Status" options={statusOptions} value={status} error={errors.status} onChange={(e) => setStatus(e.target.value as TaskStatus)} />}
        <FormField label="Due date (optional)" type="date" value={dueDate} error={errors.dueDate} onChange={(e) => setDueDate(e.target.value)} />
        <AssigneePicker label="Assignee" value={assignee} onChange={setAssignee} error={errors.assigneeId} />
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <div className="modal-actions">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={pending}>{pending ? 'Saving…' : editing ? 'Save changes' : 'Create task'}</Button>
        </div>
      </form>
    </Modal>
  )
}
