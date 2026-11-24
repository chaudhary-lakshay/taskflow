import { Link, useParams } from 'react-router'
import AppHeader from '../../components/AppHeader'
import Button from '../../components/Button'
import { useProjects } from './useProjects'

/** Placeholder until the board (T05). The backend has no single-project endpoint, so the name comes from the list. */
export default function ProjectPage() {
  const { id } = useParams()
  const projects = useProjects()
  const project = projects.data?.find((p) => String(p.id) === id)

  return (
    <>
      <AppHeader />
      <main className="page">
        <p>
          <Link to="/projects">← Back to projects</Link>
        </p>
        {projects.isPending && <p role="status">Loading project…</p>}
        {projects.isError && (
          <div role="alert" className="state-box">
            <p>Could not load the project: {projects.error.message}</p>
            <Button onClick={() => projects.refetch()}>Retry</Button>
          </div>
        )}
        {projects.isSuccess && !project && (
          <div className="state-box">
            <p>Project not found.</p>
          </div>
        )}
        {project && (
          <>
            <h1>{project.name}</h1>
            {project.description && <p>{project.description}</p>}
            <p className="muted">The task board comes next.</p>
          </>
        )}
      </main>
    </>
  )
}
