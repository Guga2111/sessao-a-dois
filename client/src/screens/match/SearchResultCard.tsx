import { Heart, Loader2, Sparkles } from "lucide-react"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import type { MediaSearchResult } from "@/types/media"

import { TYPE_LABEL, type LikeState } from "./helpers"

export function SearchResultCard({
  result,
  alreadyTracked,
  likeState,
  compareMode,
  isCompareSelected,
  compareOrder,
  onToggleCompare,
  onLike,
}: {
  result: MediaSearchResult
  alreadyTracked: boolean
  likeState: LikeState
  compareMode: boolean
  isCompareSelected: boolean
  compareOrder: number | null
  onToggleCompare: () => void
  onLike: () => void
}) {
  const hue = result.tmdbId % 360

  return (
    <div
      onClick={() => compareMode && onToggleCompare()}
      aria-pressed={compareMode ? isCompareSelected : undefined}
      className={cn(
        "overflow-hidden rounded-[18px] border bg-card transition-colors",
        compareMode
          ? cn(
              "cursor-pointer",
              isCompareSelected
                ? "border-2 border-primary shadow-[var(--shadow-glow-primary-3)]"
                : "border-dashed border-white/20 hover:border-white/35"
            )
          : "border-white/7"
      )}
    >
      <div
        className="relative aspect-[3/4]"
        style={{
          background: `linear-gradient(160deg, hsl(${hue} 42% 24%), hsl(${hue} 46% 11%))`,
        }}
      >
        {result.posterUrl ? (
          <img
            src={result.posterUrl}
            alt={result.title}
            className="absolute inset-0 h-full w-full object-cover"
          />
        ) : (
          <div
            className="absolute inset-0"
            style={{
              backgroundImage:
                "repeating-linear-gradient(135deg, rgba(255,255,255,.05) 0 8px, transparent 8px 16px)",
            }}
          />
        )}
        <div className="absolute top-2.5 left-2.5 rounded-lg bg-background/60 px-2.5 py-1 text-[11px] font-semibold text-foreground backdrop-blur-md">
          {TYPE_LABEL[result.mediaType]}
        </div>
        {compareMode ? (
          isCompareSelected && (
            <div className="absolute top-2.5 right-2.5 grid size-6 flex-none place-items-center rounded-full border-2 border-card bg-primary text-[12px] font-black text-on-primary">
              {compareOrder}
            </div>
          )
        ) : (
          result.voteAverage != null && (
            <div className="absolute top-2.5 right-2.5 flex items-center gap-1.5 rounded-lg bg-background/60 px-2.5 py-1 text-[11px] font-semibold text-foreground backdrop-blur-md">
              {/* color-ok: #01b47f e o verde de marca oficial do TMDB (identidade externa da fonte da nota, nao token do nosso design system) */}
              <span className="size-1.5 rounded-full bg-[#01b47f]" />
              {result.voteAverage.toFixed(1)}
            </div>
          )
        )}
      </div>

      <div className="p-4">
        <div className="flex items-baseline justify-between gap-2">
          <div className="truncate text-[15px] font-bold leading-tight">
            {result.title}
          </div>
          {result.year && (
            <span className="flex-none text-[13px] text-muted-foreground">
              {result.year}
            </span>
          )}
        </div>

        {result.overview && (
          <p className="mt-2 line-clamp-2 text-[12.5px] leading-snug text-muted-foreground">
            {result.overview}
          </p>
        )}

        {!compareMode && (
          <Button
            type="button"
            variant="ghost"
            onClick={(event) => {
              event.stopPropagation()
              onLike()
            }}
            disabled={alreadyTracked || likeState === "loading"}
            className={cn(
              "mt-3.5 flex w-full cursor-pointer items-center justify-center gap-2 rounded-[10px] border px-3 py-2.5 text-[13px] font-semibold transition-colors",
              likeState === "matched" &&
                "border-primary/50 bg-primary/16 text-accent-strong",
              likeState === "liked" &&
                "border-success/35 bg-success/10 text-success-foreground",
              likeState === "error" &&
                "border-destructive/35 bg-destructive/10 text-destructive-foreground",
              (likeState === "idle" || likeState === "loading") &&
                "border-white/12 bg-transparent text-foreground hover:bg-white/[0.06]"
            )}
          >
            {likeState === "loading" && (
              <>
                <Loader2 className="size-4 animate-spin" /> Curtindo...
              </>
            )}
            {likeState === "idle" && (
              <>
                <Heart className="size-4" /> Curtir
              </>
            )}
            {likeState === "matched" && (
              <>
                <Sparkles className="size-4" /> E um match!
              </>
            )}
            {likeState === "liked" && alreadyTracked && (
              <>
                <Heart className="size-4 fill-current" /> Ja na lista
              </>
            )}
            {likeState === "liked" && !alreadyTracked && (
              <>
                <Heart className="size-4 fill-current" /> Curtido
              </>
            )}
            {likeState === "error" && "Tente novamente"}
          </Button>
        )}
      </div>
    </div>
  )
}
