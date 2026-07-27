import { useEffect } from "react"
import { Navigate, Route, Routes } from "react-router-dom"

import { MatchCelebrationModal } from "@/components/MatchCelebrationModal"
import { JoinPage } from "@/routes/auth/JoinPage"
import { LoginPage } from "@/routes/auth/LoginPage"
import { RegisterPage } from "@/routes/auth/RegisterPage"
import {
  ProtectedRoute,
  PublicOnlyRoute,
  RedirectIfCoupled,
  RequireCouple,
} from "@/routes/guards"
import { DashboardScreen } from "@/screens/DashboardScreen"
import { HubScreen } from "@/screens/HubScreen"
import { LandingScreen } from "@/screens/LandingScreen"
import { MatchScreen } from "@/screens/MatchScreen"
import { useAuthStore } from "@/stores/useAuthStore"
import { useMatchStore } from "@/stores/useMatchStore"

export function App() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const loadCurrentUser = useAuthStore((state) => state.loadCurrentUser)
  const coupleId = useAuthStore((state) => state.couple?.id)
  const hasPartner = useAuthStore((state) => Boolean(state.couple?.partner))
  const connect = useMatchStore((state) => state.connect)
  const disconnect = useMatchStore((state) => state.disconnect)

  useEffect(() => {
    if (isAuthenticated) {
      void loadCurrentUser()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (isAuthenticated && hasPartner && coupleId) {
      connect(coupleId)
    } else {
      disconnect()
    }

    return () => disconnect()
  }, [isAuthenticated, hasPartner, coupleId, connect, disconnect])

  return (
    <>
      <Routes>
        <Route
          path="/"
          element={
            <PublicOnlyRoute>
              <LandingScreen />
            </PublicOnlyRoute>
          }
        />
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
          path="/hub"
          element={
            <ProtectedRoute>
              <RequireCouple>
                <HubScreen />
              </RequireCouple>
            </ProtectedRoute>
          }
        />
        <Route
          path="/match"
          element={
            <ProtectedRoute>
              <RequireCouple>
                <MatchScreen />
              </RequireCouple>
            </ProtectedRoute>
          }
        />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <RequireCouple>
                <DashboardScreen />
              </RequireCouple>
            </ProtectedRoute>
          }
        />
        <Route path="*" element={<Navigate to="/hub" replace />} />
      </Routes>
      <MatchCelebrationModal />
    </>
  )
}

export default App
