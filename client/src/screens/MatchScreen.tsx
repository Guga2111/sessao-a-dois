import { useEffect, useState } from "react"

import {
  ChevronDown,
  ChevronUp,
  Heart,
  Loader2,
  RefreshCw,
  Search,
  SlidersHorizontal,
  Sparkles,
  TriangleAlert,
} from "lucide-react"

import { ComparisonDialog, type ComparisonItem } from "@/components/ComparisonDialog"
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
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { api } from "@/lib/api"
import { buildComparisonItem } from "@/lib/comparisonItem"
import { useCompareSelection } from "@/lib/useCompareSelection"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { cn } from "@/lib/utils"
import type { MediaSearchResult } from "@/types/media"
import type { TrackKeyResponse } from "@/types/tracking"

import { SORT_OPTIONS, TMDB_MAX_PAGE, useDiscoverSearch } from "./match/useDiscoverSearch"
import { CompareSelectionChip, CompareToggleButton } from "./match/compareUi"
import {
  TYPE_LABEL,
  formatResultsCount,
  formatRuntimeRangeLabel,
  formatVoteRangeLabel,
  keyOfCompareItem,
  paginationRange,
} from "./match/helpers"

type LikeState = "idle" | "loading" | "liked" | "matched" | "error"

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

function trackKey(mediaType: string, tmdbId: number): string {
  return `${mediaType}-${tmdbId}`
}

function buildComparisonItemFromSearchResult(
  result: MediaSearchResult
): Promise<ComparisonItem> {
  return buildComparisonItem(result.mediaType, result.tmdbId, {
    title: result.title,
    year: result.year,
    posterUrl: result.posterUrl,
    overview: result.overview,
    voteAverage: result.voteAverage,
  })
}

export function SearchTab() {
  const search = useDiscoverSearch()
  const {
    query,
    setQuery,
    results,
    searching,
    searched,
    sortBy,
    filtersOpen,
    setFiltersOpen,
    genres,
    selectedDecades,
    setSelectedDecades,
    selectedCertifications,
    setSelectedCertifications,
    selectedGenres,
    setSelectedGenres,
    voteRange,
    setVoteRange,
    runtimeRange,
    setRuntimeRange,
    totalResults,
    resultsContext,
    fetchError,
    page,
    totalPages,
    hasQuery,
    activeFilterCount,
    handleRetry,
    handleSortChange,
    handleApplyFilters,
    handlePageChange,
    handleClearFilters,
  } = search
  const [trackedKeys, setTrackedKeys] = useState<Set<string>>(new Set())
  const [likeStates, setLikeStates] = useState<Record<string, LikeState>>({})
  const showSearchSkeleton = useDelayedLoading(searching)
  const compare = useCompareSelection<MediaSearchResult>(
    buildComparisonItemFromSearchResult,
    keyOfCompareItem
  )

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
              onChange={(event) => setQuery(event.target.value)}
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
