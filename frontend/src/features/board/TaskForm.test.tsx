import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../../api/http'
import * as projectsApi from '../../api/projects'
import * as tasksApi from '../../api/tasks'
import * as usersApi from '../../api/users'
import type { Task } from '../../api/types'
import { renderApp, seedSession } from '../../test-utils'

jest.mock('../../api/projects')
jest.mock('../../api/tasks')
jest.mock('../../api/users')
const projects = jest.mocked(projectsApi)
const api = jest.mocked(tasksApi)
const users = jest.mocked(usersApi)

const bob = { id: 2, name: 'Bob' }
const old: Task = { id: 9, title: 'Old task', description: 'Desc', status: 'IN_PROGRESS', priority: 'LOW', dueDate: '2020-01-01', assignee: bob, projectId: 5, createdAt: '', updatedAt: '', overdue: true }

const page = (content: Task[]) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 })

beforeEach(() => {
  jest.resetAllMocks()
  seedSession()
  projects.listProjects.mockResolvedValue([{ id: 5, name: 'Website', description: null, ownerId: 1, createdAt: '' }])
  api.getSummary.mockResolvedValue({ todo: 0, inProgress: 1, done: 0, overdue: 1, total: 1 })
  api.listTasks.mockImplementation(async (_p, q) => page(q.status === 'IN_PROGRESS' ? [old] : []))
  users.searchUsers.mockResolvedValue([bob])
})

async function openCreate() {
  renderApp('/projects/5')
  await userEvent.click(await screen.findByRole('button', { name: 'New task' }))
  return within(await screen.findByRole('dialog'))
}
async function openEdit() {
  renderApp('/projects/5')
  await userEvent.click(await screen.findByRole('button', { name: 'Edit Old task' }))
  return within(await screen.findByRole('dialog'))
}

describe('TaskForm: create', () => {
  it('requires a title and does not call the API', async () => {
    const form = await openCreate()
    await userEvent.click(form.getByRole('button', { name: 'Create task' }))
    expect(await form.findByText('Title is required')).toBeInTheDocument()
    expect(api.createTask).not.toHaveBeenCalled()
  })

  it('rejects a title over 120 characters', async () => {
    const form = await openCreate()
    await userEvent.click(form.getByLabelText('Title'))
    await userEvent.paste('x'.repeat(121))
    await userEvent.click(form.getByRole('button', { name: 'Create task' }))
    expect(await form.findByText('Title must be at most 120 characters')).toBeInTheDocument()
    expect(api.createTask).not.toHaveBeenCalled()
  })

  it('rejects a past due date on create', async () => {
    const form = await openCreate()
    await userEvent.type(form.getByLabelText('Title'), 'New')
    await userEvent.type(form.getByLabelText('Due date (optional)'), '2020-01-01')
    await userEvent.click(form.getByRole('button', { name: 'Create task' }))
    expect(await form.findByText('Due date must not be in the past')).toBeInTheDocument()
    expect(api.createTask).not.toHaveBeenCalled()
  })

  it('submits the trimmed values with the picked assignee, then closes and refreshes', async () => {
    api.createTask.mockResolvedValue({ ...old, id: 10 })
    const form = await openCreate()
    await userEvent.type(form.getByLabelText('Title'), '  Ship it  ')
    await userEvent.selectOptions(form.getByLabelText('Priority'), 'HIGH')
    await userEvent.type(form.getByLabelText('Due date (optional)'), '2099-05-06')
    await userEvent.type(form.getByLabelText('Assignee search'), 'Bo')
    await userEvent.click(await form.findByRole('button', { name: 'Bob' }))
    api.listTasks.mockClear()
    await userEvent.click(form.getByRole('button', { name: 'Create task' }))
    expect(api.createTask).toHaveBeenCalledWith(5, { title: 'Ship it', description: undefined, priority: 'HIGH', dueDate: '2099-05-06', assigneeId: 2 })
    await screen.findByRole('heading', { name: 'Website' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.listTasks).toHaveBeenCalled()
  })

  it('shows server field errors on the fields and keeps the dialog open', async () => {
    api.createTask.mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'Invalid', { title: 'Title already used' }))
    const form = await openCreate()
    await userEvent.type(form.getByLabelText('Title'), 'Dup')
    await userEvent.click(form.getByRole('button', { name: 'Create task' }))
    expect(await form.findByText('Title already used')).toBeInTheDocument()
    expect(form.getByLabelText('Title')).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('TaskForm: edit', () => {
  it('prefills and allows an unchanged past due date (D04); PUT sends the full body', async () => {
    api.updateTask.mockResolvedValue(old)
    const form = await openEdit()
    expect(form.getByLabelText('Title')).toHaveValue('Old task')
    expect(form.getByLabelText('Due date (optional)')).toHaveValue('2020-01-01')
    await userEvent.clear(form.getByLabelText('Title'))
    await userEvent.type(form.getByLabelText('Title'), 'Renamed')
    await userEvent.click(form.getByRole('button', { name: 'Save changes' }))
    expect(api.updateTask).toHaveBeenCalledWith(9, { title: 'Renamed', description: 'Desc', status: 'IN_PROGRESS', priority: 'LOW', dueDate: '2020-01-01', assigneeId: 2 })
  })

  it('rejects changing the date to a different past date', async () => {
    const form = await openEdit()
    await userEvent.clear(form.getByLabelText('Due date (optional)'))
    await userEvent.type(form.getByLabelText('Due date (optional)'), '2021-02-03')
    await userEvent.click(form.getByRole('button', { name: 'Save changes' }))
    expect(await form.findByText('Due date must not be in the past')).toBeInTheDocument()
    expect(api.updateTask).not.toHaveBeenCalled()
  })

  it('clearing the assignee sends assigneeId null', async () => {
    api.updateTask.mockResolvedValue(old)
    const form = await openEdit()
    await userEvent.click(form.getByRole('button', { name: 'Clear Assignee' }))
    await userEvent.click(form.getByRole('button', { name: 'Save changes' }))
    expect(api.updateTask).toHaveBeenCalledWith(9, expect.objectContaining({ assigneeId: null }))
  })
})
