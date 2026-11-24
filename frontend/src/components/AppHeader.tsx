import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import Button from './Button'

export default function AppHeader() {
  const { user, logout } = useAuth()
  return (
    <header className="app-header">
      <Link to="/projects" className="brand">
        TaskFlow
      </Link>
      {user && (
        <div className="header-user">
          <span>{user.name}</span>
          <Button variant="secondary" onClick={logout}>
            Log out
          </Button>
        </div>
      )}
    </header>
  )
}
