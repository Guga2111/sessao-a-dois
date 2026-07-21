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
}
