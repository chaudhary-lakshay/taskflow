import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { Task } from '../../api/types'
import TaskCard from './TaskCard'

const TODAY = '2026-11-24'
const task = (o: Partial<Task> = {}): Task => ({
  id: 1, title: 'Write docs', description: null, status: 'TODO', priority: 'HIGH', dueDate: '2026-11-20',
  assignee: { id: 2, name: 'Bob' }, projectId: 5, createdAt: '', updatedAt: '', overdue: false, ...o,
})

function show(t: Task, extra: Partial<React.ComponentProps<typeof TaskCard>> = {}) {
  const props = { onStatusChange: jest.fn(), onRetry: jest.fn(), onEdit: jest.fn(), onDelete: jest.fn() }
  const view = render(
    <ul>
      <TaskCard task={t} today={TODAY} {...props} {...extra} />
    </ul>,
  )
  return { ...props, ...view }
}

describe('TaskCard', () => {
  it('shows title, priority, due date and assignee', () => {
    show(task({ dueDate: '2026-12-01' }))
    expect(screen.getByRole('heading', { name: 'Write docs' })).toBeInTheDocument()
    expect(screen.getByText('High priority')).toBeInTheDocument()
    expect(screen.getByText('Due 2026-12-01')).toBeInTheDocument()
    expect(screen.getByText('Assigned to Bob')).toBeInTheDocument()
  })

  it('labels an open task due before today as Overdue (text, not colour only)', () => {
    const { container } = show(task())
    expect(screen.getByText('Overdue')).toBeInTheDocument()
    expect(container.querySelector('li')).toHaveClass('overdue')
  })

  it.each([
    ['due today', task({ dueDate: TODAY })],
    ['due in the future', task({ dueDate: '2026-12-01' })],
    ['no due date', task({ dueDate: null, assignee: null })],
    ['DONE although the date is past', task({ status: 'DONE' })],
  ])('is not overdue when %s', (_n, t) => {
    const { container } = show(t)
    expect(screen.queryByText('Overdue')).not.toBeInTheDocument()
    expect(container.querySelector('li')).not.toHaveClass('overdue')
  })

  it('shows Unassigned and No due date', () => {
    show(task({ dueDate: null, assignee: null }))
    expect(screen.getByText('Unassigned')).toBeInTheDocument()
    expect(screen.getByText('No due date')).toBeInTheDocument()
  })

  it('reports a status change from the dropdown', async () => {
    const { onStatusChange } = show(task())
    await userEvent.selectOptions(screen.getByLabelText('Status of Write docs'), 'DONE')
    expect(onStatusChange).toHaveBeenCalledWith('DONE')
  })

  it('shows an error with Retry, and Edit/Delete callbacks', async () => {
    const { onRetry, onEdit, onDelete } = show(task(), { error: 'boom' })
    expect(screen.getByRole('alert')).toHaveTextContent('Could not change the status: boom')
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await userEvent.click(screen.getByRole('button', { name: 'Edit Write docs' }))
    await userEvent.click(screen.getByRole('button', { name: 'Delete Write docs' }))
    expect(onRetry).toHaveBeenCalled()
    expect(onEdit).toHaveBeenCalled()
    expect(onDelete).toHaveBeenCalled()
  })
})
