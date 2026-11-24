import type { User } from '../api/types'

export interface Session {
  token: string
  user: User
  /** Epoch milliseconds when the token stops being valid. */
  expiresAt: number
}

// D09: sessionStorage survives a reload in the same tab but not a new tab or browser.
// Trade-off: any script running on the page (XSS) could read it. Cleared on logout and on any 401.
const KEY = 'taskflow.session'

// The token the API layer sends. Kept next to the storage so both always change together.
let currentToken: string | null = null
export const getToken = () => currentToken

export function loadSession(now = Date.now()): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as Session
    if (typeof s.token !== 'string' || typeof s.expiresAt !== 'number' || typeof s.user?.id !== 'number') return null
    if (s.expiresAt <= now) {
      sessionStorage.removeItem(KEY)
      return null
    }
    currentToken = s.token
    return s
  } catch {
    return null
  }
}

export function saveSession(s: Session) {
  currentToken = s.token
  sessionStorage.setItem(KEY, JSON.stringify(s))
}

export function clearStoredSession() {
  currentToken = null
  sessionStorage.removeItem(KEY)
}
