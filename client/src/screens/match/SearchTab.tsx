import { useEffect, useState, type ReactNode } from "react"

import { RefreshCw, TriangleAlert } from "lucide-react"

import { ComparisonDialog, type ComparisonItem } from "@/components/ComparisonDialog"
import { SearchResultSkeleton } from "@/components/skeletons/SearchResultSkeleton"
import { Button } from "@/components/ui/button"
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import { api } from "@/lib/api"
import { buildComparisonItem } from "@/lib/comparisonItem"
import { useCompareSelection } from "@/lib/useCompareSelection"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { cn } from "@/lib/utils"
import type { MediaSearchResult } from "@/types/media"
import type { TrackKeyResponse } from "@/types/tracking"

import { CompareSelectionChip } from "./compareUi"
import { FiltersPanel } from "./FiltersPanel"
import {
  formatResultsCount,
  keyOfCompareItem,
  paginationRange,
  trackKey,
  type LikeState,
} from "./helpers"
import { SearchResultCard } from "./SearchResultCard"
import { TMDB_MAX_PAGE, useDiscoverSearch } from "./useDiscoverSearch"

type SearchStatus = "idle" | "loading" | "error" | "empty" | "ready"

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

  const status: SearchStatus = showSearchSkeleton
    ? "loading"
    : !searched
      ? query.trim()
        ? "ready"
        : "idle"
      : fetchError
        ? "error"
        : results.length === 0
          ? "empty"
          : "ready"

  let statusContent: ReactNode = null
  switch (status) {
    case "idle":
      statusContent = (
        <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-muted-foreground">
          Comecem digitando o nome de um filme ou serie ai em cima.
        </div>
      )
      break
    case "loading":
      statusContent = <SearchResultSkeleton rows={6} />
      break
    case "error":
      statusContent = (
        <div className="mx-auto flex max-w-[420px] flex-col items-center gap-3 rounded-2xl border border-destructive/35 bg-destructive/8 px-6 py-10 text-center">
          <TriangleAlert className="size-6 text-destructive-foreground" />
          <p className="text-sm text-foreground">
            {resultsContext === "trending" && !hasQuery
              ? "Nao foi possivel carregar os titulos em alta agora."
              : "Algo deu errado ao buscar no TMDB."}{" "}
            Tentem novamente em instantes.
          </p>
          <Button
            type="button"
            onClick={handleRetry}
            className="flex items-center gap-2 rounded-full border border-destructive/40 bg-transparent px-4 py-2 text-[13px] font-semibold text-destructive-foreground hover:bg-destructive/12"
          >
            <RefreshCw className="size-4" /> Tentar novamente
          </Button>
        </div>
      )
      break
    case "empty":
      statusContent = (
        <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-muted-foreground">
          {hasQuery
            ? `Nada encontrado para "${query.trim()}". Tentem outro termo.`
            : "Nada encontrado com os filtros atuais. Ajustem a ordenacao ou os filtros."}
        </div>
      )
      break
    case "ready":
      statusContent = null
      break
  }

  return (
    <>
      <FiltersPanel
        filtersOpen={filtersOpen}
        setFiltersOpen={setFiltersOpen}
        query={query}
        setQuery={setQuery}
        sortBy={sortBy}
        handleSortChange={handleSortChange}
        hasQuery={hasQuery}
        activeFilterCount={activeFilterCount}
        selectedDecades={selectedDecades}
        setSelectedDecades={setSelectedDecades}
        selectedCertifications={selectedCertifications}
        setSelectedCertifications={setSelectedCertifications}
        genres={genres}
        selectedGenres={selectedGenres}
        setSelectedGenres={setSelectedGenres}
        voteRange={voteRange}
        setVoteRange={setVoteRange}
        runtimeRange={runtimeRange}
        setRuntimeRange={setRuntimeRange}
        handleClearFilters={handleClearFilters}
        handleApplyFilters={handleApplyFilters}
        searching={searching}
        compareActive={compare.compareMode}
        onCompareToggle={() =>
          compare.compareMode ? compare.exitCompareMode() : compare.enterCompareMode()
        }
      />

      {searched && !fetchError && (
        <div className="mb-5 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <div>
            <h2 className="font-display text-lg font-bold text-foreground sm:text-xl">
              {hasQuery
                ? `Resultados para "${query.trim()}"`
                : resultsContext === "discover"
                  ? "Filtros aplicados"
                  : "Em alta esta semana"}
            </h2>
            {!hasQuery && resultsContext === "trending" && (
              <p className="mt-1 text-[13px] text-muted-foreground">
                Resultados dinamicos do TMDB - refine com o painel de
                filtros.
              </p>
            )}
          </div>
          <span className="flex-none text-[13px] text-muted-foreground">
            {formatResultsCount(totalResults)}
          </span>
        </div>
      )}

      {statusContent}

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
          const isCompareSelected = compare.selected.some(
            (selectedItem) => keyOfCompareItem(selectedItem) === key
          )
          const compareOrder =
            compare.selected.findIndex(
              (selectedItem) => keyOfCompareItem(selectedItem) === key
            ) + 1 || null

          return (
            <SearchResultCard
              key={key}
              result={result}
              alreadyTracked={alreadyTracked}
              likeState={likeState}
              compareMode={compare.compareMode}
              isCompareSelected={isCompareSelected}
              compareOrder={compareOrder}
              onToggleCompare={() => compare.toggle(result)}
              onLike={() => handleLike(result)}
            />
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
                  className="border border-white/10 bg-card text-foreground hover:bg-white/[0.06] disabled:pointer-events-none disabled:opacity-40"
                />
              </PaginationItem>
              {paginationRange(page, Math.min(totalPages, TMDB_MAX_PAGE)).map(
                (item, index) =>
                  item === "ellipsis" ? (
                    <PaginationItem key={`ellipsis-${index}`}>
                      <PaginationEllipsis className="text-muted-foreground" />
                    </PaginationItem>
                  ) : (
                    <PaginationItem key={item}>
                      <PaginationLink
                        isActive={item === page}
                        onClick={() => handlePageChange(item)}
                        disabled={searching}
                        className={cn(
                          item === page
                            ? "bg-primary text-background hover:bg-accent-strong"
                            : "border border-white/10 bg-card text-foreground hover:bg-white/[0.06]",
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
                  className="border border-white/10 bg-card text-foreground hover:bg-white/[0.06] disabled:pointer-events-none disabled:opacity-40"
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
          {page >= Math.min(totalPages, TMDB_MAX_PAGE) && (
            <p className="text-[12.5px] text-muted-foreground">
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
