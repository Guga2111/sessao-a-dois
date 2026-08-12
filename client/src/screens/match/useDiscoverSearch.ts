import { useCallback, useEffect, useReducer, useRef } from "react"

import { api } from "@/lib/api"
import type { MediaGenre, MediaPage, MediaSearchResult } from "@/types/media"

export const SORT_OPTIONS = [
  { value: "popularity.desc", label: "Popularidade ↓" },
  { value: "popularity.asc", label: "Popularidade ↑" },
  { value: "vote_average.desc", label: "Avaliacao ↓" },
  { value: "vote_average.asc", label: "Avaliacao ↑" },
  { value: "release_date.desc", label: "Data de Lancamento ↓" },
  { value: "release_date.asc", label: "Data de Lancamento ↑" },
] as const

export const TMDB_MAX_PAGE = 500

const DEFAULT_VOTE_RANGE: [number, number] = [0, 10]
const DEFAULT_RUNTIME_RANGE: [number, number] = [0, 240]

type ResultsContext = "trending" | "search" | "discover"

type FetchAttempt = {
  url: string
  params: Record<string, string | number>
  context: ResultsContext
}

export type State = {
  // busca
  query: string
  // filtros
  sortBy: string
  filtersOpen: boolean
  genres: MediaGenre[]
  selectedDecades: string[]
  selectedCertifications: string[]
  selectedGenres: string[]
  voteRange: [number, number]
  runtimeRange: [number, number]
  // resultado
  results: MediaSearchResult[]
  totalResults: number
  resultsContext: ResultsContext
  searched: boolean
  // paginacao
  page: number
  totalPages: number
  // status de rede
  searching: boolean
  fetchError: boolean
}

export const initialState: State = {
  query: "",
  sortBy: SORT_OPTIONS[0].value,
  filtersOpen: false,
  genres: [],
  selectedDecades: [],
  selectedCertifications: [],
  selectedGenres: [],
  voteRange: DEFAULT_VOTE_RANGE,
  runtimeRange: DEFAULT_RUNTIME_RANGE,
  results: [],
  totalResults: 0,
  resultsContext: "trending",
  searched: false,
  page: 1,
  totalPages: 1,
  searching: false,
  fetchError: false,
}

export type Action =
  | { type: "QUERY_CHANGED"; query: string }
  | { type: "SORT_CHANGED"; sortBy: string }
  | { type: "FILTERS_OPEN_CHANGED"; open: boolean }
  | { type: "GENRES_LOADED"; genres: MediaGenre[] }
  | { type: "DECADES_CHANGED"; decades: string[] }
  | { type: "CERTIFICATIONS_CHANGED"; certifications: string[] }
  | { type: "SELECTED_GENRES_CHANGED"; genres: string[] }
  | { type: "VOTE_RANGE_CHANGED"; range: [number, number] }
  | { type: "RUNTIME_RANGE_CHANGED"; range: [number, number] }
  | { type: "FILTERS_CLEARED" }
  | { type: "FETCH_STARTED" }
  | {
      type: "FETCH_SUCCEEDED"
      results: MediaSearchResult[]
      totalResults: number
      totalPages: number
      page: number
      context: ResultsContext
    }
  | { type: "FETCH_FAILED"; context: ResultsContext }
  | { type: "FETCH_SETTLED" }

export function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "QUERY_CHANGED": {
      if (!action.query.trim()) {
        return { ...state, query: action.query, results: [], searched: false }
      }
      return { ...state, query: action.query }
    }
    case "SORT_CHANGED":
      return { ...state, sortBy: action.sortBy }
    case "FILTERS_OPEN_CHANGED":
      return { ...state, filtersOpen: action.open }
    case "GENRES_LOADED":
      return { ...state, genres: action.genres }
    case "DECADES_CHANGED":
      return { ...state, selectedDecades: action.decades }
    case "CERTIFICATIONS_CHANGED":
      return { ...state, selectedCertifications: action.certifications }
    case "SELECTED_GENRES_CHANGED":
      return { ...state, selectedGenres: action.genres }
    case "VOTE_RANGE_CHANGED":
      return { ...state, voteRange: action.range }
    case "RUNTIME_RANGE_CHANGED":
      return { ...state, runtimeRange: action.range }
    case "FILTERS_CLEARED":
      return {
        ...state,
        selectedDecades: [],
        selectedCertifications: [],
        selectedGenres: [],
        voteRange: DEFAULT_VOTE_RANGE,
        runtimeRange: DEFAULT_RUNTIME_RANGE,
      }
    case "FETCH_STARTED":
      return { ...state, searching: true }
    case "FETCH_SUCCEEDED":
      return {
        ...state,
        results: action.results,
        totalResults: action.totalResults,
        totalPages: action.totalPages,
        page: action.page,
        resultsContext: action.context,
        searched: true,
        fetchError: false,
      }
    case "FETCH_FAILED":
      return {
        ...state,
        results: [],
        totalResults: 0,
        totalPages: 1,
        resultsContext: action.context,
        searched: true,
        fetchError: true,
      }
    case "FETCH_SETTLED":
      return { ...state, searching: false }
    default:
      return state
  }
}

export function useDiscoverSearch() {
  const [state, dispatch] = useReducer(reducer, initialState)
  const lastFetchRef = useRef<FetchAttempt | null>(null)
  const lastAttemptRef = useRef<FetchAttempt | null>(null)

  const hasQuery = state.query.trim().length > 0
  const activeFilterCount =
    state.selectedDecades.length +
    state.selectedCertifications.length +
    state.selectedGenres.length +
    (state.voteRange[0] !== DEFAULT_VOTE_RANGE[0] ||
    state.voteRange[1] !== DEFAULT_VOTE_RANGE[1]
      ? 1
      : 0) +
    (state.runtimeRange[0] !== DEFAULT_RUNTIME_RANGE[0] ||
    state.runtimeRange[1] !== DEFAULT_RUNTIME_RANGE[1]
      ? 1
      : 0)

  const runFetch = useCallback(
    (
      url: string,
      params: Record<string, string | number>,
      context: ResultsContext,
      isCancelled: () => boolean = () => false
    ) => {
      dispatch({ type: "FETCH_STARTED" })
      lastAttemptRef.current = { url, params, context }
      return api
        .get<MediaPage>(url, { params })
        .then((response) => {
          if (isCancelled()) return
          dispatch({
            type: "FETCH_SUCCEEDED",
            results: response.data.results,
            totalResults: response.data.totalResults,
            totalPages: response.data.totalPages,
            page: response.data.page,
            context,
          })
          const paramsWithoutPage = { ...params }
          delete paramsWithoutPage.page
          lastFetchRef.current = { url, params: paramsWithoutPage, context }
        })
        .catch(() => {
          if (isCancelled()) return
          dispatch({ type: "FETCH_FAILED", context })
        })
        .finally(() => {
          if (!isCancelled()) dispatch({ type: "FETCH_SETTLED" })
        })
    },
    []
  )

  const handleRetry = useCallback(() => {
    const attempt = lastAttemptRef.current
    if (!attempt || state.searching) return
    runFetch(attempt.url, attempt.params, attempt.context)
  }, [runFetch, state.searching])

  useEffect(() => {
    api
      .get<MediaGenre[]>("/api/media/genres")
      .then((response) => dispatch({ type: "GENRES_LOADED", genres: response.data }))
      .catch((err) => {
        console.error("Failed to load media genres", { err })
      })
  }, [])

  useEffect(() => {
    let cancelled = false
    const trimmed = state.query.trim()
    const timer = setTimeout(
      () => {
        if (trimmed) {
          runFetch(
            "/api/media/search",
            { q: trimmed, page: 1 },
            "search",
            () => cancelled
          )
        } else {
          runFetch("/api/media/trending", { page: 1 }, "trending", () => cancelled)
        }
      },
      trimmed ? 400 : 0
    )

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [state.query, runFetch])

  const setQuery = useCallback((query: string) => {
    dispatch({ type: "QUERY_CHANGED", query })
  }, [])

  const setFiltersOpen = useCallback((open: boolean) => {
    dispatch({ type: "FILTERS_OPEN_CHANGED", open })
  }, [])

  const setSelectedDecades = useCallback((decades: string[]) => {
    dispatch({ type: "DECADES_CHANGED", decades })
  }, [])

  const setSelectedCertifications = useCallback((certifications: string[]) => {
    dispatch({ type: "CERTIFICATIONS_CHANGED", certifications })
  }, [])

  const setSelectedGenres = useCallback((genres: string[]) => {
    dispatch({ type: "SELECTED_GENRES_CHANGED", genres })
  }, [])

  const setVoteRange = useCallback((range: [number, number]) => {
    dispatch({ type: "VOTE_RANGE_CHANGED", range })
  }, [])

  const setRuntimeRange = useCallback((range: [number, number]) => {
    dispatch({ type: "RUNTIME_RANGE_CHANGED", range })
  }, [])

  const handleSortChange = useCallback(
    (nextSortBy: string | null) => {
      if (!nextSortBy) return
      dispatch({ type: "SORT_CHANGED", sortBy: nextSortBy })
      if (hasQuery) return

      runFetch("/api/media/discover", { sortBy: nextSortBy, page: 1 }, "discover")
    },
    [hasQuery, runFetch]
  )

  const handleApplyFilters = useCallback(() => {
    if (hasQuery) return

    const params: Record<string, string | number> = { sortBy: state.sortBy }
    if (state.selectedDecades.length) {
      params.releaseDecades = state.selectedDecades.join(",")
    }
    if (state.selectedCertifications.length) {
      params.certifications = state.selectedCertifications.join(",")
    }
    if (state.selectedGenres.length) {
      params.genres = state.selectedGenres.join(",")
    }
    if (state.voteRange[0] !== DEFAULT_VOTE_RANGE[0]) {
      params.voteAverageMin = state.voteRange[0]
    }
    if (state.voteRange[1] !== DEFAULT_VOTE_RANGE[1]) {
      params.voteAverageMax = state.voteRange[1]
    }
    if (state.runtimeRange[0] !== DEFAULT_RUNTIME_RANGE[0]) {
      params.runtimeMin = state.runtimeRange[0]
    }
    if (state.runtimeRange[1] !== DEFAULT_RUNTIME_RANGE[1]) {
      params.runtimeMax = state.runtimeRange[1]
    }

    runFetch("/api/media/discover", { ...params, page: 1 }, "discover")
  }, [
    hasQuery,
    runFetch,
    state.sortBy,
    state.selectedDecades,
    state.selectedCertifications,
    state.selectedGenres,
    state.voteRange,
    state.runtimeRange,
  ])

  const handlePageChange = useCallback(
    (nextPage: number) => {
      const last = lastFetchRef.current
      const clamped = Math.min(nextPage, state.totalPages, TMDB_MAX_PAGE)
      if (!last || state.searching || clamped === state.page || clamped < 1) return

      runFetch(last.url, { ...last.params, page: clamped }, last.context)
    },
    [runFetch, state.totalPages, state.searching, state.page]
  )

  const handleClearFilters = useCallback(() => {
    dispatch({ type: "FILTERS_CLEARED" })
  }, [])

  return {
    query: state.query,
    setQuery,
    sortBy: state.sortBy,
    filtersOpen: state.filtersOpen,
    setFiltersOpen,
    genres: state.genres,
    selectedDecades: state.selectedDecades,
    setSelectedDecades,
    selectedCertifications: state.selectedCertifications,
    setSelectedCertifications,
    selectedGenres: state.selectedGenres,
    setSelectedGenres,
    voteRange: state.voteRange,
    setVoteRange,
    runtimeRange: state.runtimeRange,
    setRuntimeRange,
    results: state.results,
    totalResults: state.totalResults,
    resultsContext: state.resultsContext,
    searched: state.searched,
    page: state.page,
    totalPages: state.totalPages,
    searching: state.searching,
    fetchError: state.fetchError,
    hasQuery,
    activeFilterCount,
    runFetch,
    handleRetry,
    handleSortChange,
    handleApplyFilters,
    handlePageChange,
    handleClearFilters,
  }
}
