import { BrowserRouter } from 'react-router-dom'
import { AppRouter } from './router/AppRouter'
import { AuthProvider } from '../features/auth/context/AuthContext'
import { OfflineBanner } from '../shared/pwa/InstallApp'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider><AppRouter /></AuthProvider>
      <OfflineBanner />
    </BrowserRouter>
  )
}
