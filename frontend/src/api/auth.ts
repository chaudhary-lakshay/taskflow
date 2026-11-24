import { request } from './http'
import type { AuthResponse, User } from './types'

export const registerUser = (body: { name: string; email: string; password: string }) =>
  request<User>('POST', '/auth/register', body)

export const loginUser = (body: { email: string; password: string }) => request<AuthResponse>('POST', '/auth/login', body)
