import type { ComparisonItem } from "@/components/ComparisonDialog"
import { buildComparisonItem as buildComparisonItemBase } from "@/lib/comparisonItem"
import type { MediaStatus, MediaTrackResponse } from "@/types/tracking"

export const COMPARE_TOOLTIP =
  "Selecione 2 títulos para comparar informações como notas, gêneros e onde assistir."

export function buildComparisonItem(track: MediaTrackResponse): Promise<ComparisonItem> {
  const ratedReviews = track.reviews.filter(
    (review) => review.rating !== null && review.rating !== undefined
  )
  const coupleRating =
    ratedReviews.length > 0
      ? ratedReviews.reduce((sum, review) => sum + review.rating!, 0) /
        ratedReviews.length
      : null
  return buildComparisonItemBase(track.mediaType, track.tmdbId, { coupleRating })
}

export interface Section {
  status: MediaStatus
  title: string
  dotColor: string
  emptyMessage: string
}

export const SECTIONS: Section[] = [
  {
    status: "WATCHING",
    title: "Assistindo Atualmente",
    dotColor: "#ff9e2c",
    emptyMessage: "Nada em andamento agora. Que tal começar algo hoje à noite?",
  },
  {
    status: "WANT_TO_SEE",
    title: "Queremos Ver",
    dotColor: "#ffcb2b",
    emptyMessage: "A lista de desejos está vazia. Adicionem um título para começar.",
  },
  {
    status: "WATCHED",
    title: "Já Vimos",
    dotColor: "#3ddc97",
    emptyMessage: "Ainda não marcaram nada como visto.",
  },
]

export const PAGE_SIZE = 20

export interface SectionState {
  items: MediaTrackResponse[]
  page: number
  total: number
  loading: boolean
  loadingMore: boolean
}

export function emptySectionState(): SectionState {
  return { items: [], page: 0, total: 0, loading: true, loadingMore: false }
}

export function emptySections(): Record<MediaStatus, SectionState> {
  return {
    WATCHING: emptySectionState(),
    WANT_TO_SEE: emptySectionState(),
    WATCHED: emptySectionState(),
  }
}
