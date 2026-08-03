export type MediaType = "MOVIE" | "TV"

export type MediaStatus = "WATCHING" | "WANT_TO_SEE" | "WATCHED"

export interface ReviewDto {
  userId: string
  userName: string
  rating: number | null
  opinion: string | null
}

export interface MediaTrackResponse {
  id: string
  tmdbId: number
  mediaType: MediaType
  status: MediaStatus
  watchedDate: string | null
  runtime: number | null
  createdAt: string
  reviews: ReviewDto[]
  title: string | null
  posterUrl: string | null
  releaseYear: number | null
}

// Shape of GET /api/tracking?status=...&page=...&size=... (Spring Data `Page<T>` JSON).
export interface PagedMediaTrackResponse {
  content: MediaTrackResponse[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

// Shape of GET /api/tracking/keys - mediaType + tmdbId only, no reviews/metadata.
export interface TrackKeyResponse {
  mediaType: MediaType
  tmdbId: number
}
