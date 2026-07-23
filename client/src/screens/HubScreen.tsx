import { useCallback, useEffect, useState } from "react"

import { ChevronDown } from "lucide-react"

import { Header } from "@/components/Header"
import { Button } from "@/components/ui/button"
import { DeleteTrackDialog } from "@/components/DeleteTrackDialog"
import { MediaCard } from "@/components/MediaCard"
import { MediaDetailModal } from "@/components/MediaDetailModal"
import { ReviewModal } from "@/components/ReviewModal"
import { TitleModal } from "@/components/TitleModal"
import { WatchModal } from "@/components/WatchModal"
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible"
import { api } from "@/lib/api"
import { useAuthStore } from "@/stores/useAuthStore"
import type { MediaStatus, MediaTrackResponse, PagedMediaTrackResponse } from "@/types/tracking"

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

function SkeletonCard() {
  return (
    <div className="overflow-hidden rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513]">
      <div className="aspect-[3/4] animate-pulse bg-white/[0.04]" />
      <div className="space-y-2.5 p-3.5">
        <div className="h-3.5 w-3/4 animate-pulse rounded bg-white/[0.06]" />
        <div className="h-3 w-1/2 animate-pulse rounded bg-white/[0.06]" />
      </div>
    </div>
  )
}

export function HubScreen() {
  const user = useAuthStore((state) => state.user)
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

  const handleLoadMore = (status: MediaStatus) => {
    const current = sections[status]
    setSections((prev) => ({
      ...prev,
      [status]: { ...prev[status], loadingMore: true },
    }))
    void fetchSectionPage(status, current.page + 1)
  }

  const handleModalSuccess = () => {
    setModalOpen(false)
    reloadAllFirstPages()
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
      <main className="mx-auto max-w-[1240px] px-5 pt-10 pb-32 sm:px-8 sm:pt-11">
        <div className="mb-7 flex flex-wrap items-end justify-between gap-5">
          <div>
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
          <Button
            type="button"
            onClick={() => setModalOpen(true)}
            className="inline-flex cursor-pointer items-center gap-2.5 rounded-2xl border-none bg-[#ffcb2b] px-5.5 py-3.5 text-[15px] font-bold text-[#111] shadow-[0_10px_26px_rgba(255,203,43,.34)] transition-transform hover:-translate-y-0.5"
          >
            <span className="text-[19px] leading-none">＋</span> Adicionar Título
          </Button>
        </div>

        {SECTIONS.map((section) => {
          const state = sections[section.status]
          const { items, loading, loadingMore, total } = state
          const hasMore = !loading && items.length < total
          const isOpen = openSections[section.status]
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
                <span className="rounded-full bg-white/[0.05] px-2.5 py-0.5 text-[13px] text-[#a6a39a]">
                  {loading ? "…" : total}
                </span>
                <ChevronDown
                  className="ml-auto size-4 text-[#a6a39a] transition-transform duration-200"
                  style={{ transform: isOpen ? "rotate(0deg)" : "rotate(-90deg)" }}
                />
              </CollapsibleTrigger>

              <CollapsibleContent>
                {loading ? (
                  <div className="grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-5.5">
                    <SkeletonCard />
                    <SkeletonCard />
                    <SkeletonCard />
                    <SkeletonCard />
                  </div>
                ) : items.length === 0 ? (
                  <div className="rounded-2xl border border-dashed border-white/10 px-5 py-7 text-sm text-[#a6a39a]">
                    {section.emptyMessage}
                  </div>
                ) : (
                  <>
                    <div className="grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-5.5">
                      {items.map((track) => (
                        <MediaCard
                          key={track.id}
                          track={track}
                          myUserId={user?.id ?? ""}
                          onStatusChange={setWatchTrack}
                          onStartWatching={reloadAllFirstPages}
                          onReview={setReviewTrack}
                          onClick={setDetailTrack}
                          onDelete={setDeleteTrack}
                        />
                      ))}
                    </div>
                    {hasMore && (
                      <div className="mt-6 flex justify-center">
                        <Button
                          type="button"
                          onClick={() => handleLoadMore(section.status)}
                          disabled={loadingMore}
                          className="h-auto cursor-pointer rounded-full border border-white/10 bg-white/[0.04] px-6 py-2.5 text-[13px] font-semibold text-[#f6f4ec] transition-colors hover:border-[rgba(255,203,43,.4)] hover:bg-white/[0.07] disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {loadingMore ? "Carregando…" : "Carregar mais"}
                        </Button>
                      </div>
                    )}
                  </>
                )}
              </CollapsibleContent>
            </Collapsible>
          )
        })}
      </main>

      <Button
        type="button"
        onClick={() => setModalOpen(true)}
        title="Adicionar Título"
        className="fixed right-5 bottom-8 z-[35] grid size-15 cursor-pointer place-items-center rounded-[20px] border-none bg-[#ffcb2b] text-[28px] text-[#111] shadow-[0_14px_34px_rgba(255,203,43,.45)] sm:right-11"
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
