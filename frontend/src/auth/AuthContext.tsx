import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { loginUser, registerUser } from '../api/auth'
import { configureHttp } from '../api/http'
import type { User } from '../api/types'
import { clearStoredSession, getToken, loadSession, saveSession, type Session } from './session'

interface AuthValue {
  user: User | null
  /** True after a 401 ended the session, until the next successful login. */
  expired: boolean
  login: (email: string, password: string) => Promise<void>
  /** Creates the account, then signs in with the same credentials. Rejects only if registration fails;
   *  resolves to false when the account exists but the automatic login failed. */
  register: (name: string, email: string, password: string) => Promise<boolean>
  logout: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [session, setSession] = useState<Session | null>(() => loadSession())
  const [expired, setExpired] = useState(false)

  const end = useCallback(
    (wasExpired: boolean) => {
      clearStoredSession()
      setSession(null)
      setExpired(wasExpired)
      queryClient.clear() // no cached data of the previous user survives
    },
    [queryClient],
  )

  // Configured during render, not in an effect: child effects (the first queries) run before a
  // parent's effects, so an effect here would let the first request go out without the token.
  configureHttp({ getToken, onUnauthorized: () => end(true) })

  const start = useCallback(
    (token: string, user: User, expiresIn: number) => {
      const next: Session = { token, user, expiresAt: Date.now() + expiresIn * 1000 }
      queryClient.clear() // a new session never starts with another user's cache
      saveSession(next)
      setSession(next)
      setExpired(false)
    },
    [queryClient],
  )

  const login = useCallback(
    async (email: string, password: string) => {
      const r = await loginUser({ email, password })
      start(r.accessToken, r.user, r.expiresIn)
    },
    [start],
  )

  const register = useCallback(
    async (name: string, email: string, password: string) => {
      await registerUser({ name, email, password })
      try {
        await login(email, password)
        return true
      } catch {
        return false
      }
    },
    [login],
  )

  const value = useMemo<AuthValue>(
    () => ({ user: session?.user ?? null, expired, login, register, logout: () => end(false) }),
    [session, expired, login, register, end],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const v = useContext(AuthContext)
  if (!v) throw new Error('useAuth must be used inside AuthProvider')
  return v
}
