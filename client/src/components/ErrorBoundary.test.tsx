import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

vi.mock("@/lib/api", () => ({
  reportClientError: vi.fn().mockResolvedValue(undefined),
  getLastRequestId: vi.fn().mockReturnValue(undefined),
}))

import { getLastRequestId, reportClientError } from "@/lib/api"

import { ErrorBoundary } from "./ErrorBoundary"

function Bomb(): never {
  throw new Error("boom")
}

describe("ErrorBoundary", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("shows the error screen and reports the render error instead of rendering a blank page", () => {
    const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {})

    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>
    )

    expect(screen.getByText(/algo quebrou do nosso lado/i)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: /recarregar a página/i })).toBeInTheDocument()
    expect(reportClientError).toHaveBeenCalledWith(
      expect.objectContaining({ message: "boom", stack: expect.any(String), route: expect.any(String) })
    )

    consoleErrorSpy.mockRestore()
  })

  it("does not render its children once a descendant has thrown", () => {
    vi.spyOn(console, "error").mockImplementation(() => {})

    render(
      <ErrorBoundary>
        <div>conteudo normal</div>
        <Bomb />
      </ErrorBoundary>
    )

    expect(screen.queryByText("conteudo normal")).not.toBeInTheDocument()
  })

  it("shows the correlation id once reportClientError resolves and one is known", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {})
    vi.mocked(getLastRequestId).mockReturnValue("abc-123")

    render(
      <ErrorBoundary>
        <Bomb />
      </ErrorBoundary>
    )

    expect(await screen.findByText("abc-123")).toBeInTheDocument()
  })

  it("renders children normally when nothing throws", () => {
    render(
      <ErrorBoundary>
        <div>tudo bem por aqui</div>
      </ErrorBoundary>
    )

    expect(screen.getByText("tudo bem por aqui")).toBeInTheDocument()
  })
})
