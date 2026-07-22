import { useCallback, useEffect, useState } from "react"

import { Header } from "@/components/Header"
import { MediaCard } from "@/components/MediaCard"
import { MediaDetailModal } from "@/components/MediaDetailModal"
import { TitleModal } from "@/components/TitleModal"
import { WatchModal } from "@/components/WatchModal"
import { api } from "@/lib/api"
import { useAuthStore } from "@/stores/useAuthStore"
import type { MediaStatus, MediaTrackResponse } from "@/types/tracking"

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
  const [tracks, setTracks] = useState<MediaTrackResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [detailTrack, setDetailTrack] = useState<MediaTrackResponse | null>(null)
  const [watchTrack, setWatchTrack] = useState<MediaTrackResponse | null>(null)

  const loadTracks = useCallback(() => {
    setLoading(true)
    return api
      .get<MediaTrackResponse[]>("/api/tracking")
      .then((response) => {
        setTracks(response.data)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    api
      .get<MediaTrackResponse[]>("/api/tracking")
      .then((response) => {
        setTracks(response.data)
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  const handleModalSuccess = () => {
    setModalOpen(false)
    void loadTracks()
  }

  const groups: Record<MediaStatus, MediaTrackResponse[]> = {
    WATCHING: tracks.filter((track) => track.status === "WATCHING"),
    WANT_TO_SEE: tracks.filter((track) => track.status === "WANT_TO_SEE"),
    WATCHED: tracks.filter((track) => track.status === "WATCHED"),
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
          <button
            type="button"
            onClick={() => setModalOpen(true)}
            className="inline-flex cursor-pointer items-center gap-2.5 rounded-2xl border-none bg-[#ffcb2b] px-5.5 py-3.5 text-[15px] font-bold text-[#111] shadow-[0_10px_26px_rgba(255,203,43,.34)] transition-transform hover:-translate-y-0.5"
          >
            <span className="text-[19px] leading-none">＋</span> Adicionar Título
          </button>
        </div>

        {SECTIONS.map((section) => {
          const items = groups[section.status]
          return (
            <section key={section.status} className="mb-11">
              <div className="mb-4.5 flex items-center gap-3">
                <span
                  className="size-2.5 rounded-full"
                  style={{
                    background: section.dotColor,
                    boxShadow: `0 0 12px ${section.dotColor}`,
                  }}
                />
                <h2 className="font-display text-xl tracking-tight">
                  {section.title}
                </h2>
                <span className="rounded-full bg-white/[0.05] px-2.5 py-0.5 text-[13px] text-[#a6a39a]">
                  {loading ? "…" : items.length}
                </span>
              </div>

              {loading ? (
                <div className="grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-5.5">
                  <SkeletonCard />
                  <SkeletonCard />
                </div>
              ) : items.length === 0 ? (
                <div className="rounded-2xl border border-dashed border-white/10 px-5 py-7 text-sm text-[#a6a39a]">
                  {section.emptyMessage}
                </div>
              ) : (
                <div className="grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-5.5">
                  {items.map((track) => (
                    <MediaCard
                      key={track.id}
                      track={track}
                      myUserId={user?.id ?? ""}
                      onStatusChange={setWatchTrack}
                      onClick={setDetailTrack}
                    />
                  ))}
                </div>
              )}
            </section>
          )
        })}
      </main>

      <button
        type="button"
        onClick={() => setModalOpen(true)}
        title="Adicionar Título"
        className="fixed right-5 bottom-8 z-[35] grid size-15 cursor-pointer place-items-center rounded-[20px] border-none bg-[#ffcb2b] text-[28px] text-[#111] shadow-[0_14px_34px_rgba(255,203,43,.45)] sm:right-11"
      >
        ＋
      </button>

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
          void loadTracks()
        }}
      />
    </div>
  )
}
