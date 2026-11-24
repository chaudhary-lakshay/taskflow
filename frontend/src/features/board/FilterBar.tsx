import SelectField from '../../components/SelectField'
import type { TaskPriority } from '../../api/types'
import AssigneePicker from './AssigneePicker'
import type { BoardFilters } from './filters'
import { PRIORITY_LABEL } from './TaskCard'

const priorityOptions = [{ value: '', label: 'All priorities' }, ...(Object.keys(PRIORITY_LABEL) as TaskPriority[]).map((p) => ({ value: p, label: PRIORITY_LABEL[p] }))]
const sortOptions = [
  { value: 'asc', label: 'Earliest first' },
  { value: 'desc', label: 'Latest first' },
]

export default function FilterBar({ filters, onChange }: { filters: BoardFilters; onChange: (f: BoardFilters) => void }) {
  return (
    <section className="filters" aria-label="Board filters">
      <SelectField
        label="Priority filter"
        options={priorityOptions}
        value={filters.priority ?? ''}
        onChange={(e) => onChange({ ...filters, priority: (e.target.value || undefined) as TaskPriority | undefined })}
      />
      <AssigneePicker label="Assignee filter" value={filters.assignee ?? null} onChange={(a) => onChange({ ...filters, assignee: a ?? undefined })} />
      <SelectField
        label="Sort by due date"
        options={sortOptions}
        value={filters.direction}
        onChange={(e) => onChange({ ...filters, direction: e.target.value as 'asc' | 'desc' })}
      />
    </section>
  )
}
