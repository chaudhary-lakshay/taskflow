import { useState } from 'react'
import { Link, useParams } from 'react-router'
import type { Task, TaskStatus } from '../../api/types'
import AppHeader from '../../components/AppHeader'
import Button from '../../components/Button'
import BoardColumn from '../board/BoardColumn'
import { utcToday } from '../board/dates'
import DeleteTaskModal from '../board/DeleteTaskModal'
import FilterBar from '../board/FilterBar'
import { useBoardFilters } from '../board/filters'
import SummaryBar from '../board/SummaryBar'
import TaskFormModal from '../board/TaskFormModal'
import { useProjects } from './useProjects'

const COLUMNS: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE']

/** The project board: summary, filters and three columns. The backend has no single-project endpoint, so the name comes from the list. */
export default function ProjectPage() {
  const { id } = useParams()
  const projects = useProjects()
  const project = projects.data?.find((p) => String(p.id) === id)
  const [filters, setFilters] = useBoardFilters()
  const [editing, setEditing] = useState<Task | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Task | null>(null)
  const today = utcToday()

  return (
    <>
      <AppHeader />
      <main className="page page-wide">
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
            <div className="page-title">
              <div>
                <h1>{project.name}</h1>
                {project.description && <p className="muted">{project.description}</p>}
              </div>
              <Button onClick={() => setEditing('new')}>New task</Button>
            </div>
            <SummaryBar projectId={project.id} />
            <FilterBar filters={filters} onChange={setFilters} />
            <div className="board">
              {COLUMNS.map((status) => (
                // the key restarts each column's page counter whenever a filter changes
                <BoardColumn
                  key={status + JSON.stringify([filters.priority, filters.assignee?.id, filters.direction])}
                  projectId={project.id}
                  status={status}
                  filters={filters}
                  today={today}
                  onEdit={setEditing}
                  onDelete={setDeleting}
                />
              ))}
            </div>
            {editing && <TaskFormModal projectId={project.id} task={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
            {deleting && <DeleteTaskModal projectId={project.id} task={deleting} onClose={() => setDeleting(null)} />}
          </>
        )}
      </main>
    </>
  )
}
