import { useState } from "react"

import { isAxiosError } from "axios"
import { Check, Clock3, Star, Trash2 } from "lucide-react"

import { RatingRequestDialog } from "@/components/RatingRequestDialog"
import { Button } from "@/components/ui/button"
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip"
import { api } from "@/lib/api"
import { cn } from "@/lib/utils"
import type { MediaTrackResponse } from "@/types/tracking"

interface MediaCardProps {
  track: MediaTrackResponse
  myUserId: string
  onStatusChange?: (track: MediaTrackResponse) => void
  onStartWatching?: (track: MediaTrackResponse) => void
  onReview?: (track: MediaTrackResponse) => void
  onRated?: (track: MediaTrackResponse) => void
  onClick?: (track: MediaTrackResponse) => void
  onDelete?: (track: MediaTrackResponse) => void
  /** When true, the card is in comparison-selection mode: clicking it toggles
   *  selection (via `onCompareToggle`) instead of opening the detail view. */
  compareMode?: boolean
  compareSelected?: boolean
  compareOrder?: number | null
  onCompareToggle?: (track: MediaTrackResponse) => void
}

const TYPE_LABEL: Record<MediaTrackResponse["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Série",
}

const STATUS_BORDER_HOVER: Record<MediaTrackResponse["status"], string> = {
  WATCHING: "hover:border-primary/50",
  WANT_TO_SEE: "hover:border-primary/50",
  WATCHED: "hover:border-[rgba(61,220,151,.4)]",
}

function formatWatchedDate(watchedDate: string | null): string | null {
  if (!watchedDate) return null
  const date = new Date(`${watchedDate}T00:00:00`)
  return date.toLocaleDateString("pt-BR", { day: "2-digit", month: "short" })
}

function reviewRatingLabel(rating: number | null | undefined): string {
  if (rating === null || rating === undefined) return "sem nota"
  return `${rating} ${rating === 1 ? "estrela" : "estrelas"}`
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
  onStartWatching,
  onReview,
  onRated,
  onClick,
  onDelete,
  compareMode = false,
  compareSelected = false,
  compareOrder = null,
  onCompareToggle,
}: MediaCardProps) {
  const [startingWatch, setStartingWatch] = useState(false)
  const [startWatchError, setStartWatchError] = useState<string | null>(null)
  const [rateOpen, setRateOpen] = useState(false)

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

  const handleStartWatching = async () => {
    setStartingWatch(true)
    setStartWatchError(null)
    try {
      const response = await api.patch<MediaTrackResponse>(
        `/api/tracking/${track.id}/status`,
        { status: "WATCHING" }
      )
      onStartWatching?.(response.data)
    } catch (caught) {
      const message =
        (isAxiosError(caught) && caught.response?.data?.message) ||
        "Não foi possível iniciar. Tente novamente."
      setStartWatchError(message)
      setStartingWatch(false)
    }
  }
  const coupleAvg =
    ratedReviews.length > 0
      ? ratedReviews.reduce((sum, r) => sum + r.rating!, 0) / ratedReviews.length
      : null

  return (
    <>
    <div
      onClick={() => (compareMode ? onCompareToggle?.(track) : onClick?.(track))}
      aria-pressed={compareMode ? compareSelected : undefined}
      className={cn(
        "font-auth-body group relative flex cursor-pointer flex-col overflow-hidden rounded-[18px] border bg-card text-foreground transition-transform duration-[.18s] ease-out hover:-translate-y-1",
        compareMode
          ? compareSelected
            ? "border-2 border-primary shadow-[0_0_24px_rgba(255,203,43,.18)]"
            : "border-dashed border-white/20 hover:border-white/35"
          : cn("border-[rgba(255,255,255,.07)]", STATUS_BORDER_HOVER[track.status])
      )}
    >
      <div
        className="relative aspect-[3/4]"
        style={{
          background: `linear-gradient(160deg, hsl(${hue} 42% 24%), hsl(${hue} 46% 11%))`,
        }}
      >
        {track.posterUrl ? (
          <img
            src={track.posterUrl}
            alt={track.title ?? `Título #${track.tmdbId}`}
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
        <div className="absolute top-2.5 left-2.5 rounded-lg bg-background/60 px-2.5 py-1 text-[11px] font-semibold text-foreground backdrop-blur-md">
          {TYPE_LABEL[track.mediaType]}
        </div>
        <div className="absolute top-2.5 right-2.5 flex items-center gap-1.5">
          {track.status === "WATCHED" && (
            <div className="grid size-6 flex-none place-items-center rounded-full bg-[rgba(61,220,151,.9)] text-[#07130d]">
              <Check className="size-3.5" strokeWidth={3} />
            </div>
          )}
          {compareMode ? (
            compareSelected && (
              <div className="grid size-6 flex-none place-items-center rounded-full border-2 border-card bg-primary text-[12px] font-black text-[#111]">
                {compareOrder}
              </div>
            )
          ) : (
            <button
              type="button"
              onClick={(event) => {
                event.stopPropagation()
                onDelete?.(track)
              }}
              aria-label="Excluir título"
              className="grid size-6 flex-none cursor-pointer place-items-center rounded-full bg-background/60 text-[#d6d2c8] opacity-0 backdrop-blur-md transition duration-150 group-hover:opacity-100 hover:bg-destructive/85 hover:text-[#1a0808] focus-visible:opacity-100 focus-visible:outline-2 focus-visible:outline-destructive"
            >
              <Trash2 className="size-3.5" />
            </button>
          )}
        </div>
        {track.status === "WANT_TO_SEE" && !track.posterUrl && (
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
              {track.title ?? `Título #${track.tmdbId}`}
            </div>
            {track.releaseYear && (
              <span className="flex-none text-[13px] text-muted-foreground">
                {track.releaseYear}
              </span>
            )}
          </div>

          {/* Tipo */}
          <div className="mt-2.5 flex flex-wrap items-center gap-1.5">
            <span className="rounded-md border border-primary/25 bg-primary/12 px-2.5 py-0.5 text-[11px] font-semibold text-[#ffdd7a]">
              {TYPE_LABEL[track.mediaType]}
            </span>
          </div>

          {/* Estrelas + média */}
          {showRatings && coupleAvg !== null && (
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger
                  render={
                    <div className="mt-2.5 flex w-fit items-center gap-2" />
                  }
                >
                  <Stars rating={coupleAvg} />
                  <span className="text-[13px] text-muted-foreground">
                    {coupleAvg.toFixed(1).replace(".", ",")}
                  </span>
                </TooltipTrigger>
                <TooltipContent className="rounded-lg border border-border bg-[#201e18] px-3 py-2 text-foreground shadow-xl">
                  <div className="flex flex-col gap-1">
                    {track.reviews.map((review) => (
                      <span key={review.userId} className="text-[12px]">
                        <span className="font-semibold">
                          {review.userName}:
                        </span>{" "}
                        {reviewRatingLabel(review.rating)}
                      </span>
                    ))}
                  </div>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          )}

          {/* Data */}
          {watchedLabel && (
            <div className="mt-2 flex items-center gap-1.5 text-[12px] text-muted-foreground">
              <Clock3 className="size-3.5 opacity-60" />
              Assistido em {watchedLabel}
            </div>
          )}

          {/* Opinião */}
          {(myReview?.opinion || partnerReview?.opinion) && (
            <div className="mt-2.5 rounded-[10px] border border-primary/18 bg-primary/7 px-3 py-2.5 text-[12.5px] leading-snug text-[#d9d4e6] italic">
              <span className="font-bold text-primary not-italic">"</span>
              {myReview?.opinion ?? partnerReview?.opinion}
            </div>
          )}
        </div>

        {/* Footer / CTA — hidden during comparison selection so it can't be
            accidentally triggered instead of selecting the card */}
        {!compareMode && track.status === "WANT_TO_SEE" && (
          <div className="mt-4 border-t border-[rgba(255,255,255,.06)] pt-3">
            <Button
              variant="outline"
              disabled={startingWatch}
              onClick={(event) => {
                event.stopPropagation()
                void handleStartWatching()
              }}
              className="h-auto w-full rounded-full border-[rgba(255,255,255,.15)] bg-transparent py-2.5 text-[13px] font-semibold text-foreground hover:bg-[rgba(255,255,255,.06)] hover:text-foreground disabled:cursor-not-allowed disabled:opacity-60"
            >
              {startingWatch ? "Iniciando…" : "Começar a assistir"}
            </Button>
            {startWatchError && (
              <p className="mt-2 text-[12px] text-destructive-foreground">{startWatchError}</p>
            )}
          </div>
        )}
        {!compareMode && track.status === "WATCHING" && (
          <div className="mt-4 border-t border-[rgba(255,255,255,.06)] pt-3">
            <Button
              variant="outline"
              onClick={(event) => {
                event.stopPropagation()
                onStatusChange?.(track)
              }}
              className="h-auto w-full rounded-full border-[rgba(255,255,255,.15)] bg-transparent py-2.5 text-[13px] font-semibold text-foreground hover:bg-[rgba(255,255,255,.06)] hover:text-foreground"
            >
              Marcar como assistido
            </Button>
          </div>
        )}
        {!compareMode && track.status === "WATCHED" &&
          (myReview?.rating ? (
            <div className="mt-4 border-t border-[rgba(255,255,255,.06)] pt-3">
              <Button
                variant="outline"
                onClick={(event) => {
                  event.stopPropagation()
                  onReview?.(track)
                }}
                className="h-auto w-full rounded-full border-[rgba(255,255,255,.15)] bg-transparent py-2.5 text-[13px] font-semibold text-foreground hover:bg-[rgba(255,255,255,.06)] hover:text-foreground"
              >
                Reavaliar
              </Button>
            </div>
          ) : (
            <div className="mt-4 border-t border-[rgba(255,255,255,.06)] pt-3">
              <Button
                variant="outline"
                onClick={(event) => {
                  event.stopPropagation()
                  setRateOpen(true)
                }}
                className="h-auto w-full rounded-full border-primary/35 bg-primary/10 py-2.5 text-[13px] font-semibold text-primary hover:bg-primary/16 hover:text-primary"
              >
                <Star className="mr-1.5 -mt-px inline size-3.5" strokeWidth={2.5} />
                Avaliar
              </Button>
            </div>
          ))}
      </div>
    </div>
    <RatingRequestDialog
      mediaTrackId={rateOpen ? track.id : null}
      title={track.title ?? `Título #${track.tmdbId}`}
      description="Dê sua nota e opinião sobre este título — ambas são opcionais."
      onClose={() => setRateOpen(false)}
      onSuccess={(updated) => {
        setRateOpen(false)
        onRated?.(updated)
      }}
    />
    </>
  )
}
