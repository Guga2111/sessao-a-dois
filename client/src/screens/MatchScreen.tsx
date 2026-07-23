import { useEffect, useState } from "react"

import { Heart, Loader2, Search, Sparkles, X } from "lucide-react"

import { Header } from "@/components/Header"
import { PendingDetailModal } from "@/components/PendingDetailModal"
import { api } from "@/lib/api"
import { cn } from "@/lib/utils"
import { useMatchStore } from "@/stores/useMatchStore"
import type { MediaSearchResult, PendingMatch } from "@/types/media"
import type { MediaTrackResponse } from "@/types/tracking"

type LikeState = "idle" | "loading" | "liked" | "matched" | "error"
type ActiveTab = "suggestions" | "search"

const TYPE_LABEL: Record<MediaSearchResult["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Serie",
}

function trackKey(mediaType: string, tmdbId: number): string {
  return `${mediaType}-${tmdbId}`
}

function SuggestionsTab() {
  const { pendingQueue, pendingLoading, fetchPending, removePending } =
    useMatchStore()
  const [actionLoading, setActionLoading] = useState(false)
  const [detailItem, setDetailItem] = useState<PendingMatch | null>(null)

  useEffect(() => {
    fetchPending()
  }, [fetchPending])

  const current = pendingQueue[0] ?? null

  const handleReject = async () => {
    if (!current || actionLoading) return
    setActionLoading(true)
    try {
      await api.post("/api/match/reject", {
        tmdbId: current.tmdbId,
        mediaType: current.mediaType,
      })
      removePending(current.tmdbId)
      setDetailItem(null)
    } catch {
      // silently ignore
    } finally {
      setActionLoading(false)
    }
  }

  const handleLike = async () => {
    if (!current || actionLoading) return
    setActionLoading(true)
    try {
      await api.post("/api/match/like", {
        tmdbId: current.tmdbId,
        mediaType: current.mediaType,
      })
      removePending(current.tmdbId)
      setDetailItem(null)
    } catch {
      // silently ignore
    } finally {
      setActionLoading(false)
    }
  }

  if (pendingLoading) {
    return (
      <div className="flex justify-center pt-16">
        <Loader2 className="size-6 animate-spin text-[#a6a39a]" />
      </div>
    )
  }

  if (!current) {
    return (
      <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
        Nenhuma sugestao pendente. Voltem a buscar titulos na aba "Buscar".
      </div>
    )
  }

  const hue = current.tmdbId % 360

  return (
    <>
      <div className="flex flex-col items-center">
        <div className="w-full max-w-[340px]">
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
            <button
              type="button"
              onClick={handleReject}
              disabled={actionLoading}
              className="flex size-14 cursor-pointer items-center justify-center rounded-full border border-[rgba(255,107,107,.3)] bg-[rgba(255,107,107,.08)] text-[#ff6b6b] transition-colors hover:bg-[rgba(255,107,107,.16)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              <X className="size-6" strokeWidth={2.5} />
            </button>
            <button
              type="button"
              onClick={handleLike}
              disabled={actionLoading}
              className="flex size-14 cursor-pointer items-center justify-center rounded-full border border-[rgba(61,220,151,.3)] bg-[rgba(61,220,151,.08)] text-[#3ddc97] transition-colors hover:bg-[rgba(61,220,151,.16)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              <Heart className="size-6" strokeWidth={2.5} />
            </button>
          </div>

          <p className="mt-4 text-center text-[13px] text-[#a6a39a]">
            1 de {pendingQueue.length}{" "}
            {pendingQueue.length === 1 ? "sugestao" : "sugestoes"}
          </p>
        </div>
      </div>

      <PendingDetailModal
        item={detailItem}
        onClose={() => setDetailItem(null)}
        onLike={handleLike}
        onReject={handleReject}
        actionLoading={actionLoading}
      />
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

  useEffect(() => {
    api
      .get<MediaTrackResponse[]>("/api/tracking")
      .then((response) => {
        setTrackedKeys(
          new Set(response.data.map((t) => trackKey(t.mediaType, t.tmdbId)))
        )
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    if (!query.trim()) {
      return
    }

    let cancelled = false
    const timer = setTimeout(() => {
      setSearching(true)
      api
        .get<MediaSearchResult[]>("/api/media/search", {
          params: { q: query.trim() },
        })
        .then((response) => {
          if (cancelled) return
          setResults(response.data)
          setSearched(true)
        })
        .catch(() => {
          if (cancelled) return
          setResults([])
          setSearched(true)
        })
        .finally(() => {
          if (!cancelled) setSearching(false)
        })
    }, 400)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [query])

  const handleLike = async (result: MediaSearchResult) => {
    const key = trackKey(result.mediaType, result.tmdbId)
    if (trackedKeys.has(key) || likeStates[key] === "loading") return

    setLikeStates((s) => ({ ...s, [key]: "loading" }))
    try {
      const response = await api.post<{ matched: boolean }>(
        "/api/match/like",
        { tmdbId: result.tmdbId, mediaType: result.mediaType }
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
      <div className="mx-auto mb-10 max-w-[520px]">
        <div className="relative">
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
            className="w-full rounded-2xl border border-white/10 bg-[#161513] py-4 pr-4 pl-11 text-sm text-[#f6f4ec] outline-none transition-shadow focus:border-[#ffcb2b] focus:shadow-[0_0_0_3px_rgba(255,203,43,.2)]"
          />
          {searching && (
            <Loader2 className="absolute top-1/2 right-4 size-4.5 -translate-y-1/2 animate-spin text-[#a6a39a]" />
          )}
        </div>
      </div>

      {!searched && !query.trim() && (
        <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
          Comecem digitando o nome de um filme ou serie ai em cima.
        </div>
      )}

      {searched && !searching && results.length === 0 && (
        <div className="mx-auto max-w-[420px] rounded-2xl border border-dashed border-white/10 px-6 py-10 text-center text-sm text-[#a6a39a]">
          Nada encontrado para "{query.trim()}". Tentem outro termo.
        </div>
      )}

      <div className="grid grid-cols-[repeat(auto-fill,minmax(250px,1fr))] gap-5.5">
        {results.map((result) => {
          const key = trackKey(result.mediaType, result.tmdbId)
          const alreadyTracked = trackedKeys.has(key)
          const likeState: LikeState =
            likeStates[key] ?? (alreadyTracked ? "liked" : "idle")
          const hue = result.tmdbId % 360

          return (
            <div
              key={key}
              className="overflow-hidden rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513]"
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

                <button
                  type="button"
                  onClick={() => handleLike(result)}
                  disabled={alreadyTracked || likeState === "loading"}
                  className={cn(
                    "mt-3.5 flex w-full cursor-pointer items-center justify-center gap-2 rounded-[10px] border px-3 py-2.5 text-[13px] font-semibold transition-colors disabled:cursor-not-allowed",
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
                </button>
              </div>
            </div>
          )
        })}
      </div>
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
            Busquem um titulo e curtam. Quando os dois curtirem o mesmo, vira um
            match — ele entra direto na lista de voces.
          </p>
        </div>

        <div className="mx-auto mb-10 flex w-fit gap-1 rounded-xl bg-[rgba(255,255,255,.06)] p-1">
          <button
            type="button"
            onClick={() => setActiveTab("suggestions")}
            className={cn(
              "cursor-pointer rounded-lg px-4 py-2 text-[13px] font-semibold transition-colors",
              activeTab === "suggestions"
                ? "bg-[#ffcb2b] text-[#09090a]"
                : "text-[#a6a39a] hover:text-[#f6f4ec]"
            )}
          >
            Sugestoes
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("search")}
            className={cn(
              "cursor-pointer rounded-lg px-4 py-2 text-[13px] font-semibold transition-colors",
              activeTab === "search"
                ? "bg-[#ffcb2b] text-[#09090a]"
                : "text-[#a6a39a] hover:text-[#f6f4ec]"
            )}
          >
            Buscar
          </button>
        </div>

        {activeTab === "suggestions" && <SuggestionsTab />}
        {activeTab === "search" && <SearchTab />}
      </main>
    </div>
  )
}
