import { useEffect } from "react"
import { Navigate, Route, Routes } from "react-router-dom"

import { HubPage } from "@/routes/HubPage"
import { JoinPage } from "@/routes/auth/JoinPage"
import { LoginPage } from "@/routes/auth/LoginPage"
import { RegisterPage } from "@/routes/auth/RegisterPage"
import {
  ProtectedRoute,
  PublicOnlyRoute,
  RedirectIfCoupled,
  RequireCouple,
} from "@/routes/guards"
import { useAuthStore } from "@/stores/useAuthStore"

export function App() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const loadCurrentUser = useAuthStore((state) => state.loadCurrentUser)

  useEffect(() => {
    if (isAuthenticated) {
      void loadCurrentUser()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <Routes>
      <Route
        path="/login"
        element={
          <PublicOnlyRoute>
            <LoginPage />
          </PublicOnlyRoute>
        }
      />
      <Route
        path="/register"
        element={
          <PublicOnlyRoute>
            <RegisterPage />
          </PublicOnlyRoute>
        }
      />
      <Route
        path="/join"
        element={
          <ProtectedRoute>
            <RedirectIfCoupled>
              <JoinPage />
            </RedirectIfCoupled>
          </ProtectedRoute>
        }
      />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <RequireCouple>
              <HubPage />
            </RequireCouple>
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
