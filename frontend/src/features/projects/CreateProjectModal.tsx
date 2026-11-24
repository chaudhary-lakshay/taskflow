import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/http'
import { validateProject, type FieldErrors } from '../../auth/validation'
import Button from '../../components/Button'
import FormField from '../../components/FormField'
import Modal from '../../components/Modal'
import { useCreateProject } from './useProjects'

export default function CreateProjectModal({ onClose }: { onClose: () => void }) {
  const create = useCreateProject()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError(undefined)
    const found = validateProject({ name, description })
    setErrors(found)
    if (Object.keys(found).length > 0) return
    try {
      await create.mutateAsync({ name: name.trim(), description: description.trim() || undefined })
      onClose()
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) setErrors(err.fieldErrors)
      else setFormError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <Modal title="New project" onClose={onClose}>
      <form onSubmit={onSubmit} noValidate>
        <FormField label="Name" value={name} error={errors.name} onChange={(e) => setName(e.target.value)} />
        <FormField label="Description (optional)" multiline value={description} error={errors.description} onChange={(e) => setDescription(e.target.value)} />
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <div className="modal-actions">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={create.isPending}>{create.isPending ? 'Creating…' : 'Create project'}</Button>
        </div>
      </form>
    </Modal>
  )
}
