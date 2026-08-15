import { useCallback, useEffect, useRef, useState } from "react"

import { RefreshCw, TriangleAlert, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { DetailModalSkeleton } from "@/components/skeletons/DetailModalSkeleton"
import { api } from "@/lib/api"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import type { MediaDetails } from "@/types/media"
import type { MediaTrackResponse } from "@/types/tracking"

interface MediaDetailModalProps {
  track: MediaTrackResponse | null
  myUserId: string
  onClose: () => void
  onStatusChange?: (track: MediaTrackResponse) => void
}

const STATUS_LABEL: Record<MediaTrackResponse["status"], string> = {
  WATCHING: "Assistindo",
  WANT_TO_SEE: "Quero Ver",
  WATCHED: "Já Vi",
}

const TYPE_LABEL: Record<MediaTrackResponse["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Série",
}

const STATUS_BADGE: Record<
  MediaTrackResponse["status"],
  { bg: string; border: string; text: string }
> = {
  WATCHING: {
    bg: "rgba(255,203,43,.18)",
    border: "rgba(255,203,43,.4)",
    text: "var(--primary)",
  },
  WANT_TO_SEE: {
    bg: "rgba(180,130,255,.14)",
    border: "rgba(180,130,255,.35)",
    text: "var(--want-to-see)",
  },
  WATCHED: {
    bg: "rgba(61,220,151,.13)",
    border: "rgba(61,220,151,.35)",
    text: "var(--success)",
  },
}

const STATUS_GLOW: Record<MediaTrackResponse["status"], string> = {
  WATCHING: "rgba(255,203,43,.28)",
  WANT_TO_SEE: "rgba(180,130,255,.22)",
  WATCHED: "rgba(61,220,151,.22)",
}

function Stars({ count, total = 5 }: { count: number; total?: number }) {
  const filled = Math.round(count)
  return (
    <span className="tracking-[2px]" aria-hidden>
      <span className="text-rating">{"★".repeat(filled)}</span>
      <span className="text-white/15">{"★".repeat(total - filled)}</span>
    </span>
  )
}

export function MediaDetailModal({
  track,
  myUserId,
  onClose,
  onStatusChange,
}: MediaDetailModalProps) {
  const [details, setDetails] = useState<MediaDetails | null>(null)
  const [detailsError, setDetailsError] = useState(false)
  const backdropRef = useRef<HTMLDivElement>(null)
  const showSkeleton = useDelayedLoading(!details && !detailsError)

  const fetchDetails = useCallback(
    (current: MediaTrackResponse, isCancelled: () => boolean) => {
      api
        .get<MediaDetails>(
          `/api/media/${current.mediaType.toLowerCase()}/${current.tmdbId}`
        )
        .then((res) => {
          if (isCancelled()) return
          setDetails(res.data)
          setDetailsError(false)
        })
        .catch((err) => {
          if (isCancelled()) return
          console.error("Failed to load media details", {
            tmdbId: current.tmdbId,
            mediaType: current.mediaType,
            err,
          })
          setDetailsError(true)
        })
    },
    []
  )

  useEffect(() => {
    let cancelled = false
    const timer = setTimeout(() => {
      if (cancelled) return
      setDetails(null)
      setDetailsError(false)
      if (!track) return
      fetchDetails(track, () => cancelled)
    }, 0)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [track, fetchDetails])

  const handleRetryDetails = () => {
    if (!track) return
    setDetailsError(false)
    fetchDetails(track, () => false)
  }

  useEffect(() => {
    if (!track) return
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose()
    }
    window.addEventListener("keydown", handleKey)
    return () => window.removeEventListener("keydown", handleKey)
  }, [track, onClose])

  if (!track) return null

  const badge = STATUS_BADGE[track.status]
  const glow = STATUS_GLOW[track.status]

  const ratedReviews = track.reviews.filter((r) => r.rating != null)
  const coupleAvg =
    ratedReviews.length > 0
      ? ratedReviews.reduce((sum, r) => sum + r.rating!, 0) / ratedReviews.length
      : null

  const hue = track.tmdbId % 360
  const posterFallback = `linear-gradient(160deg, hsl(${hue} 42% 24%), hsl(${hue} 46% 11%))`

  const providerLine = details?.watchProviders?.length
    ? details.watchProviders.map((p) => p.name).join(" · ")
    : null

  return (
    <div
      ref={backdropRef}
      onClick={(e) => {
        if (e.target === backdropRef.current) onClose()
      }}
      className="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
      style={{ background: "rgba(9,9,10,.82)", backdropFilter: "blur(12px)" }}
    >
      <div
        className="font-auth-body relative w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] overflow-hidden rounded-3xl border border-white/8 text-foreground sm:w-full sm:max-w-[900px]"
        style={{
          background:
            "radial-gradient(800px 500px at 60% -10%, rgba(255,203,43,.06), transparent 55%), var(--card)",
          maxHeight: "90svh",
          overflowY: "auto",
        }}
      >
        {/* Header */}
        <div className="sticky top-0 z-10 border-b border-white/6 bg-card/90 px-6 pt-6 pb-4 backdrop-blur-sm">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              {showSkeleton ? (
                <div className="h-7 w-48 animate-pulse rounded-lg bg-white/[0.08]" />
              ) : (
                <h2 className="font-display text-[clamp(18px,3vw,24px)] font-bold leading-tight tracking-tight">
                  {details?.title ?? `Título #${track.tmdbId}`}
                </h2>
              )}
              <div className="mt-2 flex flex-wrap items-center gap-2 text-[13px] text-muted-foreground">
                <span
                  className="rounded-md px-2.5 py-0.5 text-[11px] font-semibold"
                  style={{
                    background: badge.bg,
                    border: `1px solid ${badge.border}`,
                    color: badge.text,
                  }}
                >
                  {STATUS_LABEL[track.status]}
                </span>
                {showSkeleton ? (
                  <div className="h-4 w-32 animate-pulse rounded bg-white/[0.06]" />
                ) : (
                  <>
                    <span>{TYPE_LABEL[track.mediaType]}</span>
                    {providerLine && (
                      <>
                        <span className="text-muted-foreground/40">·</span>
                        <span>{providerLine}</span>
                      </>
                    )}
                  </>
                )}
              </div>
            </div>
            <Button
              type="button"
              variant="outline"
              size="icon-sm"
              onClick={onClose}
              className="mt-0.5 flex-none rounded-xl border-border bg-white/5 text-muted-foreground hover:bg-white/10 hover:text-foreground"
            >
              <X className="size-4" />
            </Button>
          </div>
        </div>

        {/* Body */}
        <div className="flex flex-col gap-6 p-6 sm:flex-row">
          {/* Poster */}
          <div className="flex-none sm:w-[200px]">
            <div
              className="w-full overflow-hidden rounded-xl"
              style={{
                aspectRatio: "3/4",
                background:
                  !showSkeleton && details?.posterUrl ? undefined : posterFallback,
                boxShadow: `0 8px 32px ${glow}, 0 2px 8px rgba(0,0,0,.5)`,
              }}
            >
              {showSkeleton && (
                <div className="h-full w-full animate-pulse bg-white/[0.04]" />
              )}
              {!showSkeleton && details?.posterUrl && (
                <img
                  src={details.posterUrl}
                  alt={details.title}
                  className="h-full w-full object-cover"
                />
              )}
              {!showSkeleton && !details?.posterUrl && !!details && (
                <div
                  className="h-full w-full"
                  style={{
                    backgroundImage:
                      "repeating-linear-gradient(135deg, rgba(255,255,255,.05) 0 8px, transparent 8px 16px)",
                  }}
                />
              )}
            </div>
          </div>

          {/* Details */}
          <div className="min-w-0 flex-1">
            {detailsError ? (
              <div className="flex flex-col items-center gap-3 rounded-2xl border border-destructive/35 bg-destructive/8 px-5 py-8 text-center">
                <TriangleAlert className="size-5 text-destructive-foreground" />
                <p className="text-[13px] text-destructive-foreground">
                  Não foi possível carregar os detalhes deste título.
                </p>
                <Button
                  type="button"
                  onClick={handleRetryDetails}
                  className="flex items-center gap-2 rounded-full border border-destructive/40 bg-transparent px-4 py-1.5 text-[13px] font-semibold text-destructive-foreground hover:bg-destructive/12"
                >
                  <RefreshCw className="size-3.5" /> Tentar novamente
                </Button>
              </div>
            ) : showSkeleton ? (
              <DetailModalSkeleton showPoster={false} />
            ) : (
              <div className="flex flex-col gap-5">
                {/* Stats row */}
                <div className="flex flex-wrap gap-x-6 gap-y-3">
                  {details?.year && (
                    <div className="flex flex-col gap-1">
                      <span className="text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                        Ano
                      </span>
                      <span className="text-[17px] font-bold">{details.year}</span>
                    </div>
                  )}

                  {coupleAvg !== null && (
                    <div className="flex flex-col gap-1">
                      <span className="text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                        Aval. Casal
                      </span>
                      <div className="flex items-center gap-1.5">
                        <Stars count={coupleAvg} />
                        <span className="text-[13px] font-semibold text-foreground">
                          {coupleAvg.toFixed(1).replace(".", ",")} / 5
                        </span>
                      </div>
                    </div>
                  )}

                  <div className="flex flex-col gap-1">
                    <span className="text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                      Nota TMDB
                    </span>
                    <div className="flex items-center gap-1.5">
                      <span
                        className="size-2 rounded-full"
                        style={{ background: "var(--success)" }}
                      />
                      <span className="text-[17px] font-bold">
                        {details?.voteAverage != null
                          ? details.voteAverage.toFixed(1)
                          : "—"}
                        <span className="text-[13px] font-normal text-muted-foreground">
                          {" "}
                          /10
                        </span>
                      </span>
                    </div>
                  </div>
                </div>

                {/* Genres */}
                {details?.genres && details.genres.length > 0 && (
                  <div>
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                      Gêneros
                    </span>
                    <div className="flex flex-wrap gap-1.5">
                      {details.genres.map((genre) => (
                        <span
                          key={genre}
                          className="rounded-full border border-border bg-white/5 px-3 py-0.5 text-[12px] text-pill-foreground"
                        >
                          {genre}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Watch Providers */}
                {details?.watchProviders && details.watchProviders.length > 0 && (
                  <div>
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                      Onde Assistir
                    </span>
                    <div className="flex flex-wrap gap-2">
                      {details.watchProviders.map((provider) => (
                        <span
                          key={provider.name}
                          className="flex items-center gap-1.5 rounded-full border border-border bg-white/5 px-3 py-1 text-[12px] text-pill-foreground"
                        >
                          {provider.logoUrl ? (
                            <img
                              src={provider.logoUrl}
                              alt={provider.name}
                              className="size-3.5 rounded-sm object-cover"
                            />
                          ) : (
                            <span
                              className="size-2 rounded-full"
                              style={{ background: "var(--primary)" }}
                            />
                          )}
                          {provider.name}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Overview */}
                {details?.overview && (
                  <div>
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                      Sinopse
                    </span>
                    <p className="text-[14px] leading-relaxed text-synopsis-foreground">
                      {details.overview}
                    </p>
                  </div>
                )}

                {/* Individual ratings */}
                {ratedReviews.length > 0 && (
                  <div>
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                      Avaliações
                    </span>
                    <div className="flex flex-wrap gap-4">
                      {ratedReviews.map((review) => (
                        <div
                          key={review.userId}
                          className="flex flex-col gap-1"
                        >
                          <span className="text-[12px] text-muted-foreground">
                            {review.userId === myUserId ? "Você" : review.userName}
                          </span>
                          <div className="flex items-center gap-1.5">
                            <Stars count={review.rating!} />
                            <span className="text-[12px] text-muted-foreground">
                              {review.rating},0
                            </span>
                          </div>
                          {review.opinion && (
                            <p className="mt-0.5 max-w-[200px] text-[12px] leading-snug text-opinion-foreground italic">
                              <span className="font-bold text-primary not-italic">"</span>
                              {review.opinion}
                            </p>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* CTA */}
                {track.status !== "WATCHED" && (
                  <Button
                    onClick={() => {
                      onStatusChange?.(track)
                      onClose()
                    }}
                    className="mt-4 ml-auto flex h-auto rounded-full border-0 bg-primary px-8 py-3.5 text-[15px] font-bold text-background shadow-[var(--shadow-glow-primary-10)] hover:bg-accent hover:text-background"
                  >
                    Marcar como visto
                  </Button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
