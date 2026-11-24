// The only place that calls fetch. Components never do.

/** The backend's error shape, also used for network failures (status 0). */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

interface HttpConfig {
  getToken: () => string | null
  /** Called when a request that carried a token comes back 401 (expired or invalid token). */
  onUnauthorized: () => void
}

let config: HttpConfig = { getToken: () => null, onUnauthorized: () => {} }

export function configureHttp(next: HttpConfig) {
  config = next
}

// Relative path: in development Vite proxies /api to the backend, in production the same origin serves both.
const BASE = '/api'

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = config.getToken()
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = 'Bearer ' + token

  let response: Response
  try {
    response = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Cannot reach the server. Check your connection and try again.')
  }

  if (response.ok) {
    return (response.status === 204 ? undefined : await response.json()) as T
  }

  const error = await toApiError(response)
  // A 401 on a request that sent a token means that session is no longer valid.
  // - A 401 without a token (wrong password at login) is just an error for the form.
  // - A late 401 for an old token (the user logged out and signed in again while the request was
  //   in flight) must not end the newer session: only act if the token is still the current one.
  if (response.status === 401 && token && config.getToken() === token) config.onUnauthorized()
  throw error
}

async function toApiError(response: Response): Promise<ApiError> {
  try {
    const data = (await response.json()) as Partial<{ code: string; message: string; fieldErrors: Record<string, string> }>
    return new ApiError(response.status, data.code ?? 'ERROR', data.message ?? 'Request failed', data.fieldErrors ?? {})
  } catch {
    return new ApiError(response.status, 'ERROR', 'Request failed (' + response.status + ')')
  }
}
