import { describe, expect, it } from "vitest"

import type { MediaSearchResult } from "@/types/media"

import { type Action, initialState, reducer, type State } from "./useDiscoverSearch"

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

function run(actions: Action[], state: State = initialState): State {
  return actions.reduce(reducer, state)
}

describe("useDiscoverSearch reducer (pure function, no React)", () => {
  it("QUERY_CHANGED with an empty (or whitespace-only) string clears results and turns searched off", () => {
    const withResults = run([
      { type: "FETCH_STARTED" },
      {
        type: "FETCH_SUCCEEDED",
        results: [makeResult(1)],
        totalResults: 1,
        totalPages: 1,
        page: 1,
        context: "search",
      },
    ])
    expect(withResults.results).toHaveLength(1)
    expect(withResults.searched).toBe(true)

    const cleared = reducer(withResults, { type: "QUERY_CHANGED", query: "   " })
    expect(cleared.results).toEqual([])
    expect(cleared.searched).toBe(false)
    expect(cleared.query).toBe("   ")

    const clearedEmpty = reducer(withResults, { type: "QUERY_CHANGED", query: "" })
    expect(clearedEmpty.results).toEqual([])
    expect(clearedEmpty.searched).toBe(false)
  })

  it("QUERY_CHANGED with text preserves results and does not touch searched", () => {
    const withResults = run([
      { type: "FETCH_STARTED" },
      {
        type: "FETCH_SUCCEEDED",
        results: [makeResult(1)],
        totalResults: 1,
        totalPages: 1,
        page: 1,
        context: "search",
      },
    ])

    const next = reducer(withResults, { type: "QUERY_CHANGED", query: "duna" })
    expect(next.query).toBe("duna")
    expect(next.results).toBe(withResults.results)
    expect(next.searched).toBe(true)
  })

  it("FILTERS_CLEARED resets to the defaults", () => {
    const withFilters = run([
      { type: "DECADES_CHANGED", decades: ["1990"] },
      { type: "CERTIFICATIONS_CHANGED", certifications: ["16"] },
      { type: "SELECTED_GENRES_CHANGED", genres: ["28"] },
      { type: "VOTE_RANGE_CHANGED", range: [5, 9] },
      { type: "RUNTIME_RANGE_CHANGED", range: [60, 180] },
    ])

    const cleared = reducer(withFilters, { type: "FILTERS_CLEARED" })
    expect(cleared.selectedDecades).toEqual([])
    expect(cleared.selectedCertifications).toEqual([])
    expect(cleared.selectedGenres).toEqual([])
    expect(cleared.voteRange).toEqual([0, 10])
    expect(cleared.runtimeRange).toEqual([0, 240])
  })

  it("FETCH_STARTED -> FETCH_SUCCEEDED -> FETCH_SETTLED leaves searching/fetchError coherent", () => {
    const started = reducer(initialState, { type: "FETCH_STARTED" })
    expect(started.searching).toBe(true)

    const succeeded = reducer(started, {
      type: "FETCH_SUCCEEDED",
      results: [makeResult(1), makeResult(2)],
      totalResults: 2,
      totalPages: 1,
      page: 1,
      context: "trending",
    })
    expect(succeeded.searching).toBe(true)
    expect(succeeded.fetchError).toBe(false)
    expect(succeeded.results).toHaveLength(2)
    expect(succeeded.totalResults).toBe(2)
    expect(succeeded.totalPages).toBe(1)
    expect(succeeded.page).toBe(1)
    expect(succeeded.resultsContext).toBe("trending")
    expect(succeeded.searched).toBe(true)

    const settled = reducer(succeeded, { type: "FETCH_SETTLED" })
    expect(settled.searching).toBe(false)
    expect(settled.fetchError).toBe(false)
  })

  it("FETCH_STARTED -> FETCH_FAILED -> FETCH_SETTLED leaves searching false and fetchError true", () => {
    const started = reducer(initialState, { type: "FETCH_STARTED" })
    const failed = reducer(started, { type: "FETCH_FAILED", context: "search" })
    expect(failed.searching).toBe(true)
    expect(failed.fetchError).toBe(true)
    expect(failed.searched).toBe(true)
    expect(failed.resultsContext).toBe("search")

    const settled = reducer(failed, { type: "FETCH_SETTLED" })
    expect(settled.searching).toBe(false)
    expect(settled.fetchError).toBe(true)
  })

  it("GENRES_LOADED, SORT_CHANGED and FILTERS_OPEN_CHANGED change only their own field", () => {
    const withGenres = reducer(initialState, {
      type: "GENRES_LOADED",
      genres: [{ id: 28, name: "Acao" }],
    })
    expect(withGenres.genres).toEqual([{ id: 28, name: "Acao" }])
    expect(withGenres.sortBy).toBe(initialState.sortBy)
    expect(withGenres.filtersOpen).toBe(initialState.filtersOpen)

    const withSort = reducer(initialState, {
      type: "SORT_CHANGED",
      sortBy: "vote_average.desc",
    })
    expect(withSort.sortBy).toBe("vote_average.desc")
    expect(withSort.genres).toBe(initialState.genres)

    const withFiltersOpen = reducer(initialState, {
      type: "FILTERS_OPEN_CHANGED",
      open: true,
    })
    expect(withFiltersOpen.filtersOpen).toBe(true)
    expect(withFiltersOpen.sortBy).toBe(initialState.sortBy)
  })

  it("the remaining filter actions change only their own field", () => {
    const withDecades = reducer(initialState, {
      type: "DECADES_CHANGED",
      decades: ["2000"],
    })
    expect(withDecades.selectedDecades).toEqual(["2000"])
    expect(withDecades.selectedCertifications).toEqual([])

    const withCertifications = reducer(initialState, {
      type: "CERTIFICATIONS_CHANGED",
      certifications: ["18"],
    })
    expect(withCertifications.selectedCertifications).toEqual(["18"])
    expect(withCertifications.selectedGenres).toEqual([])

    const withGenres = reducer(initialState, {
      type: "SELECTED_GENRES_CHANGED",
      genres: ["12"],
    })
    expect(withGenres.selectedGenres).toEqual(["12"])
    expect(withGenres.voteRange).toEqual(initialState.voteRange)

    const withVoteRange = reducer(initialState, {
      type: "VOTE_RANGE_CHANGED",
      range: [3, 8],
    })
    expect(withVoteRange.voteRange).toEqual([3, 8])
    expect(withVoteRange.runtimeRange).toEqual(initialState.runtimeRange)

    const withRuntimeRange = reducer(initialState, {
      type: "RUNTIME_RANGE_CHANGED",
      range: [30, 90],
    })
    expect(withRuntimeRange.runtimeRange).toEqual([30, 90])
    expect(withRuntimeRange.voteRange).toEqual(initialState.voteRange)
  })
})
