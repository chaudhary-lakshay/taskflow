import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import { saveSession } from './auth/session'

export const alice = { id: 1, name: 'Alice', email: 'alice@example.test' }

/** A signed-in session as it would be in sessionStorage. */
export function seedSession(user = alice, token = 'token-' + user.id) {
  saveSession({ token, user, expiresAt: Date.now() + 3600_000 })
}

/** Whole app with an isolated QueryClient (no retries) and an in-memory router. */
export function renderApp(route = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } } })
  const utils = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { queryClient, ...utils }
}
