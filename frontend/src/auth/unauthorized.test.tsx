import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { alice, renderApp, seedSession } from '../test-utils'

// No API mocks here: the real fetch wrapper runs against a fake fetch, so the 401 path is end to end.
const fetchMock = jest.fn()
beforeEach(() => {
  fetchMock.mockReset()
  globalThis.fetch = fetchMock as unknown as typeof fetch
})

const reply = (status: number, body: unknown) => Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(body) })

describe('a 401 from the server', () => {
  it('clears the session and the cache, redirects to /login once and does not loop', async () => {
    seedSession()
    fetchMock.mockReturnValue(reply(401, { status: 401, code: 'UNAUTHORIZED', message: 'Authentication is required or the token is invalid' }))
    const { queryClient } = renderApp('/projects')

    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your session expired')
    expect(sessionStorage.getItem('taskflow.session')).toBeNull()
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0)

    // the login page sends nothing, so nothing can loop
    await new Promise((r) => setTimeout(r, 50))
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer token-1')
  })

  it('a delayed 401 from an old token does not clear a newer session (logout, then login as B)', async () => {
    seedSession() // session A: token-1
    const bob = { id: 2, name: 'Bob', email: 'bob@example.test' }
    let lateAnswer!: (r: unknown) => void
    fetchMock.mockImplementation((url: string, init: { headers: Record<string, string> }) => {
      if (url === '/api/auth/login') return reply(200, { accessToken: 'jwt-B', tokenType: 'Bearer', expiresIn: 3600, user: bob })
      if (init.headers.Authorization === 'Bearer token-1') return new Promise((r) => (lateAnswer = r)) // A's request hangs
      if (init.headers.Authorization === 'Bearer jwt-B') {
        return reply(200, [{ id: 9, name: "Bob's project", description: null, ownerId: 2, createdAt: '2026-11-24T00:00:00Z' }])
      }
      return reply(500, { status: 500, code: 'ERROR', message: 'unexpected ' + init.headers.Authorization })
    })
    const { queryClient } = renderApp('/projects')
    await screen.findByText('Loading projects…') // A's request is now in flight

    await userEvent.click(screen.getByRole('button', { name: 'Log out' }))
    await userEvent.type(await screen.findByLabelText('Email'), 'bob@example.test')
    await userEvent.type(screen.getByLabelText('Password'), 'bobs-password')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    expect(await screen.findByText("Bob's project")).toBeInTheDocument()

    // now A's old request finally comes back 401
    lateAnswer(await reply(401, { status: 401, code: 'UNAUTHORIZED', message: 'Authentication is required or the token is invalid' }))
    await new Promise((r) => setTimeout(r, 50))

    expect(screen.getByRole('heading', { name: 'Projects' })).toBeInTheDocument() // no redirect
    expect(screen.getByText("Bob's project")).toBeInTheDocument()
    expect(JSON.parse(sessionStorage.getItem('taskflow.session')!).token).toBe('jwt-B')
    expect(queryClient.getQueryData(['user', 2, 'projects'])).toHaveLength(1) // B's cache survives
    expect(alice.id).toBe(1)
  })

  it('a 500 is shown on the page with a retry button and keeps the session', async () => {
    seedSession()
    fetchMock.mockReturnValue(reply(500, { status: 500, code: 'INTERNAL_ERROR', message: 'Something went wrong' }))
    renderApp('/projects')
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(sessionStorage.getItem('taskflow.session')).not.toBeNull()
  })
})
