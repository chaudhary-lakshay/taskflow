import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../../api/http'
import * as projectsApi from '../../api/projects'
import type { Project } from '../../api/types'
import { renderApp, seedSession } from '../../test-utils'

jest.mock('../../api/projects')
const api = jest.mocked(projectsApi)

const project = (id: number, name: string, description: string | null = null): Project => ({ id, name, description, ownerId: 1, createdAt: '2026-11-24T00:00:00Z' })

beforeEach(() => {
  jest.resetAllMocks()
  seedSession()
})

describe('Projects list', () => {
  it('shows a loading state, then the projects as links', async () => {
    let resolve!: (p: Project[]) => void
    api.listProjects.mockReturnValue(new Promise((r) => (resolve = r)))
    renderApp('/projects')
    expect(await screen.findByText('Loading projects…')).toBeInTheDocument()
    resolve([project(1, 'Website', 'Launch'), project(2, 'Ops')])
    const link = await screen.findByRole('link', { name: /Website/ })
    expect(link).toHaveAttribute('href', '/projects/1')
    expect(link).toHaveTextContent('Launch')
    expect(screen.getByRole('link', { name: /Ops/ })).toBeInTheDocument()
    expect(screen.queryByText('Loading projects…')).not.toBeInTheDocument()
  })

  it('shows an empty state', async () => {
    api.listProjects.mockResolvedValue([])
    renderApp('/projects')
    expect(await screen.findByText(/no projects yet/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'New project' })).toBeInTheDocument()
  })

  it('shows the error with a working Retry button', async () => {
    api.listProjects.mockRejectedValueOnce(new ApiError(500, 'INTERNAL_ERROR', 'Something went wrong'))
    api.listProjects.mockResolvedValueOnce([project(1, 'Website')])
    renderApp('/projects')
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load your projects: Something went wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('link', { name: /Website/ })).toBeInTheDocument()
    expect(api.listProjects).toHaveBeenCalledTimes(2)
  })
})

describe('Create project modal', () => {
  beforeEach(() => api.listProjects.mockResolvedValue([]))

  async function open() {
    renderApp('/projects')
    const button = await screen.findByRole('button', { name: 'New project' })
    await userEvent.click(button)
    return { button, dialog: await screen.findByRole('dialog', { name: 'New project' }) }
  }

  it('is an accessible dialog that focuses the first field', async () => {
    const { dialog } = await open()
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(within(dialog).getByLabelText('Name')).toHaveFocus()
  })

  it('requires a name and sends nothing when it is empty', async () => {
    const { dialog } = await open()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create project' }))
    expect(await within(dialog).findByText('Name is required')).toBeInTheDocument()
    expect(api.createProject).not.toHaveBeenCalled()
  })

  it('rejects a name over 120 characters on the client', async () => {
    const { dialog } = await open()
    await userEvent.click(within(dialog).getByLabelText('Name'))
    await userEvent.paste('x'.repeat(121))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create project' }))
    expect(await within(dialog).findByText('Name must be at most 120 characters')).toBeInTheDocument()
    expect(api.createProject).not.toHaveBeenCalled()
  })

  it('creates the project, refreshes the list, closes and returns focus to the opener', async () => {
    api.createProject.mockResolvedValue(project(7, 'New one'))
    const { button, dialog } = await open()
    api.listProjects.mockResolvedValue([project(7, 'New one', 'About it')])
    await userEvent.type(within(dialog).getByLabelText('Name'), '  New one ')
    await userEvent.type(within(dialog).getByLabelText('Description (optional)'), 'About it')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create project' }))

    expect(api.createProject).toHaveBeenCalledWith({ name: 'New one', description: 'About it' })
    expect(await screen.findByRole('link', { name: /New one/ })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => expect(button).toHaveFocus())
  })

  it('shows server field errors and keeps the modal open', async () => {
    api.createProject.mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'Invalid request', { name: 'Name must be at most 120 characters' }))
    const { dialog } = await open()
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Fine')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create project' }))
    expect(await within(dialog).findByText('Name must be at most 120 characters')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('closes on Escape and on Cancel; Tab stays inside the dialog', async () => {
    const { dialog } = await open()
    const cancel = within(dialog).getByRole('button', { name: 'Cancel' })
    const create = within(dialog).getByRole('button', { name: 'Create project' })
    create.focus()
    await userEvent.tab()
    expect(within(dialog).getByLabelText('Name')).toHaveFocus() // wrapped from the last control to the first
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await userEvent.click(screen.getByRole('button', { name: 'New project' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(cancel).toBeDefined()
  })
})
