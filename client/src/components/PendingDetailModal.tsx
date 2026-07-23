import { useEffect, useRef, useState } from "react"

import { Heart, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { api } from "@/lib/api"
import type { MediaDetails, PendingMatch } from "@/types/media"

interface PendingDetailModalProps {
  item: PendingMatch | null
  onClose: () => void
  onLike: () => void
  onReject: () => void
  actionLoading: boolean
}

const TYPE_LABEL: Record<PendingMatch["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Serie",
}

function SkeletonDetail() {
  return (
    <div className="flex animate-pulse flex-col gap-5">
      <div className="flex gap-4">
        <div className="flex flex-col gap-1.5">
          <div className="h-3 w-10 rounded bg-white/[0.06]" />
          <div className="h-5 w-12 rounded bg-white/[0.06]" />
        </div>
        <div className="flex flex-col gap-1.5">
          <div className="h-3 w-16 rounded bg-white/[0.06]" />
          <div className="h-5 w-20 rounded bg-white/[0.06]" />
        </div>
      </div>
      <div>
        <div className="mb-2 h-3 w-16 rounded bg-white/[0.06]" />
        <div className="flex gap-2">
          <div className="h-6 w-16 rounded-full bg-white/[0.06]" />
          <div className="h-6 w-20 rounded-full bg-white/[0.06]" />
        </div>
      </div>
      <div>
        <div className="mb-2 h-3 w-24 rounded bg-white/[0.06]" />
        <div className="space-y-1.5">
          <div className="h-3.5 w-full rounded bg-white/[0.06]" />
          <div className="h-3.5 w-5/6 rounded bg-white/[0.06]" />
          <div className="h-3.5 w-4/6 rounded bg-white/[0.06]" />
        </div>
      </div>
    </div>
  )
}

export function PendingDetailModal({
  item,
  onClose,
  onLike,
  onReject,
  actionLoading,
}: PendingDetailModalProps) {
  const [details, setDetails] = useState<MediaDetails | null>(null)
  const backdropRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!item) {
      setDetails(null)
      return
    }

    setDetails(null)

    api
      .get<MediaDetails>(
        `/api/media/${item.mediaType.toLowerCase()}/${item.tmdbId}`
      )
      .then((res) => setDetails(res.data))
      .catch(() => {})
  }, [item])

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
        className="font-auth-body relative w-full max-w-[900px] overflow-hidden rounded-[22px] border border-[rgba(255,255,255,.08)] text-[#f6f4ec]"
        style={{
          background:
            "radial-gradient(800px 500px at 60% -10%, rgba(255,203,43,.06), transparent 55%), #161513",
          maxHeight: "90dvh",
          overflowY: "auto",
        }}
      >
        {/* Header */}
        <div className="sticky top-0 z-10 border-b border-[rgba(255,255,255,.06)] bg-[#161513]/90 px-6 pt-6 pb-4 backdrop-blur-sm">
          <div className="flex items-start justify-between gap-4">
            <div className="min-w-0">
              {!details ? (
                <div className="h-7 w-48 animate-pulse rounded-lg bg-white/[0.08]" />
              ) : (
                <h2 className="font-display text-[clamp(18px,3vw,24px)] font-bold leading-tight tracking-tight">
                  {details.title}
                </h2>
              )}
              <div className="mt-2 flex flex-wrap items-center gap-2 text-[13px] text-[#a6a39a]">
                <span className="rounded-md border border-[rgba(255,203,43,.4)] bg-[rgba(255,203,43,.18)] px-2.5 py-0.5 text-[11px] font-semibold text-[#ffcb2b]">
                  Sugestao
                </span>
                {!details ? (
                  <div className="h-4 w-32 animate-pulse rounded bg-white/[0.06]" />
                ) : (
                  <>
                    <span>{TYPE_LABEL[item.mediaType]}</span>
                    {providerLine && (
                      <>
                        <span className="text-[#a6a39a]/40">·</span>
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
              className="mt-0.5 flex-none rounded-xl border-[rgba(255,255,255,.1)] bg-[rgba(255,255,255,.05)] text-[#a6a39a] hover:bg-[rgba(255,255,255,.1)] hover:text-[#f6f4ec]"
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
            {!details ? (
              <SkeletonDetail />
            ) : (
              <div className="flex flex-col gap-5">
                {/* Stats row */}
                <div className="flex flex-wrap gap-x-6 gap-y-3">
                  {details.year && (
                    <div className="flex flex-col gap-1">
                      <span className="text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
                        Ano
                      </span>
                      <span className="text-[17px] font-bold">
                        {details.year}
                      </span>
                    </div>
                  )}

                  <div className="flex flex-col gap-1">
                    <span className="text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
                      Nota TMDB
                    </span>
                    <div className="flex items-center gap-1.5">
                      <span
                        className="size-2 rounded-full"
                        style={{ background: "#3ddc97" }}
                      />
                      <span className="text-[17px] font-bold">
                        {details.voteAverage != null
                          ? details.voteAverage.toFixed(1)
                          : "—"}
                        <span className="text-[13px] font-normal text-[#a6a39a]">
                          {" "}
                          /10
                        </span>
                      </span>
                    </div>
                  </div>

                  {details.runtime != null && (
                    <div className="flex flex-col gap-1">
                      <span className="text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
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
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
                      Generos
                    </span>
                    <div className="flex flex-wrap gap-1.5">
                      {details.genres.map((genre) => (
                        <span
                          key={genre}
                          className="rounded-full border border-[rgba(255,255,255,.1)] bg-[rgba(255,255,255,.05)] px-3 py-0.5 text-[12px] text-[#d6d2c8]"
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
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
                      Onde Assistir
                    </span>
                    <div className="flex flex-wrap gap-2">
                      {details.watchProviders.map((provider) => (
                        <span
                          key={provider.name}
                          className="flex items-center gap-1.5 rounded-full border border-[rgba(255,255,255,.1)] bg-[rgba(255,255,255,.05)] px-3 py-1 text-[12px] text-[#d6d2c8]"
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
                              style={{ background: "#ffcb2b" }}
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
                    <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
                      Sinopse
                    </span>
                    <p className="text-[14px] leading-relaxed text-[#c4bfb4]">
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
                    className="flex flex-1 items-center justify-center gap-2 rounded-[12px] border border-[rgba(255,107,107,.3)] bg-[rgba(255,107,107,.08)] px-4 py-3 text-[14px] font-semibold text-[#ff6b6b] hover:bg-[rgba(255,107,107,.14)]"
                  >
                    <X className="size-4" strokeWidth={2.5} />
                    Passar
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={onLike}
                    disabled={actionLoading}
                    className="flex flex-1 items-center justify-center gap-2 rounded-[12px] border border-[rgba(61,220,151,.3)] bg-[rgba(61,220,151,.08)] px-4 py-3 text-[14px] font-semibold text-[#3ddc97] hover:bg-[rgba(61,220,151,.14)]"
                  >
                    <Heart className="size-4" strokeWidth={2.5} />
                    Curtir
                  </Button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
