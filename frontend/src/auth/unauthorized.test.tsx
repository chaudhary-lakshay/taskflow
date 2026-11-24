import { screen } from '@testing-library/react'
import { renderApp, seedSession } from '../test-utils'

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

  it('a 500 is shown on the page with a retry button and keeps the session', async () => {
    seedSession()
    fetchMock.mockReturnValue(reply(500, { status: 500, code: 'INTERNAL_ERROR', message: 'Something went wrong' }))
    renderApp('/projects')
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(sessionStorage.getItem('taskflow.session')).not.toBeNull()
  })
})
