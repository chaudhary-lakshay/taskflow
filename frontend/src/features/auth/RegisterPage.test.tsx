import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import * as authApi from '../../api/auth'
import { ApiError } from '../../api/http'
import * as projectsApi from '../../api/projects'
import { alice, renderApp } from '../../test-utils'

jest.mock('../../api/auth')
jest.mock('../../api/projects')
const auth = jest.mocked(authApi)
const projects = jest.mocked(projectsApi)

beforeEach(() => {
  jest.resetAllMocks()
  projects.listProjects.mockResolvedValue([])
})

async function fill(name = 'Alice', email = 'alice@example.test', password = 'correct-horse') {
  if (name) await userEvent.type(screen.getByLabelText('Name'), name)
  if (email) await userEvent.type(screen.getByLabelText('Email'), email)
  if (password) await userEvent.type(screen.getByLabelText('Password'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
}

describe('Register flow', () => {
  it('validates name, email and password like the backend (including 72 bytes)', async () => {
    renderApp('/register')
    await fill('', 'nope', 'short')
    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('Email is not valid')).toBeInTheDocument()
    expect(screen.getByText('Password must be at least 8 characters')).toBeInTheDocument()
    expect(auth.registerUser).not.toHaveBeenCalled()
  })

  it('rejects a password over 72 bytes even when it has fewer than 72 characters', async () => {
    renderApp('/register')
    await fill('Alice', 'alice@example.test', 'é'.repeat(37))
    expect(await screen.findByText('Password must be at most 72 bytes')).toBeInTheDocument()
    expect(auth.registerUser).not.toHaveBeenCalled()
  })

  it('registers, then logs in with the same credentials and opens /projects', async () => {
    auth.registerUser.mockResolvedValue(alice)
    auth.loginUser.mockResolvedValue({ accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 3600, user: alice })
    renderApp('/register')
    await fill()
    expect(await screen.findByRole('heading', { name: 'Projects' })).toBeInTheDocument()
    expect(auth.registerUser).toHaveBeenCalledWith({ name: 'Alice', email: 'alice@example.test', password: 'correct-horse' })
    expect(auth.loginUser).toHaveBeenCalledWith({ email: 'alice@example.test', password: 'correct-horse' })
  })

  it('if the automatic login fails it goes to /login and never registers again', async () => {
    auth.registerUser.mockResolvedValue(alice)
    auth.loginUser.mockRejectedValue(new ApiError(0, 'NETWORK_ERROR', 'offline'))
    renderApp('/register')
    await fill()
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your account was created')
    expect(auth.registerUser).toHaveBeenCalledTimes(1)
  })

  it('shows a duplicate-email error from the server on the email field', async () => {
    auth.registerUser.mockRejectedValue(new ApiError(409, 'EMAIL_TAKEN', 'Email is already registered', { email: 'Email is already registered' }))
    renderApp('/register')
    await fill()
    expect(await screen.findByText('Email is already registered')).toBeInTheDocument()
    expect(auth.loginUser).not.toHaveBeenCalled()
  })
})
