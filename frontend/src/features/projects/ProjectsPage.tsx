import { useState } from 'react'
import { Link } from 'react-router'
import AppHeader from '../../components/AppHeader'
import Button from '../../components/Button'
import CreateProjectModal from './CreateProjectModal'
import { useProjects } from './useProjects'

export default function ProjectsPage() {
  const projects = useProjects()
  const [creating, setCreating] = useState(false)

  return (
    <>
      <AppHeader />
      <main className="page">
        <div className="page-title">
          <h1>Projects</h1>
          <Button onClick={() => setCreating(true)}>New project</Button>
        </div>

        {projects.isPending && <p role="status">Loading projects…</p>}

        {projects.isError && (
          <div role="alert" className="state-box">
            <p>Could not load your projects: {projects.error.message}</p>
            <Button onClick={() => projects.refetch()}>Retry</Button>
          </div>
        )}

        {projects.isSuccess && projects.data.length === 0 && (
          <div className="state-box">
            <p>You have no projects yet. Create your first one to get started.</p>
          </div>
        )}

        {projects.isSuccess && projects.data.length > 0 && (
          <ul className="project-list">
            {projects.data.map((p) => (
              <li key={p.id}>
                <Link to={'/projects/' + p.id}>
                  <strong>{p.name}</strong>
                  {p.description && <span>{p.description}</span>}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
      {creating && <CreateProjectModal onClose={() => setCreating(false)} />}
    </>
  )
}
