import { BrowserRouter } from 'react-router-dom'
import { AppRouter } from './router/AppRouter'
import { AuthProvider } from '../features/auth/context/AuthContext'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider><AppRouter /></AuthProvider>
    </BrowserRouter>
  )
}
