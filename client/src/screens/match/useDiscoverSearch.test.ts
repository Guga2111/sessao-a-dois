import { act, renderHook } from "@testing-library/react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { api } from "@/lib/api"
import type { MediaGenre, MediaPage, MediaSearchResult } from "@/types/media"

import { TMDB_MAX_PAGE, useDiscoverSearch } from "./useDiscoverSearch"

vi.mock("@/lib/api", () => ({
  api: { get: vi.fn() },
}))

const mockGet = vi.mocked(api.get)

function makeResult(tmdbId: number): MediaSearchResult {
  return {
    tmdbId,
    mediaType: "MOVIE",
    title: `Title ${tmdbId}`,
    year: 2020,
    posterUrl: null,
    overview: null,
    voteAverage: 7.5,
  }
}

function page(results: MediaSearchResult[], overrides: Partial<MediaPage> = {}): {
  data: MediaPage
} {
  return {
    data: {
      results,
      page: 1,
      totalResults: results.length,
      totalPages: 1,
      ...overrides,
    },
  }
}

const genresResponse: { data: MediaGenre[] } = { data: [] }

beforeEach(() => {
  vi.useFakeTimers()
  mockGet.mockReset()
  // Genres fetch on mount, independent of the debounced search effect.
  mockGet.mockImplementation((url: string) => {
    if (url === "/api/media/genres") return Promise.resolve(genresResponse)
    return Promise.resolve(page([makeResult(1)]))
  })
})

afterEach(() => {
  vi.useRealTimers()
})

describe("useDiscoverSearch", () => {
  it("loads trending once on mount, via /api/media/trending, without a real network call", async () => {
    renderHook(() => useDiscoverSearch())

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })

    const trendingCalls = mockGet.mock.calls.filter(([url]) => url === "/api/media/trending")
    expect(trendingCalls).toHaveLength(1)
    expect(trendingCalls[0][1]).toMatchObject({ params: { page: 1 } })
  })

  it("typing a query fires one call after the debounce, not one per keystroke", async () => {
    const { result } = renderHook(() => useDiscoverSearch())
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    mockGet.mockClear()

    act(() => result.current.setQuery("d"))
    act(() => result.current.setQuery("du"))
    act(() => result.current.setQuery("dun"))
    act(() => result.current.setQuery("duna"))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(400)
    })

    const searchCalls = mockGet.mock.calls.filter(([url]) => url === "/api/media/search")
    expect(searchCalls).toHaveLength(1)
    expect(searchCalls[0][1]).toMatchObject({ params: { q: "duna", page: 1 } })
  })

  it("a network error turns fetchError on and empties results (current reducer behavior — the UI derives its status from fetchError/searched, not from a raw results array)", async () => {
    const { result } = renderHook(() => useDiscoverSearch())
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(result.current.results).toHaveLength(1)

    mockGet.mockImplementation((url: string) => {
      if (url === "/api/media/genres") return Promise.resolve(genresResponse)
      return Promise.reject(new Error("network down"))
    })

    act(() => result.current.setQuery("falha"))
    await act(async () => {
      await vi.advanceTimersByTimeAsync(400)
    })

    expect(result.current.fetchError).toBe(true)
    expect(result.current.results).toEqual([])
    expect(result.current.searching).toBe(false)
  })

  it("changing page replays the last successful request (lastFetchRef) with only page different", async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === "/api/media/genres") return Promise.resolve(genresResponse)
      return Promise.resolve(page([makeResult(1)], { totalPages: 5 }))
    })
    const { result } = renderHook(() => useDiscoverSearch())
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(result.current.totalPages).toBe(5)
    mockGet.mockClear()

    await act(async () => {
      result.current.handlePageChange(3)
      await vi.advanceTimersByTimeAsync(0)
    })

    const trendingCalls = mockGet.mock.calls.filter(([url]) => url === "/api/media/trending")
    expect(trendingCalls).toHaveLength(1)
    expect(trendingCalls[0][1]).toMatchObject({ params: { page: 3 } })
  })

  it("pagination is capped at TMDB_MAX_PAGE", async () => {
    mockGet.mockImplementation((url: string) => {
      if (url === "/api/media/genres") return Promise.resolve(genresResponse)
      return Promise.resolve(page([makeResult(1)], { totalPages: TMDB_MAX_PAGE + 50 }))
    })
    const { result } = renderHook(() => useDiscoverSearch())
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
    expect(result.current.totalPages).toBe(TMDB_MAX_PAGE + 50)
    mockGet.mockClear()

    await act(async () => {
      result.current.handlePageChange(TMDB_MAX_PAGE + 20)
      await vi.advanceTimersByTimeAsync(0)
    })

    const trendingCalls = mockGet.mock.calls.filter(([url]) => url === "/api/media/trending")
    expect(trendingCalls[0][1]).toMatchObject({ params: { page: TMDB_MAX_PAGE } })
  })
})
