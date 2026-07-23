import { useEffect, useState } from "react"

import { Check, Clock3 } from "lucide-react"

import { Button } from "@/components/ui/button"
import { api } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { MediaDetails } from "@/types/media"
import type { MediaTrackResponse } from "@/types/tracking"

interface MediaCardProps {
  track: MediaTrackResponse
  myUserId: string
  onStatusChange?: (track: MediaTrackResponse) => void
  onClick?: (track: MediaTrackResponse) => void
}

const TYPE_LABEL: Record<MediaTrackResponse["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Série",
}

const STATUS_BORDER_HOVER: Record<MediaTrackResponse["status"], string> = {
  WATCHING: "hover:border-[rgba(255,203,43,.5)]",
  WANT_TO_SEE: "hover:border-[rgba(255,203,43,.5)]",
  WATCHED: "hover:border-[rgba(61,220,151,.4)]",
}

function formatWatchedDate(watchedDate: string | null): string | null {
  if (!watchedDate) return null
  const date = new Date(`${watchedDate}T00:00:00`)
  return date.toLocaleDateString("pt-BR", { day: "2-digit", month: "short" })
}

function Stars({ rating }: { rating: number }) {
  const filled = Math.round(rating)
  return (
    <span className="text-sm tracking-[1px]" aria-hidden>
      <span className="text-[#ffb443]">{"★".repeat(filled)}</span>
      <span className="text-white/15">{"★".repeat(5 - filled)}</span>
    </span>
  )
}

export function MediaCard({
  track,
  myUserId,
  onStatusChange,
  onClick,
}: MediaCardProps) {
  const [details, setDetails] = useState<MediaDetails | null>(null)

  useEffect(() => {
    api
      .get<MediaDetails>(
        `/api/media/${track.mediaType.toLowerCase()}/${track.tmdbId}`
      )
      .then((res) => setDetails(res.data))
      .catch(() => {})
  }, [track.tmdbId, track.mediaType])

  const hue = track.tmdbId % 360
  const watchedLabel = formatWatchedDate(track.watchedDate)

  const myReview = track.reviews.find((review) => review.userId === myUserId)
  const partnerReview = track.reviews.find(
    (review) => review.userId !== myUserId
  )
  const ratedReviews = track.reviews.filter(
    (review) => review.rating !== null && review.rating !== undefined
  )
  const showRatings = track.status !== "WANT_TO_SEE" && ratedReviews.length > 0
  const coupleAvg =
    ratedReviews.length > 0
      ? ratedReviews.reduce((sum, r) => sum + r.rating!, 0) / ratedReviews.length
      : null

  return (
    <div
      onClick={() => onClick?.(track)}
      className={cn(
        "font-auth-body flex cursor-pointer flex-col overflow-hidden rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] text-[#f6f4ec] transition-transform duration-[.18s] ease-out hover:-translate-y-1",
        STATUS_BORDER_HOVER[track.status]
      )}
    >
      <div
        className="relative aspect-[3/4]"
        style={{
          background: `linear-gradient(160deg, hsl(${hue} 42% 24%), hsl(${hue} 46% 11%))`,
        }}
      >
        {details?.posterUrl ? (
          <img
            src={details.posterUrl}
            alt={details.title}
            className="absolute inset-0 h-full w-full object-cover"
          />
        ) : (
          <div
            className="absolute inset-0 opacity-100"
            style={{
              backgroundImage:
                "repeating-linear-gradient(135deg, rgba(255,255,255,.05) 0 8px, transparent 8px 16px)",
            }}
          />
        )}
        <div className="absolute top-2.5 left-2.5 rounded-lg bg-[rgba(9,9,10,.6)] px-2.5 py-1 text-[11px] font-semibold text-[#f6f4ec] backdrop-blur-md">
          {TYPE_LABEL[track.mediaType]}
        </div>
        {track.status === "WATCHED" && (
          <div className="absolute top-2.5 right-2.5 grid size-6 place-items-center rounded-full bg-[rgba(61,220,151,.9)] text-[#07130d]">
            <Check className="size-3.5" strokeWidth={3} />
          </div>
        )}
        {track.status === "WANT_TO_SEE" && !details?.posterUrl && (
          <div className="absolute inset-0 grid place-items-center text-[34px] opacity-50">
            🍿
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col p-4">
        {/* Conteúdo principal */}
        <div className="flex-1">
          {/* Título + ano */}
          <div className="flex items-baseline justify-between gap-2">
            <div className="truncate text-[15px] font-bold leading-tight">
              {details?.title ?? `Título #${track.tmdbId}`}
            </div>
            {details?.year && (
              <span className="flex-none text-[13px] text-[#a6a39a]">
                {details.year}
              </span>
            )}
          </div>

          {/* Tipo + providers */}
          <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
            <span className="rounded-md border border-[rgba(255,203,43,.25)] bg-[rgba(255,203,43,.12)] px-2.5 py-0.5 text-[11px] font-semibold text-[#ffdd7a]">
              {TYPE_LABEL[track.mediaType]}
            </span>
            {details?.watchProviders?.slice(0, 2).map((provider) => (
              <span
                key={provider.name}
                className="flex items-center gap-1.5 rounded-md border border-[rgba(255,255,255,.1)] bg-[rgba(255,255,255,.05)] px-2.5 py-0.5 text-[11px] text-[#d6d2c8]"
              >
                {provider.logoUrl ? (
                  <img
                    src={provider.logoUrl}
                    alt=""
                    className="size-3 rounded-[3px] object-cover"
                  />
                ) : (
                  <span className="size-1.5 rounded-full bg-[#ff4040]" />
                )}
                {provider.name}
              </span>
            ))}
          </div>

          {/* Estrelas + média */}
          {showRatings && coupleAvg !== null && (
            <div className="mt-2.5 flex items-center gap-2">
              <Stars rating={coupleAvg} />
              <span className="text-[13px] text-[#a6a39a]">
                {coupleAvg.toFixed(1).replace(".", ",")}
              </span>
            </div>
          )}

          {/* Data */}
          {watchedLabel && (
            <div className="mt-2 flex items-center gap-1.5 text-[12px] text-[#a6a39a]">
              <Clock3 className="size-3.5 opacity-60" />
              Assistido em {watchedLabel}
            </div>
          )}

          {/* Opinião */}
          {(myReview?.opinion || partnerReview?.opinion) && (
            <div className="mt-2.5 rounded-[10px] border border-[rgba(255,203,43,.18)] bg-[rgba(255,203,43,.07)] px-3 py-2.5 text-[12.5px] leading-snug text-[#d9d4e6] italic">
              <span className="font-bold text-[#ffcb2b] not-italic">"</span>
              {myReview?.opinion ?? partnerReview?.opinion}
            </div>
          )}
        </div>

        {/* Footer / CTA */}
        {track.status === "WANT_TO_SEE" && (
          <div className="mt-4 border-t border-[rgba(255,255,255,.06)] pt-3">
            <Button
              variant="outline"
              onClick={(event) => {
                event.stopPropagation()
                onStatusChange?.(track)
              }}
              className="h-auto w-full rounded-full border-[rgba(255,255,255,.15)] bg-transparent py-2.5 text-[13px] font-semibold text-[#f6f4ec] hover:bg-[rgba(255,255,255,.06)] hover:text-[#f6f4ec]"
            >
              Marcar como visto
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
