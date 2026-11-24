import { screen, waitFor } from '@testing-library/react'
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

describe('Login form', () => {
  it('shows validation errors and does not call the API', async () => {
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(auth.loginUser).not.toHaveBeenCalled()
  })

  it('submits trimmed credentials, stores the session in sessionStorage and opens the projects page', async () => {
    auth.loginUser.mockResolvedValue({ accessToken: 'jwt-1', tokenType: 'Bearer', expiresIn: 3600, user: alice })
    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email'), '  alice@example.test ')
    await userEvent.type(screen.getByLabelText('Password'), 'correct-horse')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(auth.loginUser).toHaveBeenCalledWith({ email: 'alice@example.test', password: 'correct-horse' })
    expect(await screen.findByRole('heading', { name: 'Projects' })).toBeInTheDocument()
    expect(JSON.parse(sessionStorage.getItem('taskflow.session')!).token).toBe('jwt-1')
    expect(localStorage.length).toBe(0)
  })

  it('shows one generic message for invalid credentials', async () => {
    auth.loginUser.mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Invalid email or password'))
    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email'), 'alice@example.test')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong-password')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(sessionStorage.getItem('taskflow.session')).toBeNull()
  })

  it('shows server field errors next to the fields', async () => {
    auth.loginUser.mockRejectedValue(new ApiError(400, 'VALIDATION_ERROR', 'Invalid request', { email: 'Email is not valid' }))
    renderApp('/login')
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co')
    await userEvent.type(screen.getByLabelText('Password'), 'whatever1')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    await waitFor(() => expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Email is not valid'))
  })
})
