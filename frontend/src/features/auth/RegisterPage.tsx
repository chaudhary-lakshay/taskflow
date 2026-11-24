import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { useAuth } from '../../auth/AuthContext'
import { validateRegister, type FieldErrors } from '../../auth/validation'
import Button from '../../components/Button'
import FormField from '../../components/FormField'

export default function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to="/projects" replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setFormError(undefined)
    const found = validateRegister({ name, email, password })
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setBusy(true)
    try {
      const signedIn = await register(name.trim(), email.trim(), password)
      // If the automatic login failed the account still exists: go to the login page, never register again.
      if (signedIn) navigate('/projects', { replace: true })
      else navigate('/login', { replace: true, state: { registered: true } })
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) setErrors(err.fieldErrors)
      else setFormError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <h1>Create account</h1>
      <form onSubmit={onSubmit} noValidate>
        <FormField label="Name" autoComplete="name" value={name} error={errors.name} onChange={(e) => setName(e.target.value)} />
        <FormField label="Email" type="email" autoComplete="email" value={email} error={errors.email} onChange={(e) => setEmail(e.target.value)} />
        <FormField label="Password" type="password" autoComplete="new-password" value={password} error={errors.password} onChange={(e) => setPassword(e.target.value)} />
        {formError && <p className="form-error" role="alert">{formError}</p>}
        <Button type="submit" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</Button>
      </form>
      <p>
        Already registered? <Link to="/login">Log in</Link>
      </p>
    </main>
  )
}
