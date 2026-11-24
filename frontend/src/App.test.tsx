import { render, screen } from '@testing-library/react'
import App from './App'

test('renders the TaskFlow heading', () => {
  render(<App />)
  expect(screen.getByRole('heading', { name: 'TaskFlow' })).toBeInTheDocument()
})
