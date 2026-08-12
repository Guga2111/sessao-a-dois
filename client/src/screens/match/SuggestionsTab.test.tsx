import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { useMatchStore } from "@/stores/useMatchStore"
import type { PendingMatch } from "@/types/media"

import { SuggestionsTab } from "./SuggestionsTab"

const mockPost = vi.fn()

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn(() => new Promise(() => {})),
    post: (...args: unknown[]) => mockPost(...args),
  },
}))

function makePendingMatch(overrides: Partial<PendingMatch> = {}): PendingMatch {
  return {
    tmdbId: 550,
    mediaType: "MOVIE",
    title: "Clube da Luta",
    posterUrl: null,
    releaseYear: 1999,
    ...overrides,
  }
}

const ERROR_MESSAGE = "Nao foi possivel carregar as sugestoes pendentes."
const EMPTY_MESSAGE = 'Nenhuma sugestao pendente. Voltem a buscar titulos na aba "Buscar".'
const ACTION_ERROR_MESSAGE = "Nao foi possivel registrar. Tentem novamente."

// A store fica em cache com a dependencia REAL de @/lib/api fechada no closure
// (storeReset.ts importa as tres stores em escopo de modulo antes do vi.mock deste
// arquivo surtir efeito - ver Codebase Patterns em progress.txt). fetchPending() e
// chamada automaticamente no mount pelo useEffect do componente; sobrescrever a acao
// via setState evita que ela dispare uma chamada de rede real, e a AC da story pede
// exatamente isso: "a store e semeada via setState no teste".
function seedStore(overrides: {
  pendingQueue?: PendingMatch[]
  pendingError?: boolean
}) {
  useMatchStore.setState({
    pendingQueue: overrides.pendingQueue ?? [],
    pendingLoading: false,
    pendingError: overrides.pendingError ?? false,
    fetchPending: vi.fn().mockResolvedValue(undefined),
  })
}

describe("SuggestionsTab", () => {
  beforeEach(() => {
    mockPost.mockReset()
  })

  it("com pendingError true, renderiza o ramo de erro visivel", () => {
    seedStore({ pendingError: true })

    render(<SuggestionsTab />)

    expect(screen.getByText(ERROR_MESSAGE)).toBeInTheDocument()
  })

  it("com pendingQueue vazia e sem erro, renderiza o estado vazio proprio", () => {
    seedStore({ pendingQueue: [] })

    render(<SuggestionsTab />)

    expect(screen.getByText(EMPTY_MESSAGE)).toBeInTheDocument()
  })

  it("com itens na fila, renderiza o card da sugestao atual", () => {
    const pending = makePendingMatch()
    seedStore({ pendingQueue: [pending] })

    render(<SuggestionsTab />)

    expect(screen.getByText(pending.title)).toBeInTheDocument()
  })

  // Os botoes de reject/like sao icon-only (X/Heart, sem aria-label): o unico botao
  // com nome acessivel no card e o CompareToggleButton ("Comparar titulos"/"Cancelar
  // comparacao"). Filtrar por ausencia de aria-label isola os dois de acao, na ordem
  // em que aparecem no JSX (reject primeiro, like depois).
  function actionButtons() {
    return screen
      .getAllByRole("button")
      .filter((button) => !button.hasAttribute("aria-label"))
  }

  it("falha ao registrar reject mostra a mensagem de erro sem derrubar a fila", async () => {
    const pending = makePendingMatch()
    seedStore({ pendingQueue: [pending] })
    mockPost.mockRejectedValueOnce(new Error("Internal Server Error"))
    const user = userEvent.setup()

    render(<SuggestionsTab />)
    const [rejectButton] = actionButtons()
    await user.click(rejectButton)

    expect(await screen.findByText(ACTION_ERROR_MESSAGE)).toBeInTheDocument()
    expect(screen.getByText(pending.title)).toBeInTheDocument()
  })

  it("falha ao registrar like mostra a mensagem de erro sem derrubar a fila", async () => {
    const pending = makePendingMatch()
    seedStore({ pendingQueue: [pending] })
    mockPost.mockRejectedValueOnce(new Error("Internal Server Error"))
    const user = userEvent.setup()

    render(<SuggestionsTab />)
    const [, likeButton] = actionButtons()
    await user.click(likeButton)

    expect(await screen.findByText(ACTION_ERROR_MESSAGE)).toBeInTheDocument()
    expect(screen.getByText(pending.title)).toBeInTheDocument()
  })

  it("um like bem-sucedido remove o item da fila", async () => {
    const first = makePendingMatch({ tmdbId: 550, title: "Clube da Luta" })
    const second = makePendingMatch({ tmdbId: 24428, title: "Os Vingadores" })
    seedStore({ pendingQueue: [first, second] })
    mockPost.mockResolvedValueOnce({ data: {} })
    const user = userEvent.setup()

    render(<SuggestionsTab />)
    const [, likeButton] = actionButtons()
    await user.click(likeButton)

    await waitFor(() => {
      expect(screen.getByText(second.title)).toBeInTheDocument()
    })
    expect(screen.queryByText(first.title)).not.toBeInTheDocument()
    expect(mockPost).toHaveBeenCalledWith(
      "/api/match/like",
      expect.objectContaining({ tmdbId: first.tmdbId, mediaType: first.mediaType })
    )
  })

  it("um reject bem-sucedido remove o item da fila", async () => {
    const first = makePendingMatch({ tmdbId: 550, title: "Clube da Luta" })
    const second = makePendingMatch({ tmdbId: 24428, title: "Os Vingadores" })
    seedStore({ pendingQueue: [first, second] })
    mockPost.mockResolvedValueOnce({ data: {} })
    const user = userEvent.setup()

    render(<SuggestionsTab />)
    const [rejectButton] = actionButtons()
    await user.click(rejectButton)

    await waitFor(() => {
      expect(screen.getByText(second.title)).toBeInTheDocument()
    })
    expect(screen.queryByText(first.title)).not.toBeInTheDocument()
    expect(mockPost).toHaveBeenCalledWith("/api/match/reject", {
      tmdbId: first.tmdbId,
      mediaType: first.mediaType,
    })
  })
})
