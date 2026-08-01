import { useMemo, useState } from "react"

import { ArrowUp, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog"
import { Skeleton } from "@/components/ui/skeleton"
import { useIsMobile } from "@/lib/useIsMobile"
import { cn } from "@/lib/utils"
import type { MediaType } from "@/types/tracking"

export interface ComparisonItem {
  tmdbId: number
  mediaType: MediaType
  title: string
  year: number | null
  posterUrl: string | null
  overview: string | null
  voteAverage: number | null
  coupleRating: number | null
  genres: string[]
  watchProviders: { name: string; logoUrl: string }[]
}

interface ComparisonDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** `null` while details are still being fetched — renders a skeleton column instead. */
  left: ComparisonItem | null
  right: ComparisonItem | null
}

const TYPE_LABEL: Record<MediaType, string> = {
  MOVIE: "Filme",
  TV: "Série",
}

type Side = "left" | "right"

function sharedNames(a: string[], b: string[]): Set<string> {
  const bLower = new Set(b.map((name) => name.toLowerCase()))
  return new Set(
    a.filter((name) => bLower.has(name.toLowerCase())).map((name) => name.toLowerCase())
  )
}

function higherSide(left: number | null, right: number | null): Side | null {
  if (left === null || right === null) return null
  if (left > right) return "left"
  if (right > left) return "right"
  return null
}

function RatingWinnerBadge() {
  return (
    <span className="inline-flex items-center text-[#ffcb2b]" aria-hidden>
      <ArrowUp className="size-3.5" strokeWidth={3} />
    </span>
  )
}

function CoupleStars({ rating }: { rating: number }) {
  const filled = Math.round(rating)
  return (
    <span className="text-[15px] tracking-[2px]" aria-hidden>
      <span className="text-[#ffb443]">{"★".repeat(filled)}</span>
      <span className="text-white/15">{"★".repeat(5 - filled)}</span>
    </span>
  )
}

function ComparisonColumn({
  item,
  side,
  sharedGenres,
  sharedProviders,
  coupleWinner,
  tmdbWinner,
}: {
  item: ComparisonItem
  side: Side
  sharedGenres: Set<string>
  sharedProviders: Set<string>
  coupleWinner: Side | null
  tmdbWinner: Side | null
}) {
  const [expanded, setExpanded] = useState(false)
  const isCoupleWinner = coupleWinner === side
  const isTmdbWinner = tmdbWinner === side
  const hue = item.tmdbId % 360
  const posterFallback = `linear-gradient(160deg, hsl(${hue} 42% 24%), hsl(${hue} 46% 11%))`

  return (
    <div className="flex min-w-0 flex-1 flex-col gap-4">
      {/* Poster */}
      <div
        className="w-full overflow-hidden rounded-[14px]"
        style={{
          aspectRatio: "2/3",
          background: item.posterUrl ? undefined : posterFallback,
        }}
      >
        {item.posterUrl ? (
          <img
            src={item.posterUrl}
            alt={item.title}
            className="h-full w-full object-cover"
          />
        ) : (
          <div
            className="h-full w-full"
            style={{
              backgroundImage:
                "repeating-linear-gradient(135deg, rgba(255,255,255,.05) 0 8px, transparent 8px 16px)",
            }}
          />
        )}
      </div>

      {/* Title + year + type */}
      <div>
        <h3 className="font-display text-[19px] font-bold leading-tight tracking-tight text-[#f6f4ec]">
          {item.title}
        </h3>
        <div className="mt-1.5 flex flex-wrap items-center gap-2 text-[13px] text-[#a6a39a]">
          {item.year && <span>{item.year}</span>}
          <span className="rounded-md border border-[rgba(255,203,43,.25)] bg-[rgba(255,203,43,.12)] px-2.5 py-0.5 text-[11px] font-semibold text-[#ffdd7a]">
            {TYPE_LABEL[item.mediaType]}
          </span>
        </div>
      </div>

      {/* Ratings */}
      <div className="flex flex-wrap gap-x-6 gap-y-3">
        <div className="flex flex-col gap-1">
          <span className="text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
            Nota do Casal
          </span>
          {item.coupleRating !== null ? (
            <div className="flex items-center gap-1.5">
              <CoupleStars rating={item.coupleRating} />
              <span
                className={cn(
                  "text-[13px] font-semibold",
                  isCoupleWinner ? "text-[#ffcb2b]" : "text-[#f6f4ec]"
                )}
              >
                {item.coupleRating.toFixed(1).replace(".", ",")} / 5
              </span>
              {isCoupleWinner && <RatingWinnerBadge />}
            </div>
          ) : (
            <span className="text-[13px] text-[#a6a39a]">Sem avaliação</span>
          )}
        </div>
        <div className="flex flex-col gap-1">
          <span className="text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
            Nota TMDB
          </span>
          <div className="flex items-center gap-1.5">
            <span
              className="size-2 rounded-full"
              style={{ background: "#3ddc97" }}
            />
            <span
              className={cn(
                "text-[15px] font-bold",
                isTmdbWinner ? "text-[#ffcb2b]" : "text-[#f6f4ec]"
              )}
            >
              {item.voteAverage != null ? item.voteAverage.toFixed(1) : "—"}
              <span className="text-[12px] font-normal text-[#a6a39a]">
                {" "}
                /10
              </span>
            </span>
            {isTmdbWinner && <RatingWinnerBadge />}
          </div>
        </div>
      </div>

      {/* Genres */}
      {item.genres.length > 0 && (
        <div>
          <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
            Gêneros
          </span>
          <div className="flex flex-wrap gap-1.5">
            {item.genres.map((genre) => (
              <span
                key={genre}
                className={cn(
                  "rounded-full border px-3 py-0.5 text-[12px]",
                  sharedGenres.has(genre.toLowerCase())
                    ? "border-[rgba(255,203,43,.25)] bg-[rgba(255,203,43,.12)] text-[#ffdd7a]"
                    : "border-[rgba(255,255,255,.1)] bg-[rgba(255,255,255,.05)] text-[#d6d2c8]"
                )}
              >
                {genre}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Synopsis */}
      {item.overview && (
        <div>
          <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
            Sinopse
          </span>
          <p
            className={cn(
              "text-[13.5px] leading-relaxed text-[#c4bfb4]",
              !expanded && "line-clamp-4"
            )}
          >
            {item.overview}
          </p>
          {item.overview.length > 160 && (
            <button
              type="button"
              onClick={() => setExpanded((v) => !v)}
              className="mt-1.5 text-[12.5px] font-semibold text-[#ffcb2b] hover:text-[#ffe08a]"
            >
              {expanded ? "Ler menos" : "Ler mais"}
            </button>
          )}
        </div>
      )}

      {/* Watch providers */}
      {item.watchProviders.length > 0 && (
        <div>
          <span className="mb-2 block text-[11px] font-semibold tracking-[.12em] text-[#a6a39a] uppercase">
            Onde Assistir
          </span>
          <div className="flex flex-wrap gap-2">
            {item.watchProviders.map((provider) => {
              const isShared = sharedProviders.has(provider.name.toLowerCase())
              return (
                <span
                  key={provider.name}
                  className={cn(
                    "flex items-center gap-1.5 rounded-full border px-3 py-1 text-[12px]",
                    isShared
                      ? "border-[rgba(255,203,43,.25)] bg-[rgba(255,203,43,.12)] text-[#ffdd7a]"
                      : "border-[rgba(255,255,255,.1)] bg-[rgba(255,255,255,.05)] text-[#d6d2c8]"
                  )}
                >
                  {provider.logoUrl ? (
                    <img
                      src={provider.logoUrl}
                      alt=""
                      className="size-3.5 rounded-sm object-cover"
                    />
                  ) : (
                    <span
                      className="size-2 rounded-full"
                      style={{ background: isShared ? "#ffcb2b" : "#a6a39a" }}
                    />
                  )}
                  {provider.name}
                </span>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}

function ComparisonColumnSkeleton() {
  return (
    <div className="flex min-w-0 flex-1 flex-col gap-4">
      <Skeleton className="w-full rounded-[14px]" style={{ aspectRatio: "2/3" }} />
      <div className="flex flex-col gap-2">
        <Skeleton className="h-5 w-3/4" />
        <Skeleton className="h-4 w-1/3" />
      </div>
      <div className="flex flex-wrap gap-x-6 gap-y-3">
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3 w-20" />
          <Skeleton className="h-5 w-24" />
        </div>
        <div className="flex flex-col gap-1.5">
          <Skeleton className="h-3 w-16" />
          <Skeleton className="h-5 w-16" />
        </div>
      </div>
      <div className="flex flex-wrap gap-1.5">
        <Skeleton className="h-6 w-16 rounded-full" />
        <Skeleton className="h-6 w-20 rounded-full" />
      </div>
      <div className="space-y-1.5">
        <Skeleton className="h-3.5 w-full" />
        <Skeleton className="h-3.5 w-5/6" />
        <Skeleton className="h-3.5 w-4/6" />
      </div>
    </div>
  )
}

export function ComparisonDialog({
  open,
  onOpenChange,
  left,
  right,
}: ComparisonDialogProps) {
  const isMobile = useIsMobile()

  const sharedGenres = useMemo(() => {
    if (!left || !right) return new Set<string>()
    return sharedNames(left.genres, right.genres)
  }, [left, right])
  const sharedProviders = useMemo(() => {
    if (!left || !right) return new Set<string>()
    return sharedNames(
      left.watchProviders.map((p) => p.name),
      right.watchProviders.map((p) => p.name)
    )
  }, [left, right])
  const coupleWinner =
    left && right ? higherSide(left.coupleRating, right.coupleRating) : null
  const tmdbWinner =
    left && right ? higherSide(left.voteAverage, right.voteAverage) : null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="font-auth-body max-h-[90svh] w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] gap-0 overflow-y-auto rounded-[22px] border border-white/10 bg-[#161513] p-0 text-[#f6f4ec] shadow-[0_30px_80px_rgba(0,0,0,.6)] ring-0 sm:w-full sm:max-w-[880px]"
      >
        <DialogTitle className="sr-only">Comparar títulos</DialogTitle>
        <DialogDescription className="sr-only">
          {left && right
            ? `Comparação lado a lado entre ${left.title} e ${right.title}.`
            : "Carregando os detalhes dos títulos selecionados para comparação."}
        </DialogDescription>

        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-[rgba(255,255,255,.06)] bg-[#161513]/95 px-6 py-5 backdrop-blur-sm">
          <div className="min-w-0">
            <div className="text-[12px] font-semibold tracking-[.14em] text-[#ffcb2b] uppercase">
              Comparação
            </div>
            {left && right ? (
              <p className="font-display mt-1 truncate text-[19px] font-bold tracking-tight">
                {left.title} <span className="text-[#a6a39a]">vs</span>{" "}
                {right.title}
              </p>
            ) : (
              <p className="font-display mt-1 text-[19px] font-bold tracking-tight text-[#a6a39a]">
                Carregando comparação…
              </p>
            )}
          </div>
          <Button
            type="button"
            variant="outline"
            size="icon-sm"
            onClick={() => onOpenChange(false)}
            aria-label="Fechar comparação"
            className="mt-0.5 flex-none rounded-[10px] border-white/10 text-[#a6a39a] hover:bg-white/[0.06] hover:text-white"
          >
            <X className="size-4.5" />
          </Button>
        </div>

        <div
          className={cn(
            "relative flex gap-8 p-6",
            isMobile ? "flex-col" : "flex-row"
          )}
        >
          {left ? (
            <ComparisonColumn
              item={left}
              side="left"
              sharedGenres={sharedGenres}
              sharedProviders={sharedProviders}
              coupleWinner={coupleWinner}
              tmdbWinner={tmdbWinner}
            />
          ) : (
            <ComparisonColumnSkeleton />
          )}

          {isMobile ? (
            <div className="relative flex items-center gap-3 py-1">
              <div className="h-px flex-1 bg-white/10" />
              <span className="grid size-8 flex-none place-items-center rounded-full border-2 border-[#161513] bg-[#ffcb2b] text-[11px] font-black text-[#111]">
                VS
              </span>
              <div className="h-px flex-1 bg-white/10" />
            </div>
          ) : (
            <div className="relative w-px flex-none self-stretch bg-white/10">
              <span className="absolute top-1/2 left-1/2 grid size-9 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full border-2 border-[#161513] bg-[#ffcb2b] text-[11px] font-black text-[#111] shadow-[0_4px_14px_rgba(255,203,43,.4)]">
                VS
              </span>
            </div>
          )}

          {right ? (
            <ComparisonColumn
              item={right}
              side="right"
              sharedGenres={sharedGenres}
              sharedProviders={sharedProviders}
              coupleWinner={coupleWinner}
              tmdbWinner={tmdbWinner}
            />
          ) : (
            <ComparisonColumnSkeleton />
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
