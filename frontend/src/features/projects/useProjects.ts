import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createProject, listProjects } from '../../api/projects'
import { useAuth } from '../../auth/AuthContext'

/** Query keys start with the user id, so one user's cache can never be shown to another. */
export const projectsKey = (userId: number | undefined) => ['user', userId, 'projects'] as const

export function useProjects() {
  const { user } = useAuth()
  return useQuery({ queryKey: projectsKey(user?.id), queryFn: listProjects, enabled: !!user })
}

export function useCreateProject() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { name: string; description?: string }) => createProject(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: projectsKey(user?.id) }),
  })
}
