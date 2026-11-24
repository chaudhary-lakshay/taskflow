import type { ButtonHTMLAttributes } from 'react'

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary'
}

export default function Button({ variant = 'primary', type = 'button', className, ...rest }: Props) {
  return <button type={type} className={'btn btn-' + variant + (className ? ' ' + className : '')} {...rest} />
}
