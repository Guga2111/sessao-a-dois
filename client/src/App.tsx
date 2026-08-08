import { useEffect } from "react"
import { Navigate, Route, Routes } from "react-router-dom"

import { MatchCelebrationModal } from "@/components/MatchCelebrationModal"
import { AppShellSkeleton } from "@/components/skeletons/AppShellSkeleton"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { JoinPage } from "@/routes/auth/JoinPage"
import { LoginPage } from "@/routes/auth/LoginPage"
import { RegisterPage } from "@/routes/auth/RegisterPage"
import {
  ProtectedRoute,
  PublicOnlyRoute,
  RedirectIfCoupled,
  RequireCouple,
} from "@/routes/guards"
import { AccountScreen } from "@/screens/account/AccountScreen"
import { DashboardScreen } from "@/screens/DashboardScreen"
import { HubScreen } from "@/screens/hub/HubScreen"
import { LandingScreen } from "@/screens/LandingScreen"
import { MatchScreen } from "@/screens/match/MatchScreen"
import { useAuthStore } from "@/stores/useAuthStore"
import { useMatchStore } from "@/stores/useMatchStore"
import { useNotificationStore } from "@/stores/useNotificationStore"

export function App() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const loadCurrentUser = useAuthStore((state) => state.loadCurrentUser)
  const bootstrapLoading = useAuthStore((state) => state.loading)
  const coupleId = useAuthStore((state) => state.couple?.id)
  const hasPartner = useAuthStore((state) => Boolean(state.couple?.partner))
  const connect = useMatchStore((state) => state.connect)
  const disconnect = useMatchStore((state) => state.disconnect)
  const fetchNotifications = useNotificationStore((state) => state.fetchNotifications)
  const fetchUnreadCount = useNotificationStore((state) => state.fetchUnreadCount)

  useEffect(() => {
    void loadCurrentUser()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (isAuthenticated && hasPartner && coupleId) {
      connect(coupleId)
      void fetchNotifications()
      void fetchUnreadCount()
    } else {
      disconnect()
    }

    return () => disconnect()
  }, [
    isAuthenticated,
    hasPartner,
    coupleId,
    connect,
    disconnect,
    fetchNotifications,
    fetchUnreadCount,
  ])

  const showBootstrapSkeleton = useDelayedLoading(bootstrapLoading)

  if (showBootstrapSkeleton) {
    return <AppShellSkeleton />
  }

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
        <Route
          path="/conta"
          element={
            <ProtectedRoute>
              <AccountScreen />
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
