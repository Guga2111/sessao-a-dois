import { Component, type ErrorInfo, type ReactNode } from "react"

import { getLastRequestId, reportClientError } from "@/lib/api"

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
  correlationId?: string
}

/**
 * Componente de classe (nao ha equivalente em hook para componentDidCatch), montado em
 * main.tsx por fora do ThemeProvider e do BrowserRouter para tambem capturar excecao vinda
 * de dentro deles (US-006, Epico 12). Por isso a tela abaixo nao pode usar useNavigate/Link
 * nem token de tema - so cores inline, no mesmo padrao de arbitrary values do resto do
 * client/ (ver client/CLAUDE.md).
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("[ErrorBoundary] erro de render nao tratado", error, info.componentStack)
    void reportClientError({
      message: error.message,
      stack: error.stack,
      route: window.location.pathname,
    }).then(() => {
      this.setState({ correlationId: getLastRequestId() })
    })
  }

  handleReload = (): void => {
    window.location.reload()
  }

  render(): ReactNode {
    if (!this.state.hasError) {
      return this.props.children
    }

    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-[#09090a] px-4">
        <div className="w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] rounded-[22px] border border-[rgba(255,255,255,.08)] bg-[#161513] px-7 py-10 text-center sm:w-full sm:max-w-[420px]">
          <div aria-hidden="true" className="mb-3 text-[44px]">
            🎞️
          </div>
          <h1 className="font-display mb-2 text-xl font-bold text-[#f6f4ec]">
            Foi mal, algo quebrou do nosso lado
          </h1>
          <p className="font-auth-body mb-6 text-sm leading-relaxed text-[#a6a39a]">
            Não foi nada que vocês fizeram. Já ficamos sabendo — recarregar a página costuma
            resolver.
          </p>
          <button
            type="button"
            onClick={this.handleReload}
            className="font-auth-body w-full cursor-pointer rounded-xl bg-[#ffcb2b] px-6 py-3 text-sm font-bold text-[#1c1a17]"
          >
            Recarregar a página
          </button>
          <a
            href="/"
            className="font-auth-body mt-4 inline-block text-xs text-[#a6a39a] underline underline-offset-2"
          >
            Ir para o início
          </a>
          {this.state.correlationId && (
            <p className="font-auth-body mt-6 text-[11px] text-[rgba(166,163,154,.7)]">
              Código para relatar o problema:{" "}
              <span className="font-mono">{this.state.correlationId}</span>
            </p>
          )}
        </div>
      </div>
    )
  }
}
