import { useId, type InputHTMLAttributes } from 'react'

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  multiline?: boolean
}

/** A labelled input (or textarea) with its error announced and linked through aria-describedby. */
export default function FormField({ label, error, multiline, ...rest }: Props) {
  const id = useId()
  const errorId = id + '-error'
  const shared = {
    id,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? errorId : undefined,
  }
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {multiline ? (
        <textarea rows={3} {...shared} {...(rest as object)} />
      ) : (
        <input {...shared} {...rest} />
      )}
      {error && (
        <p id={errorId} className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
