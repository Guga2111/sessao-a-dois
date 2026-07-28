import type { MediaType } from "@/types/tracking"

export interface MediaSearchResult {
  tmdbId: number
  mediaType: MediaType
  title: string
  year: number | null
  posterUrl: string | null
  overview: string | null
  voteAverage: number | null
}

export interface MediaDetails extends MediaSearchResult {
  genres: string[]
  runtime: number | null
  watchProviders: { name: string; logoUrl: string }[]
}

export interface MediaPage {
  results: MediaSearchResult[]
  page: number
  totalResults: number
  totalPages: number
}

export interface MediaGenre {
  id: number
  name: string
}

export interface PendingMatch {
  tmdbId: number
  mediaType: MediaType
  title: string
  posterUrl: string | null
}
