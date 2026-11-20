import { BrowserRouter } from 'react-router-dom'
import { AppRouter } from './router/AppRouter'
import { AuthProvider } from '../features/auth/context/AuthContext'
import { OfflineBanner } from '../shared/pwa/InstallApp'
import { Assistant } from '../features/assistant/Assistant'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider><AppRouter /><Assistant /></AuthProvider>
      <OfflineBanner />
    </BrowserRouter>
  )
}
