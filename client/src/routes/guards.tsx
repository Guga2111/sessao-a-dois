import type { ReactNode } from "react"
import { Navigate } from "react-router-dom"

import { useAuthStore } from "@/stores/useAuthStore"

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return children
}

export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const couple = useAuthStore((state) => state.couple)

  if (isAuthenticated) {
    return <Navigate to={couple ? "/" : "/join"} replace />
  }

  return children
}

export function RequireCouple({ children }: { children: ReactNode }) {
  const couple = useAuthStore((state) => state.couple)

  if (!couple) {
    return <Navigate to="/join" replace />
  }

  return children
}

export function RedirectIfCoupled({ children }: { children: ReactNode }) {
  const couple = useAuthStore((state) => state.couple)

  if (couple) {
    return <Navigate to="/" replace />
  }

  return children
}
