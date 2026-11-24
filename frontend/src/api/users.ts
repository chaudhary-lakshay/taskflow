import { request } from './http'
import type { UserOption } from './types'

/** Extra backend endpoint (D02): name search, at least 2 characters, at most 20 results. */
export const searchUsers = (query: string) => request<UserOption[]>('GET', '/users?query=' + encodeURIComponent(query))
