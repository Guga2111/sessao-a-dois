import { isAxiosError } from "axios"
import { Search, X } from "lucide-react"
import { useEffect, useRef, useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog"
import { ScrollArea } from "@/components/ui/scroll-area"
import { SearchResultSkeleton } from "@/components/skeletons/SearchResultSkeleton"
import { api } from "@/lib/api"
import { useDelayedLoading } from "@/lib/useDelayedLoading"
import { cn } from "@/lib/utils"
import type { MediaSearchResult } from "@/types/media"
import type { MediaStatus, MediaTrackResponse } from "@/types/tracking"

interface TitleModalProps {
  open: boolean
  onClose: () => void
  onSuccess: (track: MediaTrackResponse) => void
}

interface StatusOption {
  value: MediaStatus
  label: string
}

const STATUS_OPTIONS: StatusOption[] = [
  { value: "WATCHING", label: "Assistindo" },
  { value: "WANT_TO_SEE", label: "Queremos Ver" },
  { value: "WATCHED", label: "Já Vimos" },
]

const TYPE_LABEL: Record<MediaSearchResult["mediaType"], string> = {
  MOVIE: "Filme",
  TV: "Série",
}

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10)
}

function initialState() {
  return {
    query: "",
    results: [] as MediaSearchResult[],
    searchOpen: false,
    searching: false,
    selected: null as MediaSearchResult | null,
    runtime: null as number | null,
    status: "WANT_TO_SEE" as MediaStatus,
    rating: 0,
    watchedDate: todayIsoDate(),
    opinion: "",
    saving: false,
    error: null as string | null,
  }
}

export function TitleModal({ open, onClose, onSuccess }: TitleModalProps) {
  const [state, setState] = useState(initialState)
  const searchBoxRef = useRef<HTMLDivElement>(null)

  const {
    query,
    results,
    searchOpen,
    searching,
    selected,
    runtime,
    status,
    rating,
    watchedDate,
    opinion,
    saving,
    error,
  } = state

  const handleClose = () => {
    setState(initialState())
    onClose()
  }

  const showSearchSkeleton = useDelayedLoading(searching)

  useEffect(() => {
    if (!open || selected || !query.trim()) return

    let cancelled = false
    const timer = setTimeout(() => {
      setState((s) => ({ ...s, searching: true }))
      api
        .get<{ results: MediaSearchResult[] }>("/api/media/search", {
          params: { q: query.trim() },
        })
        .then((response) => {
          if (cancelled) return
          setState((s) => ({
            ...s,
            results: response.data.results,
            searching: false,
            searchOpen: true,
          }))
        })
        .catch(() => {
          if (cancelled) return
          setState((s) => ({ ...s, results: [], searching: false }))
        })
    }, 400)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [query, selected, open])

  useEffect(() => {
    const onPointerDown = (event: PointerEvent) => {
      if (
        searchBoxRef.current &&
        !searchBoxRef.current.contains(event.target as Node)
      ) {
        setState((s) => ({ ...s, searchOpen: false }))
      }
    }
    document.addEventListener("pointerdown", onPointerDown)
    return () => document.removeEventListener("pointerdown", onPointerDown)
  }, [])

  const selectResult = async (result: MediaSearchResult) => {
    setState((s) => ({
      ...s,
      selected: result,
      query: result.title,
      searchOpen: false,
      results: [],
    }))

    try {
      const response = await api.get(
        `/api/media/${result.mediaType.toLowerCase()}/${result.tmdbId}`
      )
      setState((s) => ({ ...s, runtime: response.data.runtime ?? null }))
    } catch {
      // Runtime is a nice-to-have; the save can proceed without it.
    }
  }

  const clearSelection = () => {
    setState((s) => ({
      ...s,
      selected: null,
      query: "",
      runtime: null,
      results: [],
    }))
  }

  const handleSave = async () => {
    if (!selected) {
      setState((s) => ({ ...s, error: "Selecione um título na busca." }))
      return
    }
    if (status === "WATCHED" && !watchedDate) {
      setState((s) => ({
        ...s,
        error: "Informe a data assistida para marcar como Já Vimos.",
      }))
      return
    }

    setState((s) => ({ ...s, saving: true, error: null }))
    try {
      const response = await api.post<MediaTrackResponse>("/api/tracking", {
        tmdbId: selected.tmdbId,
        mediaType: selected.mediaType,
        status,
        watchedDate: status === "WATCHED" ? watchedDate : null,
        runtime,
        rating: rating > 0 ? rating : null,
        opinion: opinion.trim() ? opinion.trim() : null,
      })
      onSuccess(response.data)
      setState(initialState())
      onClose()
    } catch (caught) {
      const message =
        (isAxiosError(caught) && caught.response?.data?.message) ||
        "Não foi possível salvar o título. Tente novamente."
      setState((s) => ({ ...s, saving: false, error: message }))
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(isOpen) => {
        if (!isOpen) handleClose()
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] max-h-[90svh] gap-0 overflow-visible rounded-[22px] border border-white/10 bg-[#161513] p-0 text-[#f6f4ec] shadow-[0_30px_80px_rgba(0,0,0,.6)] ring-0 sm:w-full sm:max-w-[520px]"
      >
        <DialogTitle className="sr-only">Adicionar Título</DialogTitle>
        <DialogDescription className="sr-only">
          Registre um filme ou série na lista de vocês.
        </DialogDescription>

        <div className="flex flex-row items-start justify-between gap-4 p-6 pb-0">
          <div>
            <p className="font-display text-[22px] font-bold tracking-tight text-[#f6f4ec]">
              Adicionar Título
            </p>
            <p className="mt-1.5 text-[13.5px] text-[#a6a39a]">
              Registre um filme ou série na lista de vocês.
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="icon-sm"
            onClick={handleClose}
            aria-label="Fechar"
            className="flex-none rounded-[10px] border-white/10 text-[#a6a39a] hover:bg-white/[0.06] hover:text-white"
          >
            <X className="size-4.5" />
          </Button>
        </div>

        <div className="px-6 pt-5.5" ref={searchBoxRef}>
          <label className="mb-2 block text-[13px] font-semibold text-[#d8d3c5]">
            Buscar título
          </label>
          <div className="relative">
            <Search className="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-[#a6a39a]" />
            <input
              value={query}
              onChange={(event) => {
                const value = event.target.value
                setState((s) => ({
                  ...s,
                  query: value,
                  selected: null,
                  runtime: null,
                  results: value.trim() ? s.results : [],
                  searching: value.trim() ? s.searching : false,
                  searchOpen: value.trim() ? s.searchOpen : false,
                }))
              }}
              onFocus={() =>
                setState((s) => ({
                  ...s,
                  searchOpen: s.results.length > 0 && !s.selected,
                }))
              }
              placeholder="Ex.: Coração de Vidro, Fronteira Norte…"
              className="w-full rounded-xl border border-white/10 bg-[#201e18] py-3.5 pr-3.5 pl-10 text-sm text-[#f6f4ec] outline-none transition-shadow focus:border-[#ffcb2b] focus:shadow-[0_0_0_3px_rgba(255,203,43,.2)]"
            />
            {showSearchSkeleton && (
              <div className="absolute top-[calc(100%+6px)] left-0 z-10 w-full overflow-hidden rounded-xl border border-white/10 bg-[#201e18] shadow-[0_18px_40px_rgba(0,0,0,.5)]">
                <SearchResultSkeleton rows={3} />
              </div>
            )}

            {!showSearchSkeleton && searchOpen && results.length > 0 && (
              <div className="absolute top-[calc(100%+6px)] left-0 z-10 w-full overflow-hidden rounded-xl border border-white/10 bg-[#201e18] py-1.5 shadow-[0_18px_40px_rgba(0,0,0,.5)]">
                <ScrollArea className="h-full max-h-64 [&_[data-slot=scroll-area-viewport]]:max-h-64">
                  {results.map((result) => (
                    <Button
                      key={`${result.mediaType}-${result.tmdbId}`}
                      type="button"
                      variant="ghost"
                      onClick={() => selectResult(result)}
                      className="flex w-full cursor-pointer items-center justify-between gap-3 rounded-none px-3.5 py-2.5 text-left text-sm hover:bg-white/[0.06]"
                    >
                      <span className="truncate font-medium">
                        {result.title}
                      </span>
                      <span className="flex-none text-xs text-[#a6a39a]">
                        {result.year ?? "—"} · {TYPE_LABEL[result.mediaType]}
                      </span>
                    </Button>
                  ))}
                </ScrollArea>
              </div>
            )}
          </div>
          {selected && (
            <div className="mt-2 flex items-center gap-2 text-xs text-[#a6a39a]">
              Selecionado: <span className="text-[#ffdd7a]">{selected.title}</span>
              <Button
                type="button"
                variant="link"
                onClick={clearSelection}
                className="h-auto p-0 text-xs text-[#a6a39a] underline decoration-dotted hover:text-white"
              >
                trocar
              </Button>
            </div>
          )}
        </div>

        <div className="flex max-h-[calc(90vh-280px)] flex-col gap-5 overflow-y-auto px-6 pb-2 pt-5">
          <div>
            <label className="mb-2 block text-[13px] font-semibold text-[#d8d3c5]">
              Status
            </label>
            <div className="flex flex-wrap gap-2">
              {STATUS_OPTIONS.map((option) => {
                const active = status === option.value
                return (
                  <Button
                    key={option.value}
                    type="button"
                    variant="ghost"
                    onClick={() =>
                      setState((s) => ({
                        ...s,
                        status: option.value,
                        rating: option.value !== "WATCHED" ? 0 : s.rating,
                        opinion: option.value !== "WATCHED" ? "" : s.opinion,
                      }))
                    }
                    className={cn(
                      "cursor-pointer rounded-[10px] border px-4 py-2 text-sm font-semibold transition-colors",
                      active
                        ? "border-[#ffcb2b] bg-[#ffcb2b] text-[#111]"
                        : "border-white/10 bg-transparent text-[#d8d3c5] hover:bg-white/[0.06]"
                    )}
                  >
                    {option.label}
                  </Button>
                )
              })}
            </div>
          </div>

          {status === "WATCHED" && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="mb-2 block text-[13px] font-semibold text-[#d8d3c5]">
                  Nota do casal
                </label>
                <div className="flex h-11 items-center gap-1">
                  {[1, 2, 3, 4, 5].map((star) => (
                    <Button
                      key={star}
                      type="button"
                      variant="ghost"
                      onClick={() =>
                        setState((s) => ({
                          ...s,
                          rating: s.rating === star ? 0 : star,
                        }))
                      }
                      aria-label={`${star} estrelas`}
                      className="h-auto cursor-pointer bg-transparent px-0.5 py-0 text-[26px] leading-none"
                      style={{
                        color: star <= rating ? "#ffb443" : "rgba(255,255,255,.18)",
                      }}
                    >
                      ★
                    </Button>
                  ))}
                </div>
              </div>

              <div>
                <label className="mb-2 block text-[13px] font-semibold text-[#d8d3c5]">
                  Data assistida
                </label>
                <input
                  type="date"
                  value={watchedDate}
                  onChange={(event) =>
                    setState((s) => ({ ...s, watchedDate: event.target.value }))
                  }
                  className="w-full rounded-xl border border-white/10 bg-[#201e18] px-3.5 py-3 text-sm text-[#f6f4ec] outline-none transition-shadow focus:border-[#ffcb2b] focus:shadow-[0_0_0_3px_rgba(255,203,43,.2)] [color-scheme:dark]"
                />
              </div>
            </div>
          )}

          {status === "WATCHED" && (
            <div>
              <label className="mb-2 block text-[13px] font-semibold text-[#d8d3c5]">
                Opinião do casal{" "}
                <span className="font-normal text-[#a6a39a]">(opcional)</span>
              </label>
              <textarea
                rows={3}
                value={opinion}
                onChange={(event) =>
                  setState((s) => ({ ...s, opinion: event.target.value }))
                }
                placeholder="O que vocês acharam? Alguma cena inesquecível?"
                className="w-full resize-y rounded-xl border border-white/10 bg-[#201e18] px-3.5 py-3.5 text-sm text-[#f6f4ec] outline-none transition-shadow focus:border-[#ffcb2b] focus:shadow-[0_0_0_3px_rgba(255,203,43,.2)]"
              />
            </div>
          )}

          {error && (
            <p className="rounded-xl border border-[#ff6b6b]/30 bg-[#ff6b6b]/10 px-3.5 py-2.5 text-[13px] text-[#ffb3b3]">
              {error}
            </p>
          )}
        </div>

        <div className="flex gap-3 p-6 pt-0">
          <Button
            type="button"
            variant="outline"
            onClick={handleClose}
            disabled={saving}
            className="flex-1 rounded-xl border-white/10 bg-transparent py-3.5 text-sm font-semibold text-[#f6f4ec] hover:bg-white/[0.06]"
          >
            Cancelar
          </Button>
          <Button
            type="button"
            variant="default"
            onClick={handleSave}
            disabled={saving}
            className="flex-[1.4] rounded-xl border-none bg-[#ffcb2b] py-3.5 text-sm font-bold text-[#111] shadow-[0_8px_22px_rgba(255,203,43,.35)] transition-opacity disabled:opacity-60"
          >
            {saving ? "Salvando…" : "Salvar Título"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
