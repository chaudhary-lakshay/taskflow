import type { Task } from '../../api/types'
import Button from '../../components/Button'
import Modal from '../../components/Modal'
import { useDeleteTask } from './useBoard'

export default function DeleteTaskModal({ projectId, task, onClose }: { projectId: number; task: Task; onClose: () => void }) {
  const remove = useDeleteTask(projectId)
  return (
    <Modal title="Delete task" onClose={onClose}>
      <p>
        Delete <strong>{task.title}</strong>? This cannot be undone.
      </p>
      {remove.isError && (
        <p className="form-error" role="alert">
          Could not delete the task: {remove.error.message}
        </p>
      )}
      <div className="modal-actions">
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button disabled={remove.isPending} onClick={() => remove.mutate(task.id, { onSuccess: onClose })}>
          {remove.isPending ? 'Deleting…' : remove.isError ? 'Retry delete' : 'Delete'}
        </Button>
      </div>
    </Modal>
  )
}
