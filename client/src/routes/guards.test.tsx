import type { ReactElement } from "react"
import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { createMemoryRouter, RouterProvider } from "react-router-dom"

import {
  ProtectedRoute,
  PublicOnlyRoute,
  RedirectIfCoupled,
  RequireCouple,
} from "@/routes/guards"
import type { Couple } from "@/stores/useAuthStore"
import { useAuthStore } from "@/stores/useAuthStore"

const coupleWithPartner: Couple = {
  id: "c1",
  inviteCode: null,
  inviteCodeExpiresAt: null,
  partner: { id: "u2", name: "Leo" },
  createdAt: "2026-01-01T00:00:00Z",
}

const coupleWithoutPartner: Couple = {
  id: "c2",
  inviteCode: "XYZ999",
  inviteCodeExpiresAt: "2026-03-01T00:00:00Z",
  partner: null,
  createdAt: "2026-02-01T00:00:00Z",
}

/** Mounts `guarded` at /protected via a real data router, alongside stub
 *  destinations for every path a guard can redirect to, so both the landed
 *  screen AND the router's historyAction (POP vs REPLACE) can be asserted —
 *  a bare MemoryRouter (renderWithRouter) has no way to expose historyAction. */
function renderGuarded(guarded: ReactElement) {
  const router = createMemoryRouter(
    [
      { path: "/protected", element: guarded },
      { path: "/login", element: <div>Tela de login</div> },
      { path: "/hub", element: <div>Tela do hub</div> },
      { path: "/join", element: <div>Tela de vinculo</div> },
    ],
    { initialEntries: ["/protected"] }
  )
  render(<RouterProvider router={router} />)
  return router
}

describe("ProtectedRoute", () => {
  it("redireciona para /login (com replace) sem sessao", () => {
    useAuthStore.setState({ isAuthenticated: false })

    const router = renderGuarded(
      <ProtectedRoute>
        <div>Conteudo protegido</div>
      </ProtectedRoute>
    )

    expect(screen.getByText("Tela de login")).toBeInTheDocument()
    expect(router.state.historyAction).toBe("REPLACE")
  })

  it("renderiza o filho com sessao", () => {
    useAuthStore.setState({ isAuthenticated: true })

    renderGuarded(
      <ProtectedRoute>
        <div>Conteudo protegido</div>
      </ProtectedRoute>
    )

    expect(screen.getByText("Conteudo protegido")).toBeInTheDocument()
  })

  it("renderiza o filho com sessao mesmo com couple presente mas sem parceiro (ProtectedRoute nao olha para couple)", () => {
    useAuthStore.setState({ isAuthenticated: true, couple: coupleWithoutPartner })

    renderGuarded(
      <ProtectedRoute>
        <div>Conteudo protegido</div>
      </ProtectedRoute>
    )

    expect(screen.getByText("Conteudo protegido")).toBeInTheDocument()
  })
})

describe("PublicOnlyRoute", () => {
  it("renderiza o filho sem sessao", () => {
    useAuthStore.setState({ isAuthenticated: false, couple: null })

    renderGuarded(
      <PublicOnlyRoute>
        <div>Tela publica</div>
      </PublicOnlyRoute>
    )

    expect(screen.getByText("Tela publica")).toBeInTheDocument()
  })

  it("redireciona para /hub (com replace) com sessao e parceiro", () => {
    useAuthStore.setState({ isAuthenticated: true, couple: coupleWithPartner })

    const router = renderGuarded(
      <PublicOnlyRoute>
        <div>Tela publica</div>
      </PublicOnlyRoute>
    )

    expect(screen.getByText("Tela do hub")).toBeInTheDocument()
    expect(router.state.historyAction).toBe("REPLACE")
  })

  it("redireciona para /join (com replace) com sessao e sem parceiro (couple presente, partner null)", () => {
    useAuthStore.setState({ isAuthenticated: true, couple: coupleWithoutPartner })

    const router = renderGuarded(
      <PublicOnlyRoute>
        <div>Tela publica</div>
      </PublicOnlyRoute>
    )

    expect(screen.getByText("Tela de vinculo")).toBeInTheDocument()
    expect(router.state.historyAction).toBe("REPLACE")
  })
})

describe("RequireCouple", () => {
  it("redireciona para /join (com replace) sem couple", () => {
    useAuthStore.setState({ couple: null })

    const router = renderGuarded(
      <RequireCouple>
        <div>Conteudo do casal</div>
      </RequireCouple>
    )

    expect(screen.getByText("Tela de vinculo")).toBeInTheDocument()
    expect(router.state.historyAction).toBe("REPLACE")
  })

  it("redireciona para /join (com replace) com couple presente mas sem parceiro", () => {
    useAuthStore.setState({ couple: coupleWithoutPartner })

    const router = renderGuarded(
      <RequireCouple>
        <div>Conteudo do casal</div>
      </RequireCouple>
    )

    expect(screen.getByText("Tela de vinculo")).toBeInTheDocument()
    expect(router.state.historyAction).toBe("REPLACE")
  })

  it("renderiza o filho com parceiro", () => {
    useAuthStore.setState({ couple: coupleWithPartner })

    renderGuarded(
      <RequireCouple>
        <div>Conteudo do casal</div>
      </RequireCouple>
    )

    expect(screen.getByText("Conteudo do casal")).toBeInTheDocument()
  })
})

describe("RedirectIfCoupled", () => {
  it("redireciona para /hub (com replace) com parceiro", () => {
    useAuthStore.setState({ couple: coupleWithPartner })

    const router = renderGuarded(
      <RedirectIfCoupled>
        <div>Tela de convite</div>
      </RedirectIfCoupled>
    )

    expect(screen.getByText("Tela do hub")).toBeInTheDocument()
    expect(router.state.historyAction).toBe("REPLACE")
  })

  it("renderiza o filho com couple presente mas sem parceiro", () => {
    useAuthStore.setState({ couple: coupleWithoutPartner })

    renderGuarded(
      <RedirectIfCoupled>
        <div>Tela de convite</div>
      </RedirectIfCoupled>
    )

    expect(screen.getByText("Tela de convite")).toBeInTheDocument()
  })

  it("renderiza o filho sem couple", () => {
    useAuthStore.setState({ couple: null })

    renderGuarded(
      <RedirectIfCoupled>
        <div>Tela de convite</div>
      </RedirectIfCoupled>
    )

    expect(screen.getByText("Tela de convite")).toBeInTheDocument()
  })
})
