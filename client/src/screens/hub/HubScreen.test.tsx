import { screen, waitFor } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

import { renderWithRouter } from "@/test/renderWithRouter"
import type { MediaStatus, MediaTrackResponse, PagedMediaTrackResponse } from "@/types/tracking"
import { HubScreen } from "./HubScreen"
import { SECTIONS } from "./helpers"

const mockGet = vi.fn()

vi.mock("@/lib/api", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
  },
}))

function makeTrack(overrides: Partial<MediaTrackResponse> = {}): MediaTrackResponse {
  return {
    id: "t1",
    tmdbId: 100,
    mediaType: "MOVIE",
    status: "WATCHING",
    watchedDate: null,
    runtime: 120,
    createdAt: "2026-01-01T00:00:00Z",
    reviews: [],
    title: "Filme Teste",
    posterUrl: null,
    releaseYear: 2024,
    ...overrides,
  }
}

function pagedResponse(overrides: Partial<PagedMediaTrackResponse> = {}): PagedMediaTrackResponse {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
    ...overrides,
  }
}

function statusOf(config: unknown): MediaStatus | undefined {
  return (config as { params?: { status?: MediaStatus } } | undefined)?.params?.status
}

describe("HubScreen", () => {
  beforeEach(() => {
    mockGet.mockReset()
  })

  it("monta as tres secoes e cada uma consulta o seu proprio status", async () => {
    mockGet.mockImplementation((url: string, config: unknown) =>
      url === "/api/tracking"
        ? Promise.resolve({ data: pagedResponse() })
        : Promise.reject(new Error(`unexpected url ${url} (${JSON.stringify(config)})`))
    )

    renderWithRouter(<HubScreen />)

    await waitFor(() => expect(mockGet).toHaveBeenCalledTimes(3))

    const queriedStatuses = mockGet.mock.calls.map(([, config]) => statusOf(config)).sort()
    expect(queriedStatuses).toEqual(["WANT_TO_SEE", "WATCHED", "WATCHING"])
    for (const [url, config] of mockGet.mock.calls) {
      expect(url).toBe("/api/tracking")
      expect((config as { params?: { page?: number } }).params?.page).toBe(0)
    }
  })

  it("falha de uma secao para o skeleton (console.error com status e pagina) sem derrubar as outras duas", async () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {})
    const wantToSeeTrack = makeTrack({
      id: "w1",
      status: "WANT_TO_SEE",
      title: "Queremos Ver Item",
    })
    const watchedTrack = makeTrack({
      id: "d1",
      status: "WATCHED",
      title: "Ja Visto Item",
    })

    mockGet.mockImplementation((url: string, config: unknown) => {
      const status = statusOf(config)
      if (status === "WATCHING") {
        return Promise.reject(
          Object.assign(new Error("Internal Server Error"), {
            isAxiosError: true,
            response: { status: 500 },
          })
        )
      }
      if (status === "WANT_TO_SEE") {
        return Promise.resolve({
          data: pagedResponse({ content: [wantToSeeTrack], totalElements: 1 }),
        })
      }
      if (status === "WATCHED") {
        return Promise.resolve({
          data: pagedResponse({ content: [watchedTrack], totalElements: 1 }),
        })
      }
      return Promise.reject(new Error(`unexpected status for ${url}`))
    })

    renderWithRouter(<HubScreen />)

    const watchingSection = SECTIONS.find((s) => s.status === "WATCHING")!

    // A secao que falhou para de "carregar" e mostra o estado vazio em vez de
    // girar para sempre - o skeleton nao fica preso.
    await screen.findByText(watchingSection.emptyMessage)

    // As duas secoes que responderam continuam renderizando seus itens.
    expect(await screen.findByText("Queremos Ver Item")).toBeInTheDocument()
    expect(await screen.findByText("Ja Visto Item")).toBeInTheDocument()

    await waitFor(() => {
      expect(consoleError).toHaveBeenCalledWith(
        expect.stringContaining("WATCHING"),
        expect.anything()
      )
    })
    const [message] = consoleError.mock.calls.find(([msg]) =>
      typeof msg === "string" && msg.includes("WATCHING")
    )!
    expect(message).toContain("página 0")

    consoleError.mockRestore()
  })
})
