import { useCallback, useEffect, useRef, useState } from "react"

import { Columns2, X } from "lucide-react"

import { Header } from "@/components/Header"
import { Button } from "@/components/ui/button"
import { ComparisonDialog } from "@/components/ComparisonDialog"
import { DeleteTrackDialog } from "@/components/DeleteTrackDialog"
import { MediaDetailModal } from "@/components/MediaDetailModal"
import { ReviewModal } from "@/components/ReviewModal"
import { TitleModal } from "@/components/TitleModal"
import { WatchModal } from "@/components/WatchModal"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { api } from "@/lib/api"
import { useCompareSelection } from "@/lib/useCompareSelection"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { useIsMobile } from "@/lib/useIsMobile"
import { useAuthStore } from "@/stores/useAuthStore"
import type { MediaStatus, MediaTrackResponse, PagedMediaTrackResponse } from "@/types/tracking"
import { TrackSection } from "./TrackSection"
import {
  buildComparisonItem,
  COMPARE_TOOLTIP,
  emptySections,
  PAGE_SIZE,
  SECTIONS,
  type SectionState,
} from "./helpers"

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

  const compare = useCompareSelection<MediaTrackResponse>(
    buildComparisonItem,
    (track) => track.id
  )

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
      .catch((error) => {
        console.error(`Falha ao carregar a seção ${status} (página ${page})`, error)
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
                        compare.compareMode
                          ? compare.exitCompareMode()
                          : compare.enterCompareMode()
                      }
                      aria-label={
                        compare.compareMode ? "Cancelar comparação" : "Comparar títulos"
                      }
                      className={
                        compare.compareMode
                          ? "inline-flex cursor-pointer items-center gap-2 rounded-2xl border-[rgba(255,203,43,.35)] bg-[rgba(255,203,43,.12)] px-4.5 py-3.5 text-[14px] font-semibold text-[#ffcb2b] hover:bg-[rgba(255,203,43,.18)] hover:text-[#ffcb2b]"
                          : "inline-flex cursor-pointer items-center gap-2 rounded-2xl border-white/12 bg-transparent px-4.5 py-3.5 text-[14px] font-semibold text-[#f6f4ec] hover:bg-white/[0.06] hover:text-[#f6f4ec]"
                      }
                    />
                  }
                >
                  {compare.compareMode ? (
                    <X className="size-4" />
                  ) : (
                    <Columns2 className="size-4" />
                  )}
                  {compare.compareMode ? "Cancelar" : "Comparar"}
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

        {SECTIONS.map((section) => (
          <TrackSection
            key={section.status}
            section={section}
            state={sections[section.status]}
            isOpen={openSections[section.status]}
            onOpenChange={(open) =>
              setOpenSections((prev) => ({ ...prev, [section.status]: open }))
            }
            showSkeleton={showSectionSkeleton[section.status]}
            showLoadMoreSkeleton={showLoadMoreSkeleton[section.status]}
            isMobile={isMobile}
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
            onLoadMore={handleLoadMore}
            compareMode={compare.compareMode}
            compareSelected={compare.selected}
            onCompareToggle={compare.toggle}
          />
        ))}
      </main>

      {compare.compareMode && (
        <div className="fixed inset-x-0 bottom-8 z-[35] flex justify-center px-4">
          <div className="flex max-w-[calc(100vw-32px)] flex-col items-center gap-2.5 rounded-2xl border border-white/10 bg-[#161513]/95 px-5 py-3 shadow-[0_20px_50px_rgba(0,0,0,.5)] backdrop-blur-md">
            <div className="flex items-center gap-3">
              <span className="text-[13.5px] font-semibold text-[#f6f4ec]">
                {compare.selected.length}/2 selecionados
              </span>
              <button
                type="button"
                onClick={compare.clearSelection}
                aria-label="Limpar seleção"
                className="grid size-6 cursor-pointer place-items-center rounded-full bg-white/[0.08] text-[#a6a39a] transition hover:bg-white/[0.14] hover:text-[#f6f4ec]"
              >
                <X className="size-3.5" />
              </button>
            </div>
            {compare.error && (
              <div className="flex items-center gap-2.5 text-[12.5px] text-[#ffb3b3]">
                <span>{compare.error}</span>
                <button
                  type="button"
                  onClick={compare.retry}
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
        open={compare.dialogOpen}
        onOpenChange={(open) => {
          if (!open) compare.exitCompareMode()
        }}
        left={compare.left}
        right={compare.right}
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
