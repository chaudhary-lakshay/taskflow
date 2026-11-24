import { byteLength, validateLogin, validateProject, validateRegister, validateTask } from './validation'

describe('validation matches the backend rules', () => {
  it('register: name max 100, valid email max 254, password 8 characters and 72 bytes', () => {
    expect(validateRegister({ name: 'Ann', email: 'ann@example.test', password: 'long-enough' })).toEqual({})
    expect(validateRegister({ name: ' ', email: 'x', password: 'short' })).toEqual({
      name: 'Name is required',
      email: 'Email is not valid',
      password: 'Password must be at least 8 characters',
    })
    expect(validateRegister({ name: 'n'.repeat(101), email: 'a@b.co', password: 'password1' }).name).toMatch(/100/)
    expect(validateRegister({ name: 'n'.repeat(100), email: 'a@b.co', password: 'password1' })).toEqual({})
    expect(validateRegister({ name: 'A', email: 'a'.repeat(250) + '@b.co', password: 'password1' }).email).toMatch(/254/)
  })

  it('password is limited by UTF-8 bytes, not characters', () => {
    expect(byteLength('é')).toBe(2)
    expect(validateRegister({ name: 'A', email: 'a@b.co', password: 'x'.repeat(72) }).password).toBeUndefined()
    expect(validateRegister({ name: 'A', email: 'a@b.co', password: 'x'.repeat(73) }).password).toMatch(/72 bytes/)
    expect(validateRegister({ name: 'A', email: 'a@b.co', password: 'é'.repeat(36) }).password).toBeUndefined() // 72 bytes
    expect(validateRegister({ name: 'A', email: 'a@b.co', password: 'é'.repeat(37) }).password).toMatch(/72 bytes/)
  })

  it('login only needs an email and a password (no minimum, so old passwords still work)', () => {
    expect(validateLogin({ email: 'a@b.co', password: 'x' })).toEqual({})
    expect(validateLogin({ email: '', password: '' })).toEqual({ email: 'Email is required', password: 'Password is required' })
  })

  it('project: name required max 120, description max 2000', () => {
    expect(validateProject({ name: 'P', description: '' })).toEqual({})
    expect(validateProject({ name: '  ', description: '' }).name).toBeDefined()
    expect(validateProject({ name: 'x'.repeat(121), description: '' }).name).toMatch(/120/)
    expect(validateProject({ name: 'x', description: 'd'.repeat(2001) }).description).toMatch(/2000/)
  })
})

describe('validateTask', () => {
  const base = { title: 'T', description: '', dueDate: '', originalDueDate: null, editing: false, today: '2026-11-24' }
  it('accepts a minimal task', () => expect(validateTask(base)).toEqual({}))
  it('requires a title and caps it at 120', () => {
    expect(validateTask({ ...base, title: '  ' }).title).toBe('Title is required')
    expect(validateTask({ ...base, title: 'x'.repeat(120) }).title).toBeUndefined()
    expect(validateTask({ ...base, title: 'x'.repeat(121) }).title).toBeDefined()
  })
  it('rejects a past date on create, accepts today', () => {
    expect(validateTask({ ...base, dueDate: '2026-11-23' }).dueDate).toBeDefined()
    expect(validateTask({ ...base, dueDate: '2026-11-24' }).dueDate).toBeUndefined()
  })
  it('on edit keeps an unchanged past date but rejects a changed one', () => {
    const e = { ...base, editing: true, originalDueDate: '2026-01-01' }
    expect(validateTask({ ...e, dueDate: '2026-01-01' })).toEqual({})
    expect(validateTask({ ...e, dueDate: '2026-01-02' }).dueDate).toBeDefined()
  })
})
