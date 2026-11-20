import { BrowserRouter } from 'react-router-dom'
import { AppRouter } from './router/AppRouter'
import { AuthProvider } from '../features/auth/context/AuthContext'
import { OfflineBanner } from '../shared/pwa/InstallApp'
import { MonitorWidget } from '../features/admin/components/MonitorWidget'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider><AppRouter /><MonitorWidget /></AuthProvider>
      <OfflineBanner />
    </BrowserRouter>
  )
}
