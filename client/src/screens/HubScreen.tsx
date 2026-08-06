import { useCallback, useEffect, useRef, useState } from "react"

import { ChevronDown, Columns2, X } from "lucide-react"

import { Header } from "@/components/Header"
import { Button } from "@/components/ui/button"
import { ComparisonDialog, type ComparisonItem } from "@/components/ComparisonDialog"
import { DeleteTrackDialog } from "@/components/DeleteTrackDialog"
import { MediaCard } from "@/components/MediaCard"
import { MediaDetailModal } from "@/components/MediaDetailModal"
import { MediaCardSkeleton } from "@/components/skeletons/MediaCardSkeleton"
import { ReviewModal } from "@/components/ReviewModal"
import { Skeleton } from "@/components/ui/skeleton"
import { TitleModal } from "@/components/TitleModal"
import { WatchModal } from "@/components/WatchModal"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { api } from "@/lib/api"
import { buildComparisonItem as buildComparisonItemBase } from "@/lib/comparisonItem"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { useIsMobile } from "@/lib/useIsMobile"
import { useAuthStore } from "@/stores/useAuthStore"
import type { MediaStatus, MediaTrackResponse, PagedMediaTrackResponse } from "@/types/tracking"

const COMPARE_TOOLTIP =
  "Selecione 2 títulos para comparar informações como notas, gêneros e onde assistir."

function buildComparisonItem(track: MediaTrackResponse): Promise<ComparisonItem> {
  const ratedReviews = track.reviews.filter(
    (review) => review.rating !== null && review.rating !== undefined
  )
  const coupleRating =
    ratedReviews.length > 0
      ? ratedReviews.reduce((sum, review) => sum + review.rating!, 0) /
        ratedReviews.length
      : null
  return buildComparisonItemBase(track.mediaType, track.tmdbId, { coupleRating })
}

interface Section {
  status: MediaStatus
  title: string
  dotColor: string
  emptyMessage: string
}

const SECTIONS: Section[] = [
  {
    status: "WATCHING",
    title: "Assistindo Atualmente",
    dotColor: "#ff9e2c",
    emptyMessage: "Nada em andamento agora. Que tal começar algo hoje à noite?",
  },
  {
    status: "WANT_TO_SEE",
    title: "Queremos Ver",
    dotColor: "#ffcb2b",
    emptyMessage: "A lista de desejos está vazia. Adicionem um título para começar.",
  },
  {
    status: "WATCHED",
    title: "Já Vimos",
    dotColor: "#3ddc97",
    emptyMessage: "Ainda não marcaram nada como visto.",
  },
]

const PAGE_SIZE = 20

interface SectionState {
  items: MediaTrackResponse[]
  page: number
  total: number
  loading: boolean
  loadingMore: boolean
}

function emptySectionState(): SectionState {
  return { items: [], page: 0, total: 0, loading: true, loadingMore: false }
}

function emptySections(): Record<MediaStatus, SectionState> {
  return {
    WATCHING: emptySectionState(),
    WANT_TO_SEE: emptySectionState(),
    WATCHED: emptySectionState(),
  }
}

interface LoadMoreSentinelProps {
  status: MediaStatus
  hasMore: boolean
  loadingMore: boolean
  onLoadMore: (status: MediaStatus) => void
}

/** Invisible sentinel appended after the last mobile carousel card; triggers
 *  `onLoadMore` via IntersectionObserver instead of a "load more" tap. Only
 *  ever mounted on mobile (see call site), so the observer never registers
 *  on desktop. Flags are read from a ref synced in an effect rather than the
 *  closure so the observer callback (created once per `status`) always sees
 *  the latest `hasMore`/`loadingMore` without needing to be recreated. */
function LoadMoreSentinel({ status, hasMore, loadingMore, onLoadMore }: LoadMoreSentinelProps) {
  const flagsRef = useRef({ hasMore, loadingMore })
  useEffect(() => {
    flagsRef.current = { hasMore, loadingMore }
  })

  const observerRef = useRef<IntersectionObserver | null>(null)
  const sentinelRef = useCallback(
    (node: HTMLDivElement | null) => {
      observerRef.current?.disconnect()
      observerRef.current = null
      if (!node) return
      const observer = new IntersectionObserver(
        (entries) => {
          const flags = flagsRef.current
          if (entries[0]?.isIntersecting && flags.hasMore && !flags.loadingMore) {
            onLoadMore(status)
          }
        },
        { threshold: 0.5 }
      )
      observer.observe(node)
      observerRef.current = observer
    },
    [status, onLoadMore]
  )

  return <div ref={sentinelRef} aria-hidden="true" className="w-px shrink-0" />
}

export function HubScreen() {
  const user = useAuthStore((state) => state.user)
  const isMobile = useIsMobile()
  const [sections, setSections] = useState<Record<MediaStatus, SectionState>>(emptySections)
  const [modalOpen, setModalOpen] = useState(false)
  const [detailTrack, setDetailTrack] = useState<MediaTrackResponse | null>(null)
  const [watchTrack, setWatchTrack] = useState<MediaTrackResponse | null>(null)
  const [deleteTrack, setDeleteTrack] = useState<MediaTrackResponse | null>(null)
  const [reviewTrack, setReviewTrack] = useState<MediaTrackResponse | null>(null)
  const [openSections, setOpenSections] = useState<Record<MediaStatus, boolean>>({
    WATCHING: true,
    WANT_TO_SEE: true,
    WATCHED: true,
  })

  const [compareMode, setCompareMode] = useState(false)
  const [selectedForCompare, setSelectedForCompare] = useState<MediaTrackResponse[]>([])
  const [compareLeft, setCompareLeft] = useState<ComparisonItem | null>(null)
  const [compareRight, setCompareRight] = useState<ComparisonItem | null>(null)
  const [compareLoading, setCompareLoading] = useState(false)
  const [compareError, setCompareError] = useState<string | null>(null)
  const compareDialogOpen = compareLoading || (compareLeft !== null && compareRight !== null)

  const exitCompareMode = useCallback(() => {
    setCompareMode(false)
    setSelectedForCompare([])
    setCompareLeft(null)
    setCompareRight(null)
    setCompareLoading(false)
    setCompareError(null)
  }, [])

  const clearCompareSelection = useCallback(() => {
    setSelectedForCompare([])
    setCompareLeft(null)
    setCompareRight(null)
    setCompareLoading(false)
    setCompareError(null)
  }, [])

  // Re-triggers the fetch effect below by giving `selectedForCompare` a new
  // array identity (same 2 tracks) — used by the "Tentar novamente" retry.
  const retryCompareFetch = useCallback(() => {
    setSelectedForCompare((prev) => [...prev])
  }, [])

  const toggleCompareSelection = useCallback((track: MediaTrackResponse) => {
    setSelectedForCompare((prev) => {
      if (prev.some((t) => t.id === track.id)) {
        return prev.filter((t) => t.id !== track.id)
      }
      if (prev.length >= 2) return prev
      return [...prev, track]
    })
  }, [])

  useEffect(() => {
    if (selectedForCompare.length !== 2) return
    let cancelled = false
    const timer = setTimeout(() => {
      if (cancelled) return
      setCompareLoading(true)
      setCompareError(null)
      Promise.all(selectedForCompare.map(buildComparisonItem))
        .then(([left, right]) => {
          if (cancelled) return
          setCompareLeft(left)
          setCompareRight(right)
          setCompareLoading(false)
        })
        .catch(() => {
          if (cancelled) return
          setCompareError(
            "Não foi possível carregar os detalhes para comparação. Tente novamente."
          )
          setCompareLoading(false)
        })
    }, 0)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [selectedForCompare])

  useEffect(() => {
    if (!compareMode) return
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") exitCompareMode()
    }
    window.addEventListener("keydown", handleKey)
    return () => window.removeEventListener("keydown", handleKey)
  }, [compareMode, exitCompareMode])

  const fetchSectionPage = useCallback((status: MediaStatus, page: number) => {
    return api
      .get<PagedMediaTrackResponse>("/api/tracking", {
        params: { status, page, size: PAGE_SIZE },
      })
      .then((response) => {
        const { content, totalElements } = response.data
        setSections((prev) => ({
          ...prev,
          [status]: {
            items: page === 0 ? content : [...prev[status].items, ...content],
            page,
            total: totalElements,
            loading: false,
            loadingMore: false,
          },
        }))
      })
      .catch(() => {
        setSections((prev) => ({
          ...prev,
          [status]: { ...prev[status], loading: false, loadingMore: false },
        }))
      })
  }, [])

  const reloadAllFirstPages = useCallback(() => {
    SECTIONS.forEach((section) => void fetchSectionPage(section.status, 0))
  }, [fetchSectionPage])

  useEffect(() => {
    reloadAllFirstPages()
  }, [reloadAllFirstPages])

  const sectionsRef = useRef(sections)
  useEffect(() => {
    sectionsRef.current = sections
  }, [sections])

  const handleLoadMore = useCallback(
    (status: MediaStatus) => {
      const current = sectionsRef.current[status]
      setSections((prev) => ({
        ...prev,
        [status]: { ...prev[status], loadingMore: true },
      }))
      void fetchSectionPage(status, current.page + 1)
    },
    [fetchSectionPage]
  )

  const handleModalSuccess = () => {
    setModalOpen(false)
    reloadAllFirstPages()
  }

  const showSectionSkeleton: Record<MediaStatus, boolean> = {
    WATCHING: useDelayedLoading(sections.WATCHING.loading),
    WANT_TO_SEE: useDelayedLoading(sections.WANT_TO_SEE.loading),
    WATCHED: useDelayedLoading(sections.WATCHED.loading),
  }

  const showLoadMoreSkeleton: Record<MediaStatus, boolean> = {
    WATCHING: useDelayedLoading(sections.WATCHING.loadingMore),
    WANT_TO_SEE: useDelayedLoading(sections.WANT_TO_SEE.loadingMore),
    WATCHED: useDelayedLoading(sections.WATCHED.loadingMore),
  }

  return (
    <div
      className="font-auth-body min-h-svh text-[#f6f4ec]"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), #09090a",
      }}
    >
      <Header />
      <main className="mx-auto max-w-[1240px] px-4 pt-10 pb-32 sm:px-8 sm:pt-11">
        <div className="mb-7 flex flex-wrap items-end justify-between gap-5">
          <div className="min-w-0">
            <div className="mb-2 text-[13px] font-semibold tracking-[.14em] text-[#ffcb2b] uppercase">
              Minha Lista
            </div>
            <h1 className="font-display text-[clamp(28px,4vw,40px)] font-bold tracking-tight">
              O que estamos vendo
            </h1>
            <p className="mt-2 text-[15px] text-[#a6a39a]">
              Tudo o que {user?.name ? `${user.name} e vocês dois` : "vocês dois"}{" "}
              estão acompanhando, em um só lugar.
            </p>
          </div>
          <div className="flex w-full flex-wrap items-center gap-3 sm:w-auto">
            <TooltipProvider>
              <Tooltip>
                <TooltipTrigger
                  render={
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() =>
                        compareMode ? exitCompareMode() : setCompareMode(true)
                      }
                      aria-label={
                        compareMode ? "Cancelar comparação" : "Comparar títulos"
                      }
                      className={
                        compareMode
                          ? "inline-flex cursor-pointer items-center gap-2 rounded-2xl border-[rgba(255,203,43,.35)] bg-[rgba(255,203,43,.12)] px-4.5 py-3.5 text-[14px] font-semibold text-[#ffcb2b] hover:bg-[rgba(255,203,43,.18)] hover:text-[#ffcb2b]"
                          : "inline-flex cursor-pointer items-center gap-2 rounded-2xl border-white/12 bg-transparent px-4.5 py-3.5 text-[14px] font-semibold text-[#f6f4ec] hover:bg-white/[0.06] hover:text-[#f6f4ec]"
                      }
                    />
                  }
                >
                  {compareMode ? (
                    <X className="size-4" />
                  ) : (
                    <Columns2 className="size-4" />
                  )}
                  {compareMode ? "Cancelar" : "Comparar"}
                </TooltipTrigger>
                <TooltipContent className="max-w-[240px] rounded-lg border border-[rgba(255,255,255,.1)] bg-[#201e18] px-3 py-2 text-[#f6f4ec] shadow-xl">
                  {COMPARE_TOOLTIP}
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>

            <Button
              type="button"
              onClick={() => setModalOpen(true)}
              className="inline-flex flex-1 cursor-pointer items-center justify-center gap-2.5 rounded-2xl border-none bg-[#ffcb2b] px-5.5 py-3.5 text-[15px] font-bold text-[#111] shadow-[0_10px_26px_rgba(255,203,43,.34)] transition-transform hover:-translate-y-0.5 sm:flex-none"
            >
              <span className="text-[19px] leading-none">＋</span> Adicionar Título
            </Button>
          </div>
        </div>

        {SECTIONS.map((section) => {
          const state = sections[section.status]
          const { items, loading, loadingMore, total } = state
          const hasMore = !loading && items.length < total
          const isOpen = openSections[section.status]
          const showSkeleton = showSectionSkeleton[section.status]
          const showLoadMore = showLoadMoreSkeleton[section.status]
          return (
            <Collapsible
              key={section.status}
              open={isOpen}
              onOpenChange={(open) =>
                setOpenSections((prev) => ({ ...prev, [section.status]: open }))
              }
              className="mb-11"
            >
              <CollapsibleTrigger className="mb-4.5 flex w-full cursor-pointer items-center gap-3">
                <span
                  className="size-2.5 rounded-full"
                  style={{
                    background: section.dotColor,
                    boxShadow: `0 0 12px ${section.dotColor}`,
                  }}
                />
                <h2 className="font-display text-xl tracking-tight">{section.title}</h2>
                <span className="flex items-center rounded-full bg-white/[0.05] px-2.5 py-0.5 text-[13px] text-[#a6a39a]">
                  {showSkeleton ? <Skeleton className="h-3 w-4" /> : total}
                </span>
                <ChevronDown
                  className="ml-auto size-4 text-[#a6a39a] transition-transform duration-200"
                  style={{ transform: isOpen ? "rotate(0deg)" : "rotate(-90deg)" }}
                />
              </CollapsibleTrigger>

              <CollapsibleContent>
                {showSkeleton ? (
                  <div className="no-scrollbar flex gap-5.5 overflow-x-auto pr-[20vw] [overscroll-behavior-x:contain] [scroll-snap-type:x_mandatory] md:grid md:grid-cols-[repeat(auto-fill,minmax(250px,1fr))] md:overflow-visible md:pr-0 md:[overscroll-behavior-x:auto] md:[scroll-snap-type:none]">
                    <MediaCardSkeleton />
                    <MediaCardSkeleton />
                    <MediaCardSkeleton />
                    <MediaCardSkeleton />
                  </div>
                ) : items.length === 0 ? (
                  <div className="rounded-2xl border border-dashed border-white/10 px-5 py-7 text-sm text-[#a6a39a]">
                    {section.emptyMessage}
                  </div>
                ) : (
                  <>
                    <div className="no-scrollbar flex gap-5.5 overflow-x-auto pr-[20vw] [overscroll-behavior-x:contain] [scroll-snap-type:x_mandatory] md:grid md:grid-cols-[repeat(auto-fill,minmax(250px,1fr))] md:overflow-visible md:pr-0 md:[overscroll-behavior-x:auto] md:[scroll-snap-type:none]">
                      {items.map((track) => (
                        <div
                          key={track.id}
                          className="min-w-[72vw] max-w-[72vw] shrink-0 [scroll-snap-align:start] md:min-w-0 md:max-w-none md:shrink md:[scroll-snap-align:none]"
                        >
                          <MediaCard
                            track={track}
                            myUserId={user?.id ?? ""}
                            onStatusChange={setWatchTrack}
                            onStartWatching={reloadAllFirstPages}
                            onReview={setReviewTrack}
                            onRated={(updated) => {
                              setSections((prev) => ({
                                ...prev,
                                [updated.status]: {
                                  ...prev[updated.status],
                                  items: prev[updated.status].items.map((t) =>
                                    t.id === updated.id ? updated : t
                                  ),
                                },
                              }))
                            }}
                            onClick={setDetailTrack}
                            onDelete={setDeleteTrack}
                            compareMode={compareMode}
                            compareSelected={selectedForCompare.some(
                              (t) => t.id === track.id
                            )}
                            compareOrder={
                              selectedForCompare.findIndex((t) => t.id === track.id) + 1 ||
                              null
                            }
                            onCompareToggle={toggleCompareSelection}
                          />
                        </div>
                      ))}
                      {showLoadMore && <MediaCardSkeleton />}
                      {isMobile && hasMore && (
                        <LoadMoreSentinel
                          status={section.status}
                          hasMore={hasMore}
                          loadingMore={loadingMore}
                          onLoadMore={handleLoadMore}
                        />
                      )}
                    </div>
                    {hasMore && (
                      <div className="mt-6 hidden justify-center md:flex">
                        {showLoadMore ? (
                          <div className="w-[220px]">
                            <MediaCardSkeleton />
                          </div>
                        ) : (
                          <Button
                            type="button"
                            onClick={() => handleLoadMore(section.status)}
                            disabled={loadingMore}
                            className="h-auto cursor-pointer rounded-full border border-white/10 bg-white/[0.04] px-6 py-2.5 text-[13px] font-semibold text-[#f6f4ec] transition-colors hover:border-[rgba(255,203,43,.4)] hover:bg-white/[0.07] disabled:cursor-not-allowed disabled:opacity-60"
                          >
                            Carregar mais
                          </Button>
                        )}
                      </div>
                    )}
                  </>
                )}
              </CollapsibleContent>
            </Collapsible>
          )
        })}
      </main>

      {compareMode && (
        <div className="fixed inset-x-0 bottom-8 z-[35] flex justify-center px-4">
          <div className="flex max-w-[calc(100vw-32px)] flex-col items-center gap-2.5 rounded-2xl border border-white/10 bg-[#161513]/95 px-5 py-3 shadow-[0_20px_50px_rgba(0,0,0,.5)] backdrop-blur-md">
            <div className="flex items-center gap-3">
              <span className="text-[13.5px] font-semibold text-[#f6f4ec]">
                {selectedForCompare.length}/2 selecionados
              </span>
              <button
                type="button"
                onClick={clearCompareSelection}
                aria-label="Limpar seleção"
                className="grid size-6 cursor-pointer place-items-center rounded-full bg-white/[0.08] text-[#a6a39a] transition hover:bg-white/[0.14] hover:text-[#f6f4ec]"
              >
                <X className="size-3.5" />
              </button>
            </div>
            {compareError && (
              <div className="flex items-center gap-2.5 text-[12.5px] text-[#ffb3b3]">
                <span>{compareError}</span>
                <button
                  type="button"
                  onClick={retryCompareFetch}
                  className="cursor-pointer font-semibold text-[#ffcb2b] hover:text-[#ffe08a]"
                >
                  Tentar novamente
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      <ComparisonDialog
        open={compareDialogOpen}
        onOpenChange={(open) => {
          if (!open) exitCompareMode()
        }}
        left={compareLeft}
        right={compareRight}
      />

      <Button
        type="button"
        onClick={() => setModalOpen(true)}
        title="Adicionar Título"
        className="fixed right-4 bottom-8 z-[35] grid size-15 cursor-pointer place-items-center rounded-[20px] border-none bg-[#ffcb2b] text-[28px] text-[#111] shadow-[0_14px_34px_rgba(255,203,43,.45)] sm:right-11"
      >
        ＋
      </Button>

      <TitleModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSuccess={handleModalSuccess}
      />

      <MediaDetailModal
        track={detailTrack}
        myUserId={user?.id ?? ""}
        onClose={() => setDetailTrack(null)}
        onStatusChange={(t) => {
          setDetailTrack(null)
          setWatchTrack(t)
        }}
      />

      <WatchModal
        track={watchTrack}
        onClose={() => setWatchTrack(null)}
        onSuccess={() => {
          setWatchTrack(null)
          reloadAllFirstPages()
        }}
      />

      <ReviewModal
        track={reviewTrack}
        myUserId={user?.id ?? ""}
        onClose={() => setReviewTrack(null)}
        onSuccess={(updated) => {
          setReviewTrack(null)
          setSections((prev) => ({
            ...prev,
            [updated.status]: {
              ...prev[updated.status],
              items: prev[updated.status].items.map((t) =>
                t.id === updated.id ? updated : t
              ),
            },
          }))
        }}
      />

      <DeleteTrackDialog
        track={deleteTrack}
        onClose={() => setDeleteTrack(null)}
        onSuccess={(track) => {
          setDeleteTrack(null)
          setSections((prev) => ({
            ...prev,
            [track.status]: {
              ...prev[track.status],
              items: prev[track.status].items.filter((t) => t.id !== track.id),
              total: Math.max(prev[track.status].total - 1, 0),
            },
          }))
        }}
      />
    </div>
  )
}
