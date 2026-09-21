import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { FallbackRedirect } from '@/components/auth/FallbackRedirect'
import { ProtectedRoute } from '@/components/auth/ProtectedRoute'
import { ResidentShell } from '@/components/layout/ResidentShell'
import { AdminPage } from '@/pages/AdminPage'
import { BoardPage } from '@/pages/BoardPage'
import { CardPage } from '@/pages/CardPage'
import { ChangePasswordPage } from '@/pages/ChangePasswordPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { IssuesPage } from '@/pages/IssuesPage'
import { LaundryPage } from '@/pages/LaundryPage'
import { LoginPage } from '@/pages/LoginPage'
import { PendingApprovalPage } from '@/pages/PendingApprovalPage'
import { ReceptionistDeskPage } from '@/pages/ReceptionistDeskPage'
import { ReceptionistLaundryPage } from '@/pages/ReceptionistLaundryPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { RoomsPage } from '@/pages/RoomsPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { SuperAdminPage } from '@/pages/SuperAdminPage'
import { VerifyEmailPage } from '@/pages/VerifyEmailPage'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<FallbackRedirect />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/pending-approval" element={<PendingApprovalPage />} />

        <Route element={<ProtectedRoute allowMustChangePassword />}>
          <Route
            path="/change-password"
            element={<ChangePasswordPage forced />}
          />
        </Route>

        {/* Resident Authenticated Portal */}
        <Route element={<ProtectedRoute />}>
          <Route element={<ResidentShell />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/card" element={<CardPage />} />
            <Route path="/laundry" element={<LaundryPage />} />
            <Route path="/rooms" element={<RoomsPage />} />
            <Route path="/issues" element={<IssuesPage />} />
            <Route path="/board" element={<BoardPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>

        {/* Receptionist Desk */}
        <Route element={<ProtectedRoute receptionistOnly />}>
          <Route element={<ResidentShell />}>
            <Route path="/receptionist" element={<ReceptionistDeskPage />} />
            <Route path="/receptionist/laundry" element={<ReceptionistLaundryPage />} />
          </Route>
        </Route>

        {/* Dormitory Admin Portal */}
        <Route element={<ProtectedRoute adminOnly />}>
          <Route path="/admin" element={<AdminPage />} />
        </Route>

        {/* Super Admin (AOS) Portal */}
        <Route element={<ProtectedRoute superAdminOnly />}>
          <Route path="/superadmin" element={<SuperAdminPage />} />
        </Route>

        <Route path="*" element={<FallbackRedirect />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
