import { useCallback, useEffect, useRef, useState, type ReactNode } from "react"

import {
  ChevronDown,
  ChevronUp,
  Columns2,
  Heart,
  Loader2,
  RefreshCw,
  Search,
  SlidersHorizontal,
  Sparkles,
  TriangleAlert,
  X,
} from "lucide-react"

import { ComparisonDialog, type ComparisonItem } from "@/components/ComparisonDialog"
import { Header } from "@/components/Header"
import { PendingDetailModal } from "@/components/PendingDetailModal"
import { SearchResultSkeleton } from "@/components/skeletons/SearchResultSkeleton"
import { Button } from "@/components/ui/button"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  Slider,
  SliderControl,
  SliderIndicator,
  SliderThumb,
  SliderTrack,
} from "@/components/ui/slider"
import { Skeleton } from "@/components/ui/skeleton"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { api } from "@/lib/api"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { cn } from "@/lib/utils"
import { useMatchStore } from "@/stores/useMatchStore"
import type {
  MediaDetails,
  MediaGenre,
  MediaPage,
  MediaSearchResult,
  PendingMatch,
} from "@/types/media"
import type { MediaType, TrackKeyResponse } from "@/types/tracking"

type LikeState = "idle" | "loading" | "liked" | "matched" | "error"
type ActiveTab = "suggestions" | "search"

const SORT_OPTIONS = [
  { value: "popularity.desc", label: "Popularidade ↓" },
  { value: "popularity.asc", label: "Popularidade ↑" },
  { value: "vote_average.desc", label: "Avaliacao ↓" },
  { value: "vote_average.asc", label: "Avaliacao ↑" },
  { value: "release_date.desc", label: "Data de Lancamento ↓" },
  { value: "release_date.asc", label: "Data de Lancamento ↑" },
] as const

const TYPE_LABEL: Record<MediaSearchResult["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Serie",
}

const DECADE_OPTIONS = [
  { value: "2020", label: "Anos 2020" },
  { value: "2010", label: "Anos 2010" },
  { value: "2000", label: "Anos 2000" },
  { value: "1990", label: "Anos 1990" },
  { value: "1900", label: "Anterior" },
] as const

const CERTIFICATION_OPTIONS = [
  { value: "Livre", label: "Livre" },
  { value: "10", label: "10" },
  { value: "12", label: "12" },
  { value: "14", label: "14" },
  { value: "16", label: "16" },
  { value: "18", label: "18" },
] as const

const DEFAULT_VOTE_RANGE: [number, number] = [0, 10]
const DEFAULT_RUNTIME_RANGE: [number, number] = [0, 240]

function formatVoteRangeLabel([min, max]: [number, number]): string {
  const minLabel = min.toFixed(1).replace(".", ",")
  const maxLabel = max === 10 ? "10" : max.toFixed(1).replace(".", ",")
  return `${minLabel} - ${maxLabel}`
}

function formatMinutes(minutes: number): string {
  if (minutes === 0) return "0 min"
  if (minutes % 60 === 0) return `${minutes / 60}h`
  if (minutes > 60) return `${Math.floor(minutes / 60)}h${minutes % 60}`
  return `${minutes} min`
}

function formatRuntimeRangeLabel([min, max]: [number, number]): string {
  if (min === 0) return `Ate ${formatMinutes(max)}`
  return `${formatMinutes(min)} - ${formatMinutes(max)}`
}

function trackKey(mediaType: string, tmdbId: number): string {
  return `${mediaType}-${tmdbId}`
}

function formatResultsCount(total: number): string {
  return `${total} ${total === 1 ? "titulo" : "titulos"}`
}

const TMDB_MAX_PAGE = 500

function paginationRange(
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

const COMPARE_TOOLTIP =
  "Selecione 2 títulos para comparar informações como notas, gêneros e onde assistir."

function keyOfCompareItem(item: { mediaType: MediaType; tmdbId: number }): string {
  return trackKey(item.mediaType, item.tmdbId)
}

function buildComparisonItemFromDetails(
  mediaType: MediaType,
  tmdbId: number
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
      }
    })
}

function buildComparisonItemFromPending(item: PendingMatch): Promise<ComparisonItem> {
  return buildComparisonItemFromDetails(item.mediaType, item.tmdbId)
}

function buildComparisonItemFromSearchResult(
  result: MediaSearchResult
): Promise<ComparisonItem> {
  return api
    .get<MediaDetails>(
      `/api/media/${result.mediaType.toLowerCase()}/${result.tmdbId}`
    )
    .then((response) => ({
      tmdbId: result.tmdbId,
      mediaType: result.mediaType,
      title: result.title,
      year: result.year,
      posterUrl: result.posterUrl,
      overview: result.overview,
      voteAverage: result.voteAverage,
      coupleRating: null,
      genres: response.data.genres,
      watchProviders: response.data.watchProviders,
    }))
}

/** Selection-mode state for the "compare 2 titles" flow, shared by SearchTab
 *  and SuggestionsTab. Each tab keeps its own instance so switching tabs
 *  (which unmounts the previous tab component entirely) resets it for free,
 *  same effect as the key-remount pattern without needing an explicit key. */
function useCompareSelection<T>(
  buildItem: (item: T) => Promise<ComparisonItem>,
  keyOf: (item: T) => string
) {
  const [compareMode, setCompareModeState] = useState(false)
  const [selected, setSelected] = useState<T[]>([])
  const [left, setLeft] = useState<ComparisonItem | null>(null)
  const [right, setRight] = useState<ComparisonItem | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const enterCompareMode = useCallback(() => setCompareModeState(true), [])

  const exitCompareMode = useCallback(() => {
    setCompareModeState(false)
    setSelected([])
    setLeft(null)
    setRight(null)
    setLoading(false)
    setError(null)
  }, [])

  const clearSelection = useCallback(() => {
    setSelected([])
    setLeft(null)
    setRight(null)
    setLoading(false)
    setError(null)
  }, [])

  const retry = useCallback(() => {
    setSelected((prev) => [...prev])
  }, [])

  const toggle = useCallback(
    (item: T) => {
      setSelected((prev) => {
        const key = keyOf(item)
        if (prev.some((p) => keyOf(p) === key)) {
          return prev.filter((p) => keyOf(p) !== key)
        }
        if (prev.length >= 2) return prev
        return [...prev, item]
      })
    },
    [keyOf]
  )

  useEffect(() => {
    if (selected.length !== 2) return
    let cancelled = false
    const timer = setTimeout(() => {
      if (cancelled) return
      setLoading(true)
      setError(null)
      Promise.all(selected.map(buildItem))
        .then(([nextLeft, nextRight]) => {
          if (cancelled) return
          setLeft(nextLeft)
          setRight(nextRight)
          setLoading(false)
        })
        .catch(() => {
          if (cancelled) return
          setError(
            "Não foi possível carregar os detalhes para comparação. Tente novamente."
          )
          setLoading(false)
        })
    }, 0)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [selected, buildItem])

  useEffect(() => {
    if (!compareMode) return
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") exitCompareMode()
    }
    window.addEventListener("keydown", handleKey)
    return () => window.removeEventListener("keydown", handleKey)
  }, [compareMode, exitCompareMode])

  return {
    compareMode,
    enterCompareMode,
    exitCompareMode,
    clearSelection,
    retry,
    toggle,
    selected,
    left,
    right,
    error,
    dialogOpen: loading || (left !== null && right !== null),
  }
}

function CompareToggleButton({
  active,
  onToggle,
}: {
  active: boolean
  onToggle: () => void
}) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger
          render={
            <Button
              type="button"
              variant="outline"
              onClick={onToggle}
              aria-label={active ? "Cancelar comparação" : "Comparar títulos"}
              className={cn(
                "inline-flex cursor-pointer items-center gap-2 rounded-full border px-4 py-2.5 text-[13px] font-semibold transition-colors",
                active
                  ? "border-[rgba(255,203,43,.5)] bg-[rgba(255,203,43,.12)] text-[#ffcb2b] hover:bg-[rgba(255,203,43,.18)] hover:text-[#ffcb2b]"
                  : "border-white/10 bg-[#161513] text-[#f6f4ec] hover:bg-white/[0.06]"
              )}
            />
          }
        >
          {active ? <X className="size-4" /> : <Columns2 className="size-4" />}
          {active ? "Cancelar" : "Comparar"}
        </TooltipTrigger>
        <TooltipContent className="max-w-[240px] rounded-lg border border-[rgba(255,255,255,.1)] bg-[#201e18] px-3 py-2 text-[#f6f4ec] shadow-xl">
          {COMPARE_TOOLTIP}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

function CompareSelectionChip({
  count,
  onClear,
  error,
  onRetry,
}: {
  count: number
  onClear: () => void
  error: string | null
  onRetry: () => void
}) {
  return (
    <div className="fixed inset-x-0 bottom-8 z-[35] flex justify-center px-4">
      <div className="flex max-w-[calc(100vw-32px)] flex-col items-center gap-2.5 rounded-2xl border border-white/10 bg-[#161513]/95 px-5 py-3 shadow-[0_20px_50px_rgba(0,0,0,.5)] backdrop-blur-md">
        <div className="flex items-center gap-3">
          <span className="text-[13.5px] font-semibold text-[#f6f4ec]">
            {count}/2 selecionados
          </span>
          <button
            type="button"
            onClick={onClear}
            aria-label="Limpar seleção"
            className="grid size-6 cursor-pointer place-items-center rounded-full bg-white/[0.08] text-[#a6a39a] transition hover:bg-white/[0.14] hover:text-[#f6f4ec]"
          >
            <X className="size-3.5" />
          </button>
        </div>
        {error && (
          <div className="flex items-center gap-2.5 text-[12.5px] text-[#ffb3b3]">
            <span>{error}</span>
            <button
              type="button"
              onClick={onRetry}
              className="cursor-pointer font-semibold text-[#ffcb2b] hover:text-[#ffe08a]"
            >
              Tentar novamente
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function SuggestionsTab() {
  const { pendingQueue, pendingLoading, fetchPending, removePending } =
    useMatchStore()
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [lastAction, setLastAction] = useState<"like" | "reject" | null>(null)
  const [detailItem, setDetailItem] = useState<PendingMatch | null>(null)
  const showPendingSkeleton = useDelayedLoading(pendingLoading)
  const compare = useCompareSelection<PendingMatch>(
    buildComparisonItemFromPending,
    keyOfCompareItem
  )

  useEffect(() => {
    fetchPending()
  }, [fetchPending])

  const current = pendingQueue[0] ?? null

  const handleReject = async () => {
    if (!current || actionLoading) return
    setActionLoading(true)
    setLastAction("reject")
    try {
      await api.post("/api/match/reject", {
        tmdbId: current.tmdbId,
        mediaType: current.mediaType,
      })
      removePending(current.tmdbId)
      setDetailItem(null)
      setActionError(null)
    } catch {
      setActionError("Nao foi possivel registrar. Tentem novamente.")
    } finally {
      setActionLoading(false)
    }
  }

  const handleLike = async () => {
    if (!current || actionLoading) return
    setActionLoading(true)
    setLastAction("like")
    try {
      await api.post("/api/match/like", {
        tmdbId: current.tmdbId,
        mediaType: current.mediaType,
        title: current.title,
        posterUrl: current.posterUrl,
        releaseYear: current.releaseYear,
      })
      removePending(current.tmdbId)
      setDetailItem(null)
      setActionError(null)
    } catch {
      setActionError("Nao foi possivel registrar. Tentem novamente.")
    } finally {
      setActionLoading(false)
    }
  }

  const handleActionRetry = () => {
    if (lastAction === "like") handleLike()
    else if (lastAction === "reject") handleReject()
  }

  if (showPendingSkeleton) {
    return (
      <div className="flex flex-col items-center">
        <div className="w-full max-w-[calc(100vw-32px)] sm:max-w-[280px]">
          <div className="overflow-hidden rounded-[22px] border border-[rgba(255,255,255,.07)] bg-[#161513]">
            <Skeleton className="aspect-[2/3] rounded-none" />
          </div>

          <div className="mt-6 flex items-center justify-center gap-6">
            <Skeleton className="size-14 rounded-full" />
            <Skeleton className="size-14 rounded-full" />
          </div>

          <div className="mt-4 flex justify-center">
            <Skeleton className="h-3.5 w-32" />
          </div>
        </div>
      </div>
    )
  }

  const hue = current?.tmdbId ? current.tmdbId % 360 : 0

  const compareToggleButton: ReactNode = (
    <CompareToggleButton
      active={compare.compareMode}
      onToggle={() =>
        compare.compareMode ? compare.exitCompareMode() : compare.enterCompareMode()
      }
    />
  )

  return (
    <>
      <div className="mb-6 flex items-center justify-between gap-3">
        <p className="text-[13px] text-[#a6a39a]">
          {pendingQueue.length > 0
            ? `${pendingQueue.length} ${
                pendingQueue.length === 1 ? "sugestão pendente" : "sugestões pendentes"
              }`
            : "Nenhuma sugestão pendente"}
        </p>
        {compareToggleButton}
      </div>

      {compare.compareMode ? (
        pendingQueue.length === 0 ? (
          <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
            Nenhuma sugestão pendente para comparar.
          </div>
        ) : (
          <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-4">
            {pendingQueue.map((item) => {
              const key = keyOfCompareItem(item)
              const isSelected = compare.selected.some(
                (selectedItem) => keyOfCompareItem(selectedItem) === key
              )
              const order =
                compare.selected.findIndex(
                  (selectedItem) => keyOfCompareItem(selectedItem) === key
                ) + 1 || null
              const itemHue = item.tmdbId % 360
              return (
                <div
                  key={key}
                  onClick={() => compare.toggle(item)}
                  aria-pressed={isSelected}
                  className={cn(
                    "cursor-pointer overflow-hidden rounded-[16px] border bg-[#161513] transition-colors",
                    isSelected
                      ? "border-2 border-[#ffcb2b] shadow-[0_0_24px_rgba(255,203,43,.18)]"
                      : "border-dashed border-white/20 hover:border-white/35"
                  )}
                >
                  <div
                    className="relative aspect-[2/3]"
                    style={{
                      background: `linear-gradient(160deg, hsl(${itemHue} 42% 24%), hsl(${itemHue} 46% 11%))`,
                    }}
                  >
                    {item.posterUrl ? (
                      <img
                        src={item.posterUrl}
                        alt={item.title}
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
                    <div className="absolute top-2 left-2 rounded-lg bg-[rgba(9,9,10,.6)] px-2 py-0.5 text-[10px] font-semibold text-[#f6f4ec] backdrop-blur-md">
                      {TYPE_LABEL[item.mediaType]}
                    </div>
                    {isSelected && (
                      <div className="absolute top-2 right-2 grid size-6 flex-none place-items-center rounded-full border-2 border-[#161513] bg-[#ffcb2b] text-[12px] font-black text-[#111]">
                        {order}
                      </div>
                    )}
                  </div>
                  <div className="truncate p-2.5 text-[12.5px] font-semibold text-[#f6f4ec]">
                    {item.title}
                  </div>
                </div>
              )
            })}
          </div>
        )
      ) : !current ? (
        <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
          Nenhuma sugestao pendente. Voltem a buscar titulos na aba "Buscar".
        </div>
      ) : (
        <div className="flex flex-col items-center">
          <div className="w-full max-w-[calc(100vw-32px)] sm:max-w-[280px]">
            <div
              className="cursor-pointer overflow-hidden rounded-[22px] border border-[rgba(255,255,255,.07)] bg-[#161513] transition-shadow hover:shadow-[0_0_0_2px_rgba(255,203,43,.25)]"
              onClick={() => setDetailItem(current)}
            >
              <div
                className="relative aspect-[2/3]"
                style={{
                  background: `linear-gradient(160deg, hsl(${hue} 42% 24%), hsl(${hue} 46% 11%))`,
                }}
              >
                {current.posterUrl ? (
                  <img
                    src={current.posterUrl}
                    alt={current.title}
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
                <div className="absolute top-3 left-3 rounded-lg bg-[rgba(9,9,10,.6)] px-2.5 py-1 text-[11px] font-semibold text-[#f6f4ec] backdrop-blur-md">
                  {TYPE_LABEL[current.mediaType]}
                </div>
                <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-[#09090a] via-[rgba(9,9,10,.85)] to-transparent px-5 pt-16 pb-5">
                  <h2 className="font-display text-[22px] font-bold leading-tight tracking-tight">
                    {current.title}
                  </h2>
                </div>
              </div>
            </div>

            <div className="mt-6 flex items-center justify-center gap-6">
              <Button
                type="button"
                variant="destructive"
                size="icon-lg"
                onClick={handleReject}
                disabled={actionLoading}
                className="size-14 rounded-full border border-[rgba(255,107,107,.3)] bg-[rgba(255,107,107,.08)] text-[#ff6b6b] hover:bg-[rgba(255,107,107,.16)]"
              >
                <X className="size-6" strokeWidth={2.5} />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon-lg"
                onClick={handleLike}
                disabled={actionLoading}
                className="size-14 rounded-full border border-[rgba(61,220,151,.3)] bg-[rgba(61,220,151,.08)] text-[#3ddc97] hover:bg-[rgba(61,220,151,.16)]"
              >
                <Heart className="size-6" strokeWidth={2.5} />
              </Button>
            </div>

            <p className="mt-4 text-center text-[13px] text-[#a6a39a]">
              1 de {pendingQueue.length}{" "}
              {pendingQueue.length === 1 ? "sugestao" : "sugestoes"}
            </p>

            {actionError && !detailItem && (
              <div className="mx-auto mt-4 flex max-w-[320px] flex-col items-center gap-2 rounded-2xl border border-[rgba(255,107,107,.35)] bg-[rgba(255,107,107,.08)] px-5 py-3 text-center">
                <p className="flex items-center gap-2 text-[13px] text-[#ffb3b3]">
                  <TriangleAlert className="size-4" />
                  {actionError}
                </p>
                <Button
                  type="button"
                  onClick={handleActionRetry}
                  disabled={actionLoading}
                  className="flex items-center gap-2 rounded-full border border-[rgba(255,107,107,.4)] bg-transparent px-4 py-1.5 text-[13px] font-semibold text-[#ffb3b3] hover:bg-[rgba(255,107,107,.12)]"
                >
                  <RefreshCw className="size-3.5" /> Tentar novamente
                </Button>
              </div>
            )}
          </div>
        </div>
      )}

      <PendingDetailModal
        item={detailItem}
        onClose={() => setDetailItem(null)}
        onLike={handleLike}
        onReject={handleReject}
        actionLoading={actionLoading}
        actionError={actionError}
        onRetryAction={handleActionRetry}
      />

      <ComparisonDialog
        open={compare.dialogOpen}
        onOpenChange={(open) => {
          if (!open) compare.exitCompareMode()
        }}
        left={compare.left}
        right={compare.right}
      />
      {compare.compareMode && (
        <CompareSelectionChip
          count={compare.selected.length}
          onClear={compare.clearSelection}
          error={compare.error}
          onRetry={compare.retry}
        />
      )}
    </>
  )
}

function SearchTab() {
  const [query, setQuery] = useState("")
  const [results, setResults] = useState<MediaSearchResult[]>([])
  const [searching, setSearching] = useState(false)
  const [searched, setSearched] = useState(false)
  const [trackedKeys, setTrackedKeys] = useState<Set<string>>(new Set())
  const [likeStates, setLikeStates] = useState<Record<string, LikeState>>({})
  const [sortBy, setSortBy] = useState<string>(SORT_OPTIONS[0].value)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [genres, setGenres] = useState<MediaGenre[]>([])
  const [selectedDecades, setSelectedDecades] = useState<string[]>([])
  const [selectedCertifications, setSelectedCertifications] = useState<
    string[]
  >([])
  const [selectedGenres, setSelectedGenres] = useState<string[]>([])
  const [voteRange, setVoteRange] = useState<[number, number]>(
    DEFAULT_VOTE_RANGE
  )
  const [runtimeRange, setRuntimeRange] = useState<[number, number]>(
    DEFAULT_RUNTIME_RANGE
  )
  const [totalResults, setTotalResults] = useState(0)
  const [resultsContext, setResultsContext] = useState<
    "trending" | "search" | "discover"
  >("trending")
  const [fetchError, setFetchError] = useState(false)
  const [page, setPage] = useState(1)
  const [totalPages, setTotalPages] = useState(1)
  type FetchAttempt = {
    url: string
    params: Record<string, string | number>
    context: "trending" | "search" | "discover"
  }
  const lastFetchRef = useRef<FetchAttempt | null>(null)
  const lastAttemptRef = useRef<FetchAttempt | null>(null)
  const showSearchSkeleton = useDelayedLoading(searching)
  const compare = useCompareSelection<MediaSearchResult>(
    buildComparisonItemFromSearchResult,
    keyOfCompareItem
  )
  const activeFilterCount =
    selectedDecades.length +
    selectedCertifications.length +
    selectedGenres.length +
    (voteRange[0] !== DEFAULT_VOTE_RANGE[0] ||
    voteRange[1] !== DEFAULT_VOTE_RANGE[1]
      ? 1
      : 0) +
    (runtimeRange[0] !== DEFAULT_RUNTIME_RANGE[0] ||
    runtimeRange[1] !== DEFAULT_RUNTIME_RANGE[1]
      ? 1
      : 0)
  const hasQuery = query.trim().length > 0

  const runFetch = useCallback(
    (
      url: string,
      params: Record<string, string | number>,
      context: "trending" | "search" | "discover",
      isCancelled: () => boolean = () => false
    ) => {
      setSearching(true)
      lastAttemptRef.current = { url, params, context }
      return api
        .get<MediaPage>(url, { params })
        .then((response) => {
          if (isCancelled()) return
          setResults(response.data.results)
          setTotalResults(response.data.totalResults)
          setTotalPages(response.data.totalPages)
          setPage(response.data.page)
          setResultsContext(context)
          setSearched(true)
          setFetchError(false)
          const paramsWithoutPage = { ...params }
          delete paramsWithoutPage.page
          lastFetchRef.current = { url, params: paramsWithoutPage, context }
        })
        .catch(() => {
          if (isCancelled()) return
          setResults([])
          setTotalResults(0)
          setTotalPages(1)
          setResultsContext(context)
          setSearched(true)
          setFetchError(true)
        })
        .finally(() => {
          if (!isCancelled()) setSearching(false)
        })
    },
    []
  )

  const handleRetry = () => {
    const attempt = lastAttemptRef.current
    if (!attempt || searching) return
    runFetch(attempt.url, attempt.params, attempt.context)
  }

  useEffect(() => {
    api
      .get<MediaGenre[]>("/api/media/genres")
      .then((response) => setGenres(response.data))
      .catch((err) => {
        console.error("Failed to load media genres", { err })
      })
  }, [])

  useEffect(() => {
    api
      .get<TrackKeyResponse[]>("/api/tracking/keys")
      .then((response) => {
        setTrackedKeys(
          new Set(response.data.map((t) => trackKey(t.mediaType, t.tmdbId)))
        )
      })
      .catch((err) => {
        console.error("Failed to load tracking keys", { err })
      })
  }, [])

  useEffect(() => {
    let cancelled = false
    const trimmed = query.trim()
    const timer = setTimeout(
      () => {
        if (trimmed) {
          runFetch(
            "/api/media/search",
            { q: trimmed, page: 1 },
            "search",
            () => cancelled
          )
        } else {
          runFetch("/api/media/trending", { page: 1 }, "trending", () => cancelled)
        }
      },
      trimmed ? 400 : 0
    )

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [query, runFetch])

  const handleSortChange = (nextSortBy: string | null) => {
    if (!nextSortBy) return
    setSortBy(nextSortBy)
    if (hasQuery) return

    runFetch("/api/media/discover", { sortBy: nextSortBy, page: 1 }, "discover")
  }

  const handleApplyFilters = () => {
    if (hasQuery) return

    const params: Record<string, string | number> = { sortBy }
    if (selectedDecades.length) {
      params.releaseDecades = selectedDecades.join(",")
    }
    if (selectedCertifications.length) {
      params.certifications = selectedCertifications.join(",")
    }
    if (selectedGenres.length) {
      params.genres = selectedGenres.join(",")
    }
    if (voteRange[0] !== DEFAULT_VOTE_RANGE[0]) {
      params.voteAverageMin = voteRange[0]
    }
    if (voteRange[1] !== DEFAULT_VOTE_RANGE[1]) {
      params.voteAverageMax = voteRange[1]
    }
    if (runtimeRange[0] !== DEFAULT_RUNTIME_RANGE[0]) {
      params.runtimeMin = runtimeRange[0]
    }
    if (runtimeRange[1] !== DEFAULT_RUNTIME_RANGE[1]) {
      params.runtimeMax = runtimeRange[1]
    }

    runFetch("/api/media/discover", { ...params, page: 1 }, "discover")
  }

  const handlePageChange = (nextPage: number) => {
    const last = lastFetchRef.current
    const clamped = Math.min(nextPage, totalPages, TMDB_MAX_PAGE)
    if (!last || searching || clamped === page || clamped < 1) return

    runFetch(last.url, { ...last.params, page: clamped }, last.context)
  }

  const handleClearFilters = () => {
    setSelectedDecades([])
    setSelectedCertifications([])
    setSelectedGenres([])
    setVoteRange(DEFAULT_VOTE_RANGE)
    setRuntimeRange(DEFAULT_RUNTIME_RANGE)
  }

  const handleLike = async (result: MediaSearchResult) => {
    const key = trackKey(result.mediaType, result.tmdbId)
    if (trackedKeys.has(key) || likeStates[key] === "loading") return

    setLikeStates((s) => ({ ...s, [key]: "loading" }))
    try {
      const response = await api.post<{ matched: boolean }>(
        "/api/match/like",
        {
          tmdbId: result.tmdbId,
          mediaType: result.mediaType,
          title: result.title,
          posterUrl: result.posterUrl,
          releaseYear: result.year,
        }
      )
      setLikeStates((s) => ({
        ...s,
        [key]: response.data.matched ? "matched" : "liked",
      }))
      if (response.data.matched) {
        setTrackedKeys((s) => new Set(s).add(key))
      }
    } catch {
      setLikeStates((s) => ({ ...s, [key]: "error" }))
    }
  }

  return (
    <>
      <Collapsible
        open={filtersOpen}
        onOpenChange={setFiltersOpen}
        className="mx-auto mb-8 max-w-[820px]"
      >
        <div className="flex flex-wrap items-center gap-3">
          <div className="relative w-full sm:flex-1">
            <Search className="pointer-events-none absolute top-1/2 left-4 size-4.5 -translate-y-1/2 text-[#a6a39a]" />
            <input
              value={query}
              onChange={(event) => {
                const value = event.target.value
                setQuery(value)
                if (!value.trim()) {
                  setResults([])
                  setSearched(false)
                }
              }}
              placeholder="Ex.: Coracao de Vidro, Fronteira Norte..."
              className="w-full rounded-2xl border border-white/10 bg-[#161513] py-3.5 pr-4 pl-11 text-sm text-[#f6f4ec] outline-none transition-shadow focus:border-[#ffcb2b] focus:shadow-[0_0_0_3px_rgba(255,203,43,.2)]"
            />
          </div>

          <div className="ml-auto flex items-center gap-3">
            <Select
              items={SORT_OPTIONS}
              value={sortBy}
              onValueChange={handleSortChange}
              disabled={hasQuery}
            >
              <SelectTrigger className="rounded-full border-white/10 bg-[#161513] px-4 py-2.5 text-[13px] font-semibold text-[#f6f4ec] hover:bg-white/[0.06] disabled:cursor-not-allowed disabled:opacity-40 data-[popup-open]:bg-white/[0.06]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent className="border border-white/10 bg-[#161513] text-[#f6f4ec]">
                {SORT_OPTIONS.map((option) => (
                  <SelectItem
                    key={option.value}
                    value={option.value}
                    className="data-highlighted:bg-white/[0.08]"
                  >
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <CollapsibleTrigger
              disabled={hasQuery}
              className={cn(
                "flex cursor-pointer items-center gap-2 rounded-full border px-4 py-2.5 text-[13px] font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40",
                filtersOpen || activeFilterCount > 0
                  ? "border-[rgba(255,203,43,.5)] bg-[rgba(255,203,43,.12)] text-[#ffcb2b]"
                  : "border-white/10 bg-[#161513] text-[#f6f4ec] hover:bg-white/[0.06]"
              )}
            >
              <SlidersHorizontal className="size-4" />
              Filtros
              {activeFilterCount > 0 && (
                <span className="flex size-5 items-center justify-center rounded-full bg-[#ffcb2b] text-[11px] font-bold text-[#09090a]">
                  {activeFilterCount}
                </span>
              )}
              {filtersOpen ? (
                <ChevronUp className="size-4" />
              ) : (
                <ChevronDown className="size-4" />
              )}
            </CollapsibleTrigger>

            <CompareToggleButton
              active={compare.compareMode}
              onToggle={() =>
                compare.compareMode
                  ? compare.exitCompareMode()
                  : compare.enterCompareMode()
              }
            />
          </div>
        </div>

        <CollapsibleContent className="mt-4 overflow-hidden rounded-2xl border border-white/10 bg-[#161513] px-5 py-5 data-[ending-style]:animate-out data-[starting-style]:animate-in data-[ending-style]:fade-out data-[starting-style]:fade-in">
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
            <div>
              <div className="mb-2.5 text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
                Data de lancamento
              </div>
              <ToggleGroup
                multiple
                value={selectedDecades}
                onValueChange={setSelectedDecades}
              >
                {DECADE_OPTIONS.map((option) => (
                  <ToggleGroupItem key={option.value} value={option.value}>
                    {option.label}
                  </ToggleGroupItem>
                ))}
              </ToggleGroup>
            </div>

            <div>
              <div className="mb-2.5 text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
                Classificacao indicativa
              </div>
              <ToggleGroup
                multiple
                value={selectedCertifications}
                onValueChange={setSelectedCertifications}
              >
                {CERTIFICATION_OPTIONS.map((option) => (
                  <ToggleGroupItem key={option.value} value={option.value}>
                    {option.label}
                  </ToggleGroupItem>
                ))}
              </ToggleGroup>
            </div>
          </div>

          {genres.length > 0 && (
            <div className="mt-6 border-t border-white/10 pt-5">
              <div className="mb-2.5 text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
                Generos
              </div>
              <ToggleGroup
                multiple
                value={selectedGenres}
                onValueChange={setSelectedGenres}
              >
                {genres.map((genre) => (
                  <ToggleGroupItem key={genre.id} value={String(genre.id)}>
                    {genre.name}
                  </ToggleGroupItem>
                ))}
              </ToggleGroup>
            </div>
          )}

          <div className="mt-6 grid grid-cols-1 gap-6 border-t border-white/10 pt-5 sm:grid-cols-2">
            <div>
              <div className="mb-3 flex items-center justify-between">
                <span className="text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
                  Nota TMDB
                </span>
                <span className="text-[13px] font-bold text-[#ffcb2b]">
                  {formatVoteRangeLabel(voteRange)}
                </span>
              </div>
              <Slider
                min={0}
                max={10}
                step={0.5}
                minStepsBetweenValues={1}
                value={voteRange}
                onValueChange={(v) => setVoteRange(v as [number, number])}
              >
                <SliderControl>
                  <SliderTrack>
                    <SliderIndicator />
                  </SliderTrack>
                  <SliderThumb index={0} />
                  <SliderThumb index={1} />
                </SliderControl>
              </Slider>
              <div className="mt-1.5 flex justify-between text-[11px] text-[#a6a39a]">
                <span>0</span>
                <span>10</span>
              </div>
            </div>

            <div>
              <div className="mb-3 flex items-center justify-between">
                <span className="text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
                  Duracao
                </span>
                <span className="text-[13px] font-bold text-[#ffcb2b] uppercase">
                  {formatRuntimeRangeLabel(runtimeRange)}
                </span>
              </div>
              <Slider
                min={0}
                max={240}
                step={5}
                minStepsBetweenValues={1}
                value={runtimeRange}
                onValueChange={(v) => setRuntimeRange(v as [number, number])}
              >
                <SliderControl>
                  <SliderTrack>
                    <SliderIndicator />
                  </SliderTrack>
                  <SliderThumb index={0} />
                  <SliderThumb index={1} />
                </SliderControl>
              </Slider>
              <div className="mt-1.5 flex justify-between text-[11px] text-[#a6a39a]">
                <span>0 min</span>
                <span>240 min</span>
              </div>
            </div>
          </div>

          <div className="mt-6 flex items-center justify-end gap-3 border-t border-white/10 pt-5">
            <button
              type="button"
              onClick={handleClearFilters}
              disabled={activeFilterCount === 0}
              className="text-[13px] font-semibold text-[#a6a39a] transition-colors hover:text-[#f6f4ec] disabled:cursor-not-allowed disabled:opacity-40"
            >
              Limpar tudo
            </button>
            <Button
              type="button"
              onClick={handleApplyFilters}
              disabled={hasQuery || searching}
              className="rounded-full bg-[#ffcb2b] px-5 py-2.5 text-[13px] font-bold text-[#09090a] hover:bg-[#ffdd7a] disabled:cursor-not-allowed disabled:opacity-40"
            >
              Aplicar filtros
            </Button>
          </div>
        </CollapsibleContent>
      </Collapsible>

      {searched && !fetchError && (
        <div className="mb-5 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <div>
            <h2 className="font-display text-lg font-bold text-[#f6f4ec] sm:text-xl">
              {hasQuery
                ? `Resultados para "${query.trim()}"`
                : resultsContext === "discover"
                  ? "Filtros aplicados"
                  : "Em alta esta semana"}
            </h2>
            {!hasQuery && resultsContext === "trending" && (
              <p className="mt-1 text-[13px] text-[#a6a39a]">
                Resultados dinamicos do TMDB - refine com o painel de
                filtros.
              </p>
            )}
          </div>
          <span className="flex-none text-[13px] text-[#a6a39a]">
            {formatResultsCount(totalResults)}
          </span>
        </div>
      )}

      {!searched && !showSearchSkeleton && !query.trim() && (
        <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
          Comecem digitando o nome de um filme ou serie ai em cima.
        </div>
      )}

      {showSearchSkeleton && <SearchResultSkeleton rows={6} />}

      {searched && !showSearchSkeleton && fetchError && (
        <div className="mx-auto flex max-w-[420px] flex-col items-center gap-3 rounded-2xl border border-[rgba(255,107,107,.35)] bg-[rgba(255,107,107,.08)] px-6 py-10 text-center">
          <TriangleAlert className="size-6 text-[#ffb3b3]" />
          <p className="text-sm text-[#f6f4ec]">
            {resultsContext === "trending" && !hasQuery
              ? "Nao foi possivel carregar os titulos em alta agora."
              : "Algo deu errado ao buscar no TMDB."}{" "}
            Tentem novamente em instantes.
          </p>
          <Button
            type="button"
            onClick={handleRetry}
            className="flex items-center gap-2 rounded-full border border-[rgba(255,107,107,.4)] bg-transparent px-4 py-2 text-[13px] font-semibold text-[#ffb3b3] hover:bg-[rgba(255,107,107,.12)]"
          >
            <RefreshCw className="size-4" /> Tentar novamente
          </Button>
        </div>
      )}

      {searched && !showSearchSkeleton && !fetchError && results.length === 0 && (
        <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
          {hasQuery
            ? `Nada encontrado para "${query.trim()}". Tentem outro termo.`
            : "Nada encontrado com os filtros atuais. Ajustem a ordenacao ou os filtros."}
        </div>
      )}

      <div
        className={cn(
          "grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-5.5",
          showSearchSkeleton && "hidden"
        )}
      >
        {results.map((result) => {
          const key = trackKey(result.mediaType, result.tmdbId)
          const alreadyTracked = trackedKeys.has(key)
          const likeState: LikeState =
            likeStates[key] ?? (alreadyTracked ? "liked" : "idle")
          const hue = result.tmdbId % 360
          const isCompareSelected = compare.selected.some(
            (selectedItem) => keyOfCompareItem(selectedItem) === key
          )
          const compareOrder =
            compare.selected.findIndex(
              (selectedItem) => keyOfCompareItem(selectedItem) === key
            ) + 1 || null

          return (
            <div
              key={key}
              onClick={() => compare.compareMode && compare.toggle(result)}
              aria-pressed={compare.compareMode ? isCompareSelected : undefined}
              className={cn(
                "overflow-hidden rounded-[18px] border bg-[#161513] transition-colors",
                compare.compareMode
                  ? cn(
                      "cursor-pointer",
                      isCompareSelected
                        ? "border-2 border-[#ffcb2b] shadow-[0_0_24px_rgba(255,203,43,.18)]"
                        : "border-dashed border-white/20 hover:border-white/35"
                    )
                  : "border-[rgba(255,255,255,.07)]"
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
                <div className="absolute top-2.5 left-2.5 rounded-lg bg-[rgba(9,9,10,.6)] px-2.5 py-1 text-[11px] font-semibold text-[#f6f4ec] backdrop-blur-md">
                  {TYPE_LABEL[result.mediaType]}
                </div>
                {compare.compareMode ? (
                  isCompareSelected && (
                    <div className="absolute top-2.5 right-2.5 grid size-6 flex-none place-items-center rounded-full border-2 border-[#161513] bg-[#ffcb2b] text-[12px] font-black text-[#111]">
                      {compareOrder}
                    </div>
                  )
                ) : (
                  result.voteAverage != null && (
                    <div className="absolute top-2.5 right-2.5 flex items-center gap-1.5 rounded-lg bg-[rgba(9,9,10,.6)] px-2.5 py-1 text-[11px] font-semibold text-[#f6f4ec] backdrop-blur-md">
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
                    <span className="flex-none text-[13px] text-[#a6a39a]">
                      {result.year}
                    </span>
                  )}
                </div>

                {result.overview && (
                  <p className="mt-2 line-clamp-2 text-[12.5px] leading-snug text-[#a6a39a]">
                    {result.overview}
                  </p>
                )}

                {!compare.compareMode && (
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={(event) => {
                      event.stopPropagation()
                      handleLike(result)
                    }}
                    disabled={alreadyTracked || likeState === "loading"}
                    className={cn(
                      "mt-3.5 flex w-full cursor-pointer items-center justify-center gap-2 rounded-[10px] border px-3 py-2.5 text-[13px] font-semibold transition-colors",
                      likeState === "matched" &&
                        "border-[rgba(255,203,43,.5)] bg-[rgba(255,203,43,.16)] text-[#ffdd7a]",
                      likeState === "liked" &&
                        "border-[rgba(61,220,151,.35)] bg-[rgba(61,220,151,.1)] text-[#8fe9c4]",
                      likeState === "error" &&
                        "border-[rgba(255,107,107,.35)] bg-[rgba(255,107,107,.1)] text-[#ffb3b3]",
                      (likeState === "idle" || likeState === "loading") &&
                        "border-white/12 bg-transparent text-[#f6f4ec] hover:bg-white/[0.06]"
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
        })}
      </div>

      {searched && !showSearchSkeleton && results.length > 0 && totalPages > 1 && (
        <div className="mt-8 flex flex-col items-center gap-3">
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page <= 1 || searching}
                  className="border border-white/10 bg-[#161513] text-[#f6f4ec] hover:bg-white/[0.06] disabled:pointer-events-none disabled:opacity-40"
                />
              </PaginationItem>
              {paginationRange(page, Math.min(totalPages, TMDB_MAX_PAGE)).map(
                (item, index) =>
                  item === "ellipsis" ? (
                    <PaginationItem key={`ellipsis-${index}`}>
                      <PaginationEllipsis className="text-[#a6a39a]" />
                    </PaginationItem>
                  ) : (
                    <PaginationItem key={item}>
                      <PaginationLink
                        isActive={item === page}
                        onClick={() => handlePageChange(item)}
                        disabled={searching}
                        className={cn(
                          item === page
                            ? "bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffdd7a]"
                            : "border border-white/10 bg-[#161513] text-[#f6f4ec] hover:bg-white/[0.06]",
                          "disabled:pointer-events-none disabled:opacity-40"
                        )}
                      >
                        {item}
                      </PaginationLink>
                    </PaginationItem>
                  )
              )}
              <PaginationItem>
                <PaginationNext
                  onClick={() => handlePageChange(page + 1)}
                  disabled={page >= Math.min(totalPages, TMDB_MAX_PAGE) || searching}
                  className="border border-white/10 bg-[#161513] text-[#f6f4ec] hover:bg-white/[0.06] disabled:pointer-events-none disabled:opacity-40"
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
          {page >= Math.min(totalPages, TMDB_MAX_PAGE) && (
            <p className="text-[12.5px] text-[#a6a39a]">
              Voces chegaram ao fim dos resultados.
            </p>
          )}
        </div>
      )}

      <ComparisonDialog
        open={compare.dialogOpen}
        onOpenChange={(open) => {
          if (!open) compare.exitCompareMode()
        }}
        left={compare.left}
        right={compare.right}
      />
      {compare.compareMode && (
        <CompareSelectionChip
          count={compare.selected.length}
          onClear={compare.clearSelection}
          error={compare.error}
          onRetry={compare.retry}
        />
      )}
    </>
  )
}

export function MatchScreen() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("suggestions")

  return (
    <div
      className="font-auth-body min-h-svh text-[#f6f4ec]"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), #09090a",
      }}
    >
      <Header />
      <main className="mx-auto max-w-[1240px] px-5 pt-10 pb-32 sm:px-8 sm:pt-11">
        <div className="mx-auto mb-9 max-w-[560px] text-center">
          <div className="mb-2 text-[13px] font-semibold tracking-[.14em] text-[#ff9e2c] uppercase">
            Match a Dois
          </div>
          <h1 className="font-display text-[clamp(26px,4vw,38px)] font-bold tracking-tight">
            Descubram o proximo juntos
          </h1>
          <p className="mt-2 text-[15px] text-[#a6a39a]">
            Filtrem o catalogo do TMDB e curtam. Quando os dois curtirem o
            mesmo, vira um match.
          </p>
        </div>

        <div className="mx-auto mb-10 flex w-fit gap-1 rounded-xl bg-[rgba(255,255,255,.06)] p-1">
          <Button
            type="button"
            variant="ghost"
            onClick={() => setActiveTab("search")}
            className={cn(
              "cursor-pointer rounded-lg px-4 py-2 text-[13px] font-semibold transition-colors",
              activeTab === "search"
                ? "bg-[#ffcb2b] text-[#09090a]"
                : "text-[#a6a39a] hover:text-[#f6f4ec]"
            )}
          >
            Descobrir
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => setActiveTab("suggestions")}
            className={cn(
              "cursor-pointer rounded-lg px-4 py-2 text-[13px] font-semibold transition-colors",
              activeTab === "suggestions"
                ? "bg-[#ffcb2b] text-[#09090a]"
                : "text-[#a6a39a] hover:text-[#f6f4ec]"
            )}
          >
            Sugestoes
          </Button>
        </div>

        {activeTab === "suggestions" && <SuggestionsTab />}
        {activeTab === "search" && <SearchTab />}
      </main>
    </div>
  )
}
