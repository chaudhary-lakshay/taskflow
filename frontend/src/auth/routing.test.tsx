import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as authApi from '../api/auth'
import * as projectsApi from '../api/projects'
import { alice, renderApp, seedSession } from '../test-utils'

jest.mock('../api/auth')
jest.mock('../api/projects')
const auth = jest.mocked(authApi)
const projects = jest.mocked(projectsApi)

beforeEach(() => {
  jest.resetAllMocks()
  projects.listProjects.mockResolvedValue([{ id: 5, name: 'Website', description: null, ownerId: 1, createdAt: '2026-11-24T00:00:00Z' }])
})

describe('protected routes', () => {
  it('sends a logged-out visitor from /projects to /login without calling the API', async () => {
    renderApp('/projects')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(projects.listProjects).not.toHaveBeenCalled()
  })

  it('redirects / to /projects (then to /login when logged out)', async () => {
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
  })

  it('returns the user to the page they wanted after logging in', async () => {
    auth.loginUser.mockResolvedValue({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 3600, user: alice })
    renderApp('/projects/5')
    await userEvent.type(await screen.findByLabelText('Email'), 'alice@example.test')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByRole('heading', { name: 'Website' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Back to projects/ })).toHaveAttribute('href', '/projects')
  })

  it('a signed-in user opening /login or / is sent to /projects', async () => {
    seedSession()
    renderApp('/login')
    expect(await screen.findByRole('heading', { name: 'Projects' })).toBeInTheDocument()
  })

  it('an unknown project id shows a not-found message with the back link', async () => {
    seedSession()
    renderApp('/projects/999')
    expect(await screen.findByText('Project not found.')).toBeInTheDocument()
  })

  it('logout clears the session and the query cache and returns to /login', async () => {
    seedSession()
    const { queryClient } = renderApp('/projects')
    expect(await screen.findByText('Website')).toBeInTheDocument()
    expect(queryClient.getQueryCache().getAll().length).toBeGreaterThan(0)
    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(sessionStorage.getItem('taskflow.session')).toBeNull()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)
  })

  it('ignores a stored session that has already expired', async () => {
    sessionStorage.setItem('taskflow.session', JSON.stringify({ token: 't', user: alice, expiresAt: Date.now() - 1000 }))
    renderApp('/projects')
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    await waitFor(() => expect(sessionStorage.getItem('taskflow.session')).toBeNull())
  })
})
