import { request } from './http'
import type { Project } from './types'

export const listProjects = () => request<Project[]>('GET', '/projects')

export const createProject = (body: { name: string; description?: string }) => request<Project>('POST', '/projects', body)
