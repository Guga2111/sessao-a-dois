import type { MediaSearchResult } from "@/types/media"
import type { MediaType } from "@/types/tracking"

export const TYPE_LABEL: Record<MediaSearchResult["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Serie",
}

export function formatVoteRangeLabel([min, max]: [number, number]): string {
  const minLabel = min.toFixed(1).replace(".", ",")
  const maxLabel = max === 10 ? "10" : max.toFixed(1).replace(".", ",")
  return `${minLabel} - ${maxLabel}`
}

export function formatMinutes(minutes: number): string {
  if (minutes === 0) return "0 min"
  if (minutes % 60 === 0) return `${minutes / 60}h`
  if (minutes > 60) return `${Math.floor(minutes / 60)}h${minutes % 60}`
  return `${minutes} min`
}

export function formatRuntimeRangeLabel([min, max]: [number, number]): string {
  if (min === 0) return `Ate ${formatMinutes(max)}`
  return `${formatMinutes(min)} - ${formatMinutes(max)}`
}

export function trackKey(mediaType: string, tmdbId: number): string {
  return `${mediaType}-${tmdbId}`
}

export function formatResultsCount(total: number): string {
  return `${total} ${total === 1 ? "titulo" : "titulos"}`
}

export function paginationRange(
  current: number,
  total: number
): (number | "ellipsis")[] {
  const pages: (number | "ellipsis")[] = []
  const add = (value: number | "ellipsis") => pages.push(value)

  add(1)
  if (current - 1 > 2) add("ellipsis")
  for (
    let page = Math.max(2, current - 1);
    page <= Math.min(total - 1, current + 1);
    page++
  ) {
    add(page)
  }
  if (current + 1 < total - 1) add("ellipsis")
  if (total > 1) add(total)

  return pages
}

export function keyOfCompareItem(item: { mediaType: MediaType; tmdbId: number }): string {
  return trackKey(item.mediaType, item.tmdbId)
}
