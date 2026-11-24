// Mirrors the backend rules (docs/api-contract.md). The server stays the authority.
export type FieldErrors = Record<string, string>

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export const byteLength = (s: string) => new TextEncoder().encode(s).length

export function validateEmail(email: string): string | undefined {
  const e = email.trim()
  if (!e) return 'Email is required'
  if (e.length > 254) return 'Email must be at most 254 characters'
  if (!EMAIL.test(e)) return 'Email is not valid'
}

export function validatePassword(password: string): string | undefined {
  if (!password.trim()) return 'Password is required'
  if ([...password].length < 8) return 'Password must be at least 8 characters'
  if (byteLength(password) > 72) return 'Password must be at most 72 bytes'
}

export function validateLogin(v: { email: string; password: string }): FieldErrors {
  const errors: FieldErrors = {}
  const email = validateEmail(v.email)
  if (email) errors.email = email
  if (!v.password) errors.password = 'Password is required'
  else if (byteLength(v.password) > 72) errors.password = 'Password must be at most 72 bytes'
  return errors
}

export function validateRegister(v: { name: string; email: string; password: string }): FieldErrors {
  const errors: FieldErrors = {}
  if (!v.name.trim()) errors.name = 'Name is required'
  else if (v.name.trim().length > 100) errors.name = 'Name must be at most 100 characters'
  const email = validateEmail(v.email)
  if (email) errors.email = email
  const password = validatePassword(v.password)
  if (password) errors.password = password
  return errors
}

export function validateProject(v: { name: string; description: string }): FieldErrors {
  const errors: FieldErrors = {}
  if (!v.name.trim()) errors.name = 'Name is required'
  else if (v.name.trim().length > 120) errors.name = 'Name must be at most 120 characters'
  if (v.description.length > 2000) errors.description = 'Description must be at most 2000 characters'
  return errors
}
