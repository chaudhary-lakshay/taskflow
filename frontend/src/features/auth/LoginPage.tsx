import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { validateLogin, type FieldErrors } from '../../auth/validation'
import Button from '../../components/Button'
import FormField from '../../components/FormField'

interface LocationState {
  from?: string
  registered?: boolean
}

export default function LoginPage() {
  const { user, login, expired } = useAuth()
  const navigate = useNavigate()
  const state = (useLocation().state ?? {}) as LocationState
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [busy, setBusy] = useState(false)

  const destination = state.from && state.from.startsWith('/') ? state.from : '/projects'
  if (user) return <Navigate to={destination} replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError(undefined)
    const found = validateLogin({ email, password })
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setBusy(true)
    try {
      await login(email.trim(), password)
      navigate(destination, { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) setErrors(err.fieldErrors)
      else if (err instanceof ApiError && err.status === 401) setFormError('Invalid email or password')
      else setFormError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <h1>Log in</h1>
      {state.registered && <p role="status" className="notice">Your account was created. Please log in.</p>}
      {expired && <p role="status" className="notice">Your session expired. Please log in again.</p>}
      <form onSubmit={onSubmit} noValidate>
        <FormField label="Email" type="email" autoComplete="email" value={email} error={errors.email} onChange={(e) => setEmail(e.target.value)} />
        <FormField label="Password" type="password" autoComplete="current-password" value={password} error={errors.password} onChange={(e) => setPassword(e.target.value)} />
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <Button type="submit" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</Button>
      </form>
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </main>
  )
}
