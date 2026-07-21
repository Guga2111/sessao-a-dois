import { useEffect } from "react"
import { Navigate, Route, Routes } from "react-router-dom"

import { JoinPage } from "@/routes/auth/JoinPage"
import { LoginPage } from "@/routes/auth/LoginPage"
import { RegisterPage } from "@/routes/auth/RegisterPage"
import {
  ProtectedRoute,
  PublicOnlyRoute,
  RedirectIfCoupled,
  RequireCouple,
} from "@/routes/guards"
import { HubScreen } from "@/screens/HubScreen"
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
              <HubScreen />
            </RequireCouple>
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
