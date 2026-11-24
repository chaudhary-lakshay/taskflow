import { useQuery } from '@tanstack/react-query'
import { useEffect, useId, useState } from 'react'
import { searchUsers } from '../../api/users'
import { useAuth } from '../../auth/AuthContext'
import Button from '../../components/Button'

export interface Assignee {
  id: number
  name: string
}

function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return v
}

interface Props {
  label: string
  value: Assignee | null
  onChange: (a: Assignee | null) => void
  error?: string
}

/** Pick a user by name through the lookup endpoint (min 2 characters); the choice can be cleared. */
export default function AssigneePicker({ label, value, onChange, error }: Props) {
  const { user } = useAuth()
  const id = useId()
  const [text, setText] = useState('')
  const term = useDebounced(text.trim(), 250)
  const enabled = !!user && term.length >= 2 && !value
  const results = useQuery({ queryKey: ['user', user?.id, 'users', term], queryFn: () => searchUsers(term), enabled })

  return (
    <div className="field" role="group" aria-labelledby={id + '-label'}>
      <span id={id + '-label'} className="label">
        {label}
      </span>
      {value ? (
        <div className="picked">
          <span>{value.name}</span>
          <Button variant="secondary" onClick={() => onChange(null)} aria-label={'Clear ' + label}>
            Clear
          </Button>
        </div>
      ) : (
        <>
          <input
            type="search"
            aria-label={label + ' search'}
            placeholder="Type at least 2 letters"
            value={text}
            onChange={(e) => setText(e.target.value)}
            aria-invalid={error ? true : undefined}
          />
          {enabled && results.isPending && <p role="status">Searching…</p>}
          {enabled && results.isError && <p role="alert" className="field-error">Search failed: {results.error.message}</p>}
          {enabled && results.isSuccess && results.data.length === 0 && <p className="muted">No users found</p>}
          {enabled && results.isSuccess && results.data.length > 0 && (
            <ul className="picker-results" aria-label={label + ' results'}>
              {results.data.map((u) => (
                <li key={u.id}>
                  <button
                    type="button"
                    onClick={() => {
                      onChange(u)
                      setText('')
                    }}
                  >
                    {u.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
      {error && <p className="field-error" role="alert">{error}</p>}
    </div>
  )
}
