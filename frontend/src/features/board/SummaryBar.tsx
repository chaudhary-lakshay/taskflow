import Button from '../../components/Button'
import { useSummary } from './useBoard'

/** Counts of the whole project: they ignore the board filters. */
export default function SummaryBar({ projectId }: { projectId: number }) {
  const summary = useSummary(projectId)
  if (summary.isPending) return <p role="status">Loading summary…</p>
  if (summary.isError) {
    return (
      <div role="alert" className="state-box">
        <p>Could not load the summary: {summary.error.message}</p>
        <Button onClick={() => summary.refetch()}>Retry</Button>
      </div>
    )
  }
  const s = summary.data
  const items: [string, number][] = [
    ['To Do', s.todo],
    ['In Progress', s.inProgress],
    ['Done', s.done],
    ['Overdue', s.overdue],
    ['Total', s.total],
  ]
  return (
    <dl className="summary" aria-label="Project summary">
      {items.map(([label, n]) => (
        <div key={label} className={label === 'Overdue' && n > 0 ? 'stat stat-overdue' : 'stat'}>
          <dt>{label}</dt>
          <dd>{n}</dd>
        </div>
      ))}
    </dl>
  )
}
