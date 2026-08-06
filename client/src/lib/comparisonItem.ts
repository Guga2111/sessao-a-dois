import type { ComparisonItem } from "@/components/ComparisonDialog"
import { api } from "@/lib/api"
import type { MediaDetails } from "@/types/media"
import type { MediaType } from "@/types/tracking"

export function buildComparisonItem(
  mediaType: MediaType,
  tmdbId: number,
  overrides?: Partial<ComparisonItem>
): Promise<ComparisonItem> {
  return api
    .get<MediaDetails>(`/api/media/${mediaType.toLowerCase()}/${tmdbId}`)
    .then((response) => {
      const details = response.data
      return {
        tmdbId,
        mediaType,
        title: details.title,
        year: details.year,
        posterUrl: details.posterUrl,
        overview: details.overview,
        voteAverage: details.voteAverage,
        coupleRating: null,
        genres: details.genres,
        watchProviders: details.watchProviders,
        ...overrides,
      }
    })
}
