import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { beforeEach, describe, expect, it, vi } from "vitest"

import type { MediaPage, MediaSearchResult } from "@/types/media"

import { SearchTab } from "./SearchTab"

const mockGet = vi.fn()

vi.mock("@/lib/api", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: vi.fn(),
  },
}))

function makeResult(overrides: Partial<MediaSearchResult> = {}): MediaSearchResult {
  return {
    tmdbId: 550,
    mediaType: "MOVIE",
    title: "Clube da Luta",
    year: 1999,
    posterUrl: null,
    overview: "Um homem insone e um vendedor de sabao formam um clube de luta.",
    voteAverage: 8.4,
    ...overrides,
  }
}

function mediaPage(
  results: MediaSearchResult[],
  overrides: Partial<Omit<MediaPage, "results">> = {}
): MediaPage {
  return {
    results,
    page: 1,
    totalResults: results.length,
    totalPages: 1,
    ...overrides,
  }
}

const IDLE_MESSAGE = "Comecem digitando o nome de um filme ou serie ai em cima."
const TRENDING_ERROR_MESSAGE =
  "Nao foi possivel carregar os titulos em alta agora."
const EMPTY_NO_QUERY_MESSAGE =
  "Nada encontrado com os filtros atuais. Ajustem a ordenacao ou os filtros."

// Endpoints disparados no mount que nao fazem parte do que estas stories testam
// (chaves ja rastreadas, generos do TMDB): resolvidos vazios por padrao, so o
// endpoint de busca/trending varia por teste.
function mockAncillaryEndpoints() {
  mockGet.mockImplementation((url: string) => {
    if (url === "/api/tracking/keys") return Promise.resolve({ data: [] })
    if (url === "/api/media/genres") return Promise.resolve({ data: [] })
    // sem implementacao para o endpoint de busca/trending: cada teste registra a sua.
    return new Promise(() => {})
  })
}

describe("SearchTab", () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockAncillaryEndpoints()
  })

  it("estado idle: sem busca ainda disparada, mostra a mensagem de convite", () => {
    // Nenhum timer avancado: o efeito de busca inicial usa setTimeout(fn, 0), que so
    // dispara depois que o corpo sincrono deste teste termina - o assert abaixo roda
    // antes disso, entao o estado ainda e o inicial (searched:false, query vazia).
    render(<SearchTab />)

    expect(screen.getByText(IDLE_MESSAGE)).toBeInTheDocument()
  })

  it("estado loading: grid escondido e skeleton visivel enquanto a busca inicial esta pendente", async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === "/api/tracking/keys") return Promise.resolve({ data: [] })
      if (url === "/api/media/genres") return Promise.resolve({ data: [] })
      if (url === "/api/media/trending") return new Promise(() => {})
      return Promise.reject(new Error(`unexpected url ${url}`))
    })

    render(<SearchTab />)

    await waitFor(() => {
      expect(
        document.querySelectorAll('[data-slot="skeleton"]').length
      ).toBeGreaterThan(0)
    })
  })

  it("estado error: falha ao carregar em alta mostra a mensagem e esconde o cabecalho de resultados", async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === "/api/tracking/keys") return Promise.resolve({ data: [] })
      if (url === "/api/media/genres") return Promise.resolve({ data: [] })
      if (url === "/api/media/trending") {
        return Promise.reject(
          Object.assign(new Error("Internal Server Error"), {
            isAxiosError: true,
            response: { status: 500 },
          })
        )
      }
      return Promise.reject(new Error(`unexpected url ${url}`))
    })

    render(<SearchTab />)

    expect(
      await screen.findByText(TRENDING_ERROR_MESSAGE, { exact: false })
    ).toBeInTheDocument()
    expect(screen.queryByText(/Resultados para/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Em alta esta semana/)).not.toBeInTheDocument()
  })

  it("estado empty: em alta sem resultados mostra a mensagem de nada encontrado e o grid vazio", async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === "/api/tracking/keys") return Promise.resolve({ data: [] })
      if (url === "/api/media/genres") return Promise.resolve({ data: [] })
      if (url === "/api/media/trending") {
        return Promise.resolve({ data: mediaPage([]) })
      }
      return Promise.reject(new Error(`unexpected url ${url}`))
    })

    render(<SearchTab />)

    expect(await screen.findByText(EMPTY_NO_QUERY_MESSAGE)).toBeInTheDocument()
  })

  it("estado ready: em alta com resultados mostra os cards", async () => {
    const result = makeResult()
    mockGet.mockImplementation((url: string) => {
      if (url === "/api/tracking/keys") return Promise.resolve({ data: [] })
      if (url === "/api/media/genres") return Promise.resolve({ data: [] })
      if (url === "/api/media/trending") {
        return Promise.resolve({ data: mediaPage([result]) })
      }
      return Promise.reject(new Error(`unexpected url ${url}`))
    })

    render(<SearchTab />)

    expect(await screen.findByText(result.title)).toBeInTheDocument()
  })

  it("REGRESSAO: em loading, o grid com os resultados anteriores continua montado (nao desmonta)", async () => {
    const previousResult = makeResult({ tmdbId: 1, title: "Resultado Anterior" })
    mockGet.mockImplementation((url: string) => {
      if (url === "/api/tracking/keys") return Promise.resolve({ data: [] })
      if (url === "/api/media/genres") return Promise.resolve({ data: [] })
      if (url === "/api/media/trending") {
        return Promise.resolve({ data: mediaPage([previousResult]) })
      }
      if (url === "/api/media/search") return new Promise(() => {})
      return Promise.reject(new Error(`unexpected url ${url}`))
    })

    render(<SearchTab />)
    expect(await screen.findByText("Resultado Anterior")).toBeInTheDocument()

    const user = userEvent.setup()
    await user.type(
      screen.getByPlaceholderText(/Coracao de Vidro/),
      "clube"
    )

    // O novo fetch de busca fica pendente para sempre (mock acima): assim que o
    // debounce (400ms) dispara e o delay do skeleton (150ms) passa, o status vira
    // "loading" - e o card antigo, que a QUERY_CHANGED preserva no reducer, tem que
    // continuar no DOM (escondido por classe), nao desmontado.
    await waitFor(
      () => {
        expect(
          document.querySelectorAll('[data-slot="skeleton"]').length
        ).toBeGreaterThan(0)
      },
      { timeout: 2000 }
    )
    expect(screen.getByText("Resultado Anterior")).toBeInTheDocument()
  })
})
