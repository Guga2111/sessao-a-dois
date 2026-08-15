import { useCallback, useEffect, useRef, useState } from "react"

import { Heart, RefreshCw, TriangleAlert, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { DetailModalSkeleton } from "@/components/skeletons/DetailModalSkeleton"
import { api } from "@/lib/api"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import type { MediaDetails, PendingMatch } from "@/types/media"

interface PendingDetailModalProps {
  item: PendingMatch | null
  onClose: () => void
  onLike: () => void
  onReject: () => void
  actionLoading: boolean
  actionError?: string | null
  onRetryAction?: () => void
}

const TYPE_LABEL: Record<PendingMatch["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Serie",
}

export function PendingDetailModal({
  item,
  onClose,
  onLike,
  onReject,
  actionLoading,
  actionError,
  onRetryAction,
}: PendingDetailModalProps) {
  const [details, setDetails] = useState<MediaDetails | null>(null)
  const [detailsError, setDetailsError] = useState(false)
  const backdropRef = useRef<HTMLDivElement>(null)
  const showSkeleton = useDelayedLoading(!details && !detailsError)

  const fetchDetails = useCallback(
    (current: PendingMatch, isCancelled: () => boolean) => {
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
          console.error("Failed to load pending match details", {
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
      if (!item) return
      fetchDetails(item, () => cancelled)
    }, 0)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [item, fetchDetails])

  const handleRetryDetails = () => {
    if (!item) return
    setDetailsError(false)
    fetchDetails(item, () => false)
  }

  useEffect(() => {
    if (!item) return
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose()
    }
    window.addEventListener("keydown", handleKey)
    return () => window.removeEventListener("keydown", handleKey)
  }, [item, onClose])

  if (!item) return null

  const hue = item.tmdbId % 360
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
        className="font-auth-body relative w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] overflow-hidden rounded-[22px] border border-white/8 text-foreground sm:w-full sm:max-w-[900px]"
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
                  {details?.title ?? item.title}
                </h2>
              )}
              <div className="mt-2 flex flex-wrap items-center gap-2 text-[13px] text-muted-foreground">
                <span className="rounded-md border border-primary/40 bg-primary/18 px-2.5 py-0.5 text-[11px] font-semibold text-primary">
                  Sugestao
                </span>
                {showSkeleton ? (
                  <div className="h-4 w-32 animate-pulse rounded bg-white/[0.06]" />
                ) : (
                  <>
                    <span>{TYPE_LABEL[item.mediaType]}</span>
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
              className="w-full overflow-hidden rounded-[14px]"
              style={{
                aspectRatio: "3/4",
                background: item.posterUrl ? undefined : posterFallback,
                boxShadow:
                  "0 8px 32px rgba(255,203,43,.22), 0 2px 8px rgba(0,0,0,.5)",
              }}
            >
              {item.posterUrl && (
                <img
                  src={item.posterUrl}
                  alt={item.title}
                  className="h-full w-full object-cover"
                />
              )}
              {!item.posterUrl && (
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
            ) : showSkeleton || !details ? (
              <DetailModalSkeleton showPoster={false} />
            ) : (
              <div className="flex flex-col gap-5">
                {/* Stats row */}
                <div className="flex flex-wrap gap-x-6 gap-y-3">
                  {details.year && (
                    <div className="flex flex-col gap-1">
                      <span className="text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                        Ano
                      </span>
                      <span className="text-[17px] font-bold">
                        {details.year}
                      </span>
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
                        {details.voteAverage != null
                          ? details.voteAverage.toFixed(1)
                          : "—"}
                        <span className="text-[13px] font-normal text-muted-foreground">
                          {" "}
                          /10
                        </span>
                      </span>
                    </div>
                  </div>

                  {details.runtime != null && (
                    <div className="flex flex-col gap-1">
                      <span className="text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                        Duracao
                      </span>
                      <span className="text-[17px] font-bold">
                        {details.runtime} min
                      </span>
                    </div>
                  )}
                </div>

                {/* Genres */}
                {details.genres && details.genres.length > 0 && (
                  <div>
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                      Generos
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
                {details.watchProviders && details.watchProviders.length > 0 && (
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
                {details.overview && (
                  <div>
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-muted-foreground uppercase">
                      Sinopse
                    </span>
                    <p className="text-[14px] leading-relaxed text-synopsis-foreground">
                      {details.overview}
                    </p>
                  </div>
                )}

                {/* Action buttons */}
                <div className="flex gap-3 pt-1">
                  <Button
                    type="button"
                    variant="destructive"
                    onClick={onReject}
                    disabled={actionLoading}
                    className="flex flex-1 items-center justify-center gap-2 rounded-[12px] border border-destructive/30 bg-destructive/8 px-4 py-3 text-[14px] font-semibold text-destructive hover:bg-destructive/14"
                  >
                    <X className="size-4" strokeWidth={2.5} />
                    Passar
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={onLike}
                    disabled={actionLoading}
                    className="flex flex-1 items-center justify-center gap-2 rounded-[12px] border border-success/30 bg-success/8 px-4 py-3 text-[14px] font-semibold text-success hover:bg-success/14"
                  >
                    <Heart className="size-4" strokeWidth={2.5} />
                    Curtir
                  </Button>
                </div>

                {actionError && (
                  <div className="flex flex-col items-center gap-2 rounded-2xl border border-destructive/35 bg-destructive/8 px-5 py-3 text-center">
                    <p className="flex items-center gap-2 text-[13px] text-destructive-foreground">
                      <TriangleAlert className="size-4" />
                      {actionError}
                    </p>
                    <Button
                      type="button"
                      onClick={onRetryAction}
                      disabled={actionLoading}
                      className="flex items-center gap-2 rounded-full border border-destructive/40 bg-transparent px-4 py-1.5 text-[13px] font-semibold text-destructive-foreground hover:bg-destructive/12"
                    >
                      <RefreshCw className="size-3.5" /> Tentar novamente
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
