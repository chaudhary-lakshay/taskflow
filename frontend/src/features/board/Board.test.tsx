import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../../api/http'
import * as projectsApi from '../../api/projects'
import * as tasksApi from '../../api/tasks'
import * as usersApi from '../../api/users'
import type { Task, TaskPriority, TaskStatus } from '../../api/types'
import { renderApp, seedSession } from '../../test-utils'

jest.mock('../../api/projects')
jest.mock('../../api/tasks')
jest.mock('../../api/users')
const projects = jest.mocked(projectsApi)
const api = jest.mocked(tasksApi)
const users = jest.mocked(usersApi)

const PAST = '2020-01-01'
const FUTURE = '2099-01-01'
let seq = 0
const mk = (title: string, status: TaskStatus = 'TODO', priority: TaskPriority = 'MEDIUM', dueDate: string | null = FUTURE, assignee: Task['assignee'] = null): Task => ({
  id: ++seq, title, description: null, status, priority, dueDate, assignee, projectId: 5, createdAt: '', updatedAt: '', overdue: false,
})

/** A small stateful backend: filters, sorts and pages like the real one, changeStatus mutates. */
function fakeBackend(all: Task[]) {
  api.listTasks.mockImplementation(async (_pid, p) => {
    let rows = all.filter((t) => t.status === p.status && (!p.priority || t.priority === p.priority) && (p.assigneeId === undefined || t.assignee?.id === p.assigneeId))
    rows = [...rows].sort((a, b) => (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999') * (p.direction === 'asc' ? 1 : -1))
    const content = rows.slice(p.page * p.size, (p.page + 1) * p.size)
    return { content, page: p.page, size: p.size, totalElements: rows.length, totalPages: Math.ceil(rows.length / p.size) }
  })
  api.getSummary.mockImplementation(async () => {
    const n = (s: TaskStatus) => all.filter((t) => t.status === s).length
    return { todo: n('TODO'), inProgress: n('IN_PROGRESS'), done: n('DONE'), overdue: all.filter((t) => t.status !== 'DONE' && t.dueDate !== null && t.dueDate < '2026-01-01').length, total: all.length }
  })
  api.changeStatus.mockImplementation(async (id, status) => {
    const t = all.find((x) => x.id === id)!
    t.status = status
    return t
  })
  api.deleteTask.mockImplementation(async (id) => {
    all.splice(all.findIndex((x) => x.id === id), 1)
  })
}

async function renderBoard(route: string) {
  const view = renderApp(route)
  await screen.findByRole('heading', { name: 'Website' })
  return view
}

const column = (name: string) => within(screen.getByRole('region', { name: new RegExp('^' + name) }))
const summaryValue = (label: string) => within(screen.getByLabelText('Project summary')).getByText(label).nextElementSibling

beforeEach(() => {
  jest.resetAllMocks()
  seq = 0
  seedSession()
  projects.listProjects.mockResolvedValue([{ id: 5, name: 'Website', description: null, ownerId: 1, createdAt: '' }])
})

describe('board states', () => {
  it('shows loading states first', async () => {
    api.getSummary.mockReturnValue(new Promise(() => {}))
    api.listTasks.mockReturnValue(new Promise(() => {}))
    await renderBoard('/projects/5')
    expect(await screen.findByText('Loading summary…')).toBeInTheDocument()
    expect(screen.getByText('Loading To Do tasks…')).toBeInTheDocument()
    expect(screen.getByText('Loading In Progress tasks…')).toBeInTheDocument()
    expect(screen.getByText('Loading Done tasks…')).toBeInTheDocument()
  })

  it('shows an empty state in every column and zero counts', async () => {
    fakeBackend([])
    await renderBoard('/projects/5')
    await waitFor(() => expect(screen.getAllByText('No tasks here yet.')).toHaveLength(3))
    expect(summaryValue('Total')).toHaveTextContent('0')
  })

  it('shows the project name, the summary numbers and the tasks in their columns', async () => {
    fakeBackend([mk('A'), mk('B', 'IN_PROGRESS'), mk('C', 'DONE'), mk('D', 'DONE')])
    await renderBoard('/projects/5')
    expect(await screen.findByRole('heading', { name: 'Website' })).toBeInTheDocument()
    await column('To Do').findByText('A')
    await column('In Progress').findByText('B')
    await column('Done').findByText('C')
    await waitFor(() => expect(summaryValue('Total')).toHaveTextContent('4'))
    expect(summaryValue('Done')).toHaveTextContent('2')
    expect(summaryValue('To Do')).toHaveTextContent('1')
    for (const s of ['TODO', 'IN_PROGRESS', 'DONE']) expect(api.listTasks).toHaveBeenCalledWith(5, expect.objectContaining({ status: s, direction: 'asc', page: 0, size: 10 }))
  })

  it('shows a column error with Retry without breaking the other columns or the summary', async () => {
    fakeBackend([mk('A'), mk('B', 'DONE')])
    const real = api.listTasks.getMockImplementation()!
    api.listTasks.mockImplementation((pid, p) => (p.status === 'IN_PROGRESS' ? Promise.reject(new ApiError(500, 'X', 'column down')) : real(pid, p)))
    await renderBoard('/projects/5')
    expect(await screen.findByText('Could not load In Progress tasks: column down')).toBeInTheDocument()
    await column('To Do').findByText('A')
    await column('Done').findByText('B')
    api.listTasks.mockImplementation(real)
    await userEvent.click(within(screen.getByRole('alert')).getByRole('button', { name: 'Retry' }))
    expect(await column('In Progress').findByText('No tasks here yet.')).toBeInTheDocument()
  })

  it('shows a summary error with Retry', async () => {
    fakeBackend([mk('A')])
    const real = api.getSummary.getMockImplementation()!
    api.getSummary.mockRejectedValueOnce(new ApiError(500, 'X', 'summary down'))
    await renderBoard('/projects/5')
    expect(await screen.findByText('Could not load the summary: summary down')).toBeInTheDocument()
    api.getSummary.mockImplementation(real)
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await waitFor(() => expect(summaryValue('Total')).toHaveTextContent('1'))
  })
})

describe('paging', () => {
  it('pages a column with Next/Previous so no task is hidden', async () => {
    fakeBackend(Array.from({ length: 12 }, (_, i) => mk('T' + String(i + 1).padStart(2, '0'), 'TODO', 'MEDIUM', '2099-01-' + String(i + 1).padStart(2, '0'))))
    await renderBoard('/projects/5')
    const todo = column('To Do')
    await todo.findByText('T01')
    expect(todo.queryByText('T11')).not.toBeInTheDocument()
    await userEvent.click(todo.getByRole('button', { name: 'Next' }))
    expect(await todo.findByText('T11')).toBeInTheDocument()
    expect(todo.getByText('T12')).toBeInTheDocument()
    expect(api.listTasks).toHaveBeenCalledWith(5, expect.objectContaining({ status: 'TODO', page: 1 }))
    expect(todo.getByRole('button', { name: 'Next' })).toBeDisabled()
    await userEvent.click(todo.getByRole('button', { name: 'Previous' }))
    expect(await todo.findByText('T01')).toBeInTheDocument()
  })
})

describe('filters', () => {
  it('priority filter refetches all three columns with the priority and keeps it in the URL', async () => {
    fakeBackend([mk('Low one', 'TODO', 'LOW'), mk('High one', 'TODO', 'HIGH'), mk('High done', 'DONE', 'HIGH')])
    await renderBoard('/projects/5')
    await column('To Do').findByText('Low one')
    api.listTasks.mockClear()
    await userEvent.selectOptions(screen.getByLabelText('Priority filter'), 'HIGH')
    await waitFor(() => expect(column('To Do').queryByText('Low one')).not.toBeInTheDocument())
    for (const s of ['TODO', 'IN_PROGRESS', 'DONE']) expect(api.listTasks).toHaveBeenCalledWith(5, expect.objectContaining({ status: s, priority: 'HIGH' }))
    expect(column('To Do').getByText('High one')).toBeInTheDocument()
    expect(summaryValue('Total')).toHaveTextContent('3') // the summary ignores filters
  })

  it('reads the filters from the URL on load (reload keeps them)', async () => {
    fakeBackend([mk('X', 'TODO', 'HIGH')])
    await renderBoard('/projects/5?priority=HIGH&assignee=2&assigneeName=Bob&dir=desc')
    await screen.findByRole('heading', { name: 'Website' })
    await waitFor(() => expect(api.listTasks).toHaveBeenCalledWith(5, expect.objectContaining({ priority: 'HIGH', assigneeId: 2, direction: 'desc' })))
    expect(screen.getByLabelText('Priority filter')).toHaveValue('HIGH')
    expect(screen.getByLabelText('Sort by due date')).toHaveValue('desc')
    expect(screen.getAllByText('Bob').length).toBeGreaterThan(0)
  })

  it('sort direction applies to all columns and reorders', async () => {
    fakeBackend([mk('Soon', 'TODO', 'LOW', '2098-01-01'), mk('Later', 'TODO', 'LOW', '2099-01-01')])
    await renderBoard('/projects/5')
    await column('To Do').findByText('Soon')
    const order = () => column('To Do').getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(order()).toEqual(['Soon', 'Later'])
    api.listTasks.mockClear()
    await userEvent.selectOptions(screen.getByLabelText('Sort by due date'), 'desc')
    await waitFor(() => expect(order()).toEqual(['Later', 'Soon']))
    for (const s of ['TODO', 'IN_PROGRESS', 'DONE']) expect(api.listTasks).toHaveBeenCalledWith(5, expect.objectContaining({ status: s, direction: 'desc' }))
  })

  it('assignee filter uses the user lookup and can be cleared', async () => {
    const bob = { id: 2, name: 'Bob' }
    fakeBackend([mk('Mine', 'TODO', 'LOW', FUTURE, bob), mk('Other')])
    users.searchUsers.mockResolvedValue([bob])
    await renderBoard('/projects/5')
    await column('To Do').findByText('Other')
    await userEvent.type(screen.getByLabelText('Assignee filter search'), 'Bo')
    await userEvent.click(await screen.findByRole('button', { name: 'Bob' }))
    expect(users.searchUsers).toHaveBeenCalledWith('Bo')
    await waitFor(() => expect(api.listTasks).toHaveBeenCalledWith(5, expect.objectContaining({ assigneeId: 2 })))
    await waitFor(() => expect(column('To Do').queryByText('Other')).not.toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: 'Clear Assignee filter' }))
    expect(await column('To Do').findByText('Other')).toBeInTheDocument()
  })
})

describe('status change', () => {
  it('on success moves the task and refreshes the columns and the summary', async () => {
    fakeBackend([mk('Move me')])
    await renderBoard('/projects/5')
    await column('To Do').findByText('Move me')
    await waitFor(() => expect(summaryValue('Done')).toHaveTextContent('0'))
    api.listTasks.mockClear()
    api.getSummary.mockClear()
    await userEvent.selectOptions(screen.getByLabelText('Status of Move me'), 'DONE')
    expect(api.changeStatus).toHaveBeenCalledWith(1, 'DONE')
    expect(await column('Done').findByText('Move me')).toBeInTheDocument()
    expect(column('To Do').queryByText('Move me')).not.toBeInTheDocument()
    expect(api.listTasks).toHaveBeenCalledWith(5, expect.objectContaining({ status: 'TODO' }))
    expect(api.listTasks).toHaveBeenCalledWith(5, expect.objectContaining({ status: 'DONE' }))
    await waitFor(() => expect(summaryValue('Done')).toHaveTextContent('1'))
    expect(api.getSummary).toHaveBeenCalled()
  })

  it('on failure keeps the old status, shows an error, and Retry works', async () => {
    fakeBackend([mk('Stuck')])
    const real = api.changeStatus.getMockImplementation()!
    api.changeStatus.mockRejectedValueOnce(new ApiError(500, 'X', 'nope'))
    await renderBoard('/projects/5')
    await column('To Do').findByText('Stuck')
    await userEvent.selectOptions(screen.getByLabelText('Status of Stuck'), 'IN_PROGRESS')
    const alert = await screen.findByText('Could not change the status: nope')
    expect(screen.getByLabelText('Status of Stuck')).toHaveValue('TODO')
    expect(column('To Do').getByText('Stuck')).toBeInTheDocument()
    api.changeStatus.mockImplementation(real)
    await userEvent.click(within(alert.parentElement!).getByRole('button', { name: 'Retry' }))
    expect(await column('In Progress').findByText('Stuck')).toBeInTheDocument()
    expect(api.changeStatus).toHaveBeenLastCalledWith(1, 'IN_PROGRESS')
  })
})

describe('overdue on the board', () => {
  it('flags only open past-due tasks', async () => {
    fakeBackend([mk('Late', 'TODO', 'MEDIUM', PAST), mk('Finished', 'DONE', 'MEDIUM', PAST)])
    await renderBoard('/projects/5')
    await column('To Do').findByText('Late')
    expect(column('To Do').getByText('Overdue')).toBeInTheDocument()
    await column('Done').findByText('Finished')
    expect(column('Done').queryByText('Overdue')).not.toBeInTheDocument()
  })
})

describe('delete', () => {
  it('asks first; Cancel does nothing; confirm deletes and refreshes', async () => {
    fakeBackend([mk('Bye')])
    await renderBoard('/projects/5')
    await column('To Do').findByText('Bye')
    await userEvent.click(screen.getByRole('button', { name: 'Delete Bye' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('Delete Bye?')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    expect(api.deleteTask).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Delete Bye' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }))
    expect(api.deleteTask).toHaveBeenCalledWith(1)
    await waitFor(() => expect(column('To Do').queryByText('Bye')).not.toBeInTheDocument())
    await waitFor(() => expect(summaryValue('Total')).toHaveTextContent('0'))
  })

  it('shows an error with a retry when the delete fails', async () => {
    fakeBackend([mk('Keep')])
    api.deleteTask.mockRejectedValueOnce(new ApiError(500, 'X', 'cannot'))
    await renderBoard('/projects/5')
    await column('To Do').findByText('Keep')
    await userEvent.click(screen.getByRole('button', { name: 'Delete Keep' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete' }))
    expect(await screen.findByText('Could not delete the task: cannot')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry delete' })).toBeInTheDocument()
  })
})

describe('cache keys', () => {
  it('every board and lookup query is namespaced by user (and project)', async () => {
    fakeBackend([mk('A')])
    const { queryClient } = await renderBoard('/projects/5')
    await column('To Do').findByText('A')
    const keys = queryClient.getQueryCache().getAll().map((q) => q.queryKey as unknown[])
    const boardKeys = keys.filter((k) => k.includes('tasks') || k.includes('summary'))
    expect(boardKeys.length).toBeGreaterThanOrEqual(4)
    for (const k of boardKeys) expect(k.slice(0, 4)).toEqual(['user', 1, 'project', 5])
  })
})
