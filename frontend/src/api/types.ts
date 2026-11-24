export interface User {
  id: number
  name: string
  email: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface Project {
  id: number
  name: string
  description: string | null
  ownerId: number
  createdAt: string
}
