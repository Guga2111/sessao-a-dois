import { isAxiosError } from "axios"
import { X } from "lucide-react"
import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
import { api } from "@/lib/api"
import type { MediaTrackResponse } from "@/types/tracking"

interface ReviewModalProps {
  track: MediaTrackResponse | null
  myUserId: string
  onClose: () => void
  onSuccess: (track: MediaTrackResponse) => void
}

export function ReviewModal({ track, myUserId, onClose, onSuccess }: ReviewModalProps) {
  useEffect(() => {
    if (!track) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose()
    }
    document.addEventListener("keydown", onKeyDown)
    return () => document.removeEventListener("keydown", onKeyDown)
  }, [track, onClose])

  if (!track) return null

  return (
    <ReviewModalContent
      key={track.id}
      track={track}
      myUserId={myUserId}
      onClose={onClose}
      onSuccess={onSuccess}
    />
  )
}

interface ReviewModalContentProps {
  track: MediaTrackResponse
  myUserId: string
  onClose: () => void
  onSuccess: (track: MediaTrackResponse) => void
}

function ReviewModalContent({
  track,
  myUserId,
  onClose,
  onSuccess,
}: ReviewModalContentProps) {
  const myReview = track.reviews.find((review) => review.userId === myUserId)
  const [rating, setRating] = useState(myReview?.rating ?? 0)
  const [opinion, setOpinion] = useState(myReview?.opinion ?? "")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setSaving(true)
    setError(null)
    try {
      const response = await api.put<MediaTrackResponse>(
        `/api/tracking/${track.id}/review`,
        {
          rating: rating > 0 ? rating : null,
          opinion: opinion.trim() || null,
        }
      )
      onSuccess(response.data)
    } catch (caught) {
      const message =
        (isAxiosError(caught) && caught.response?.data?.message) ||
        "Não foi possível salvar a avaliação. Tente novamente."
      setError(message)
      setSaving(false)
    }
  }

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-[60] grid place-items-center bg-[rgba(8,7,11,.72)] p-5 backdrop-blur-md"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="animate-in fade-in zoom-in-95 w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] max-h-[90svh] overflow-y-auto rounded-[22px] border border-white/10 bg-card text-foreground shadow-[0_30px_80px_rgba(0,0,0,.6)] duration-200 sm:w-full sm:max-w-[420px]"
      >
        {/* Header */}
        <div className="flex items-start justify-between gap-4 p-6 pb-5">
          <div>
            <h2 className="font-display text-[20px] font-bold tracking-tight">
              Reavaliar
            </h2>
            <p className="mt-1 text-[13px] text-muted-foreground">
              Atualize sua nota e opinião. Nota e opinião são opcionais.
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="icon-sm"
            onClick={onClose}
            aria-label="Fechar"
            className="flex-none rounded-[10px] border-white/10 text-muted-foreground hover:bg-white/[0.06] hover:text-white"
          >
            <X className="size-4.5" />
          </Button>
        </div>

        <div className="flex flex-col gap-5 px-6 pb-6">
          {/* Stars */}
          <div>
            <label className="mb-2 block text-[13px] font-semibold text-[#d8d3c5]">
              Sua nota{" "}
              <span className="font-normal text-muted-foreground">(opcional)</span>
            </label>
            <div className="flex h-10 items-center gap-0.5">
              {[1, 2, 3, 4, 5].map((star) => (
                <Button
                  key={star}
                  type="button"
                  variant="ghost"
                  onClick={() => setRating((prev) => (prev === star ? 0 : star))}
                  aria-label={`${star} estrela${star > 1 ? "s" : ""}`}
                  className="h-auto cursor-pointer bg-transparent px-1 py-0 text-[28px] leading-none transition-transform hover:scale-110"
                  style={{
                    color: star <= rating ? "#ffb443" : "rgba(255,255,255,.18)",
                  }}
                >
                  ★
                </Button>
              ))}
            </div>
          </div>

          {/* Opinion */}
          <div>
            <label className="mb-2 block text-[13px] font-semibold text-[#d8d3c5]">
              Sua opinião{" "}
              <span className="font-normal text-muted-foreground">(opcional)</span>
            </label>
            <textarea
              rows={3}
              value={opinion}
              onChange={(e) => setOpinion(e.target.value)}
              placeholder="O que você achou? Alguma cena inesquecível?"
              className="w-full resize-y rounded-xl border border-white/10 bg-[#201e18] px-3.5 py-3.5 text-sm text-foreground outline-none transition-shadow focus:border-primary focus:shadow-[0_0_0_3px_rgba(255,203,43,.2)]"
            />
          </div>

          {error && (
            <p className="rounded-xl border border-destructive/30 bg-destructive/10 px-3.5 py-2.5 text-[13px] text-destructive-foreground">
              {error}
            </p>
          )}

          {/* Actions */}
          <div className="flex gap-3">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={saving}
              className="flex-1 rounded-xl border-white/10 bg-transparent py-3.5 text-sm font-semibold text-foreground hover:bg-white/[0.06]"
            >
              Cancelar
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={handleConfirm}
              disabled={saving}
              className="flex-[1.4] rounded-xl border border-primary/35 bg-primary/14 py-3.5 text-sm font-bold text-primary shadow-[0_6px_20px_rgba(255,203,43,.15)] hover:bg-primary/20 disabled:opacity-60"
            >
              {saving ? "Salvando…" : "Salvar avaliação"}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
