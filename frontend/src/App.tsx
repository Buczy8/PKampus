import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { FallbackRedirect } from '@/components/auth/FallbackRedirect'
import { ProtectedRoute } from '@/components/auth/ProtectedRoute'
import { AppShell } from '@/components/layout/AppShell'
import { AdminCheckinsPage } from '@/pages/admin/AdminCheckinsPage'
import { AdminDormRoomsPage } from '@/pages/admin/AdminDormRoomsPage'
import { AdminEventsPage } from '@/pages/admin/AdminEventsPage'
import { AdminLaundryPage } from '@/pages/admin/AdminLaundryPage'
import { AdminPortersPage } from '@/pages/admin/AdminPortersPage'
import { AdminResidentsPage } from '@/pages/admin/AdminResidentsPage'
import { AdminThematicRoomsPage } from '@/pages/admin/AdminThematicRoomsPage'
import { BoardPage } from '@/pages/BoardPage'
import { CardPage } from '@/pages/CardPage'
import { ChangePasswordPage } from '@/pages/ChangePasswordPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { EventsPage } from '@/pages/EventsPage'
import { ForgotPasswordPage } from '@/pages/ForgotPasswordPage'
import { IssuesPage } from '@/pages/IssuesPage'
import { LaundryPage } from '@/pages/LaundryPage'
import { LoginPage } from '@/pages/LoginPage'
import { PendingApprovalPage } from '@/pages/PendingApprovalPage'
import { ReceptionistBoardPage } from '@/pages/ReceptionistBoardPage'
import { ReceptionistDeskPage } from '@/pages/ReceptionistDeskPage'
import { ReceptionistEventsPage } from '@/pages/ReceptionistEventsPage'
import { ReceptionistIssuesPage } from '@/pages/ReceptionistIssuesPage'
import { ReceptionistLaundryPage } from '@/pages/ReceptionistLaundryPage'
import { ReceptionistRoomsPage } from '@/pages/ReceptionistRoomsPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { ResetPasswordPage } from '@/pages/ResetPasswordPage'
import { RoomsPage } from '@/pages/RoomsPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { SuperAdminAdminsPage } from '@/pages/superadmin/SuperAdminAdminsPage'
import { SuperAdminDormitoriesPage } from '@/pages/superadmin/SuperAdminDormitoriesPage'
import { SuperAdminEventsPage } from '@/pages/superadmin/SuperAdminEventsPage'
import { VerifyEmailPage } from '@/pages/VerifyEmailPage'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<FallbackRedirect />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/pending-approval" element={<PendingApprovalPage />} />

        <Route element={<ProtectedRoute allowMustChangePassword />}>
          <Route
            path="/change-password"
            element={<ChangePasswordPage forced />}
          />
        </Route>

        {/* Resident Authenticated Portal */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/card" element={<CardPage />} />
            <Route path="/laundry" element={<LaundryPage />} />
            <Route path="/rooms" element={<RoomsPage />} />
            <Route path="/issues" element={<IssuesPage />} />
            <Route path="/events" element={<EventsPage />} />
            <Route path="/board" element={<BoardPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>

        {/* Receptionist Desk */}
        <Route element={<ProtectedRoute receptionistOnly />}>
          <Route element={<AppShell />}>
            <Route path="/receptionist" element={<ReceptionistDeskPage />} />
            <Route path="/receptionist/laundry" element={<ReceptionistLaundryPage />} />
            <Route path="/receptionist/rooms" element={<ReceptionistRoomsPage />} />
            <Route path="/receptionist/issues" element={<ReceptionistIssuesPage />} />
            <Route path="/receptionist/events" element={<ReceptionistEventsPage />} />
            <Route path="/receptionist/board" element={<ReceptionistBoardPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>

        {/* Dormitory Admin Portal */}
        <Route element={<ProtectedRoute adminOnly />}>
          <Route element={<AppShell />}>
            <Route path="/admin" element={<Navigate to="/admin/residents" replace />} />
            <Route path="/admin/residents" element={<AdminResidentsPage />} />
            <Route path="/admin/checkins" element={<AdminCheckinsPage />} />
            <Route path="/admin/dorm-rooms" element={<AdminDormRoomsPage />} />
            <Route path="/admin/rooms" element={<AdminThematicRoomsPage />} />
            <Route path="/admin/laundry" element={<AdminLaundryPage />} />
            <Route path="/admin/events" element={<AdminEventsPage />} />
            <Route path="/admin/porters" element={<AdminPortersPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>

        {/* Super Admin (AOS) Portal */}
        <Route element={<ProtectedRoute superAdminOnly />}>
          <Route element={<AppShell />}>
            <Route path="/superadmin" element={<Navigate to="/superadmin/dormitories" replace />} />
            <Route path="/superadmin/dormitories" element={<SuperAdminDormitoriesPage />} />
            <Route path="/superadmin/admins" element={<SuperAdminAdminsPage />} />
            <Route path="/superadmin/events" element={<SuperAdminEventsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
        </Route>

        <Route path="*" element={<FallbackRedirect />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
