import { Check, Clock3 } from "lucide-react"

import { cn } from "@/lib/utils"
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
  return (
    <span className="text-sm tracking-[1px] text-[#ffb443]" aria-hidden>
      {"★★★★★".slice(0, rating)}
      <span className="text-white/15">{"★★★★★".slice(rating)}</span>
    </span>
  )
}

export function MediaCard({
  track,
  myUserId,
  onStatusChange,
  onClick,
}: MediaCardProps) {
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

  return (
    <div
      onClick={() => onClick?.(track)}
      className={cn(
        "font-auth-body cursor-pointer overflow-hidden rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] text-[#f6f4ec] transition-transform duration-[.18s] ease-out hover:-translate-y-1",
        STATUS_BORDER_HOVER[track.status]
      )}
    >
      <div
        className="relative aspect-[3/4]"
        style={{
          background: `linear-gradient(160deg, hsl(${hue} 42% 24%), hsl(${hue} 46% 11%))`,
        }}
      >
        <div
          className="absolute inset-0 opacity-100"
          style={{
            backgroundImage:
              "repeating-linear-gradient(135deg, rgba(255,255,255,.05) 0 8px, transparent 8px 16px)",
          }}
        />
        <div className="absolute top-2.5 left-2.5 rounded-lg bg-[rgba(9,9,10,.6)] px-2.5 py-1 text-[11px] font-semibold text-[#f6f4ec] backdrop-blur-md">
          {TYPE_LABEL[track.mediaType]}
        </div>
        {track.status === "WATCHED" && (
          <div className="absolute top-2.5 right-2.5 grid size-6 place-items-center rounded-full bg-[rgba(61,220,151,.9)] text-[#07130d]">
            <Check className="size-3.5" strokeWidth={3} />
          </div>
        )}
        {track.status === "WANT_TO_SEE" && (
          <div className="absolute inset-0 grid place-items-center text-[34px] opacity-50">
            🍿
          </div>
        )}
      </div>

      <div className="p-3.5">
        <div className="flex items-baseline justify-between gap-2">
          <div className="text-[15px] leading-tight font-bold">
            Título #{track.tmdbId}
          </div>
        </div>

        <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
          <span className="rounded-md border border-[rgba(255,203,43,.25)] bg-[rgba(255,203,43,.12)] px-2.5 py-0.5 text-[11px] font-semibold text-[#ffdd7a]">
            {TYPE_LABEL[track.mediaType]}
          </span>
        </div>

        {showRatings && (
          <div className="mt-2.5">
            {ratedReviews.length === 2 ? (
              <div className="flex items-center gap-4">
                {ratedReviews.map((review) => (
                  <div key={review.userId} className="flex flex-col gap-1">
                    <span className="text-[11px] text-[#a6a39a]">
                      {review.userId === myUserId ? "Você" : review.userName}
                    </span>
                    <Stars rating={review.rating!} />
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Stars rating={ratedReviews[0].rating!} />
                <span className="text-xs text-[#a6a39a]">
                  {ratedReviews[0].rating},0
                </span>
              </div>
            )}
          </div>
        )}

        {watchedLabel && (
          <div className="mt-2 flex items-center gap-1.5 text-xs text-[#a6a39a]">
            <Clock3 className="size-3.5 opacity-70" />
            Assistido em {watchedLabel}
          </div>
        )}

        {myReview?.opinion && (
          <div className="mt-2.5 rounded-[10px] border border-[rgba(255,203,43,.18)] bg-[rgba(255,203,43,.09)] px-2.5 py-2 text-[12.5px] leading-snug text-[#d9d4e6] italic">
            <span className="font-bold text-[#ffcb2b] not-italic">“</span>
            {myReview.opinion}
          </div>
        )}

        {partnerReview?.opinion && (
          <div className="mt-2.5 rounded-[10px] border border-[rgba(255,255,255,.08)] bg-[rgba(255,255,255,.04)] px-2.5 py-2 text-[12.5px] leading-snug text-[#d8d3c5] italic">
            <span className="font-bold text-[#ffcb2b] not-italic">“</span>
            {partnerReview.opinion}
          </div>
        )}

        {track.status === "WANT_TO_SEE" && (
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation()
              onStatusChange?.(track)
            }}
            className="mt-3 w-full cursor-pointer rounded-[10px] border border-[rgba(255,255,255,.12)] bg-transparent px-3 py-2 text-[13px] font-semibold text-[#f6f4ec] transition-colors hover:bg-[rgba(255,255,255,.06)]"
          >
            Marcar como visto
          </button>
        )}
      </div>
    </div>
  )
}
