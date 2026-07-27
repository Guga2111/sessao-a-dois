import { isAxiosError } from "axios"
import { useEffect, useState } from "react"

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { api } from "@/lib/api"
import type { MediaTrackResponse } from "@/types/tracking"

interface RatingRequestDialogProps {
  mediaTrackId: string | null
  title: string
  onClose: () => void
  onSuccess: (track: MediaTrackResponse) => void
}

export function RatingRequestDialog({
  mediaTrackId,
  title,
  onClose,
  onSuccess,
}: RatingRequestDialogProps) {
  const [rating, setRating] = useState(0)
  const [opinion, setOpinion] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!mediaTrackId) {
      setRating(0)
      setOpinion("")
      setError(null)
      setSaving(false)
    }
  }, [mediaTrackId])

  const open = mediaTrackId !== null

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) onClose()
  }

  const handleConfirm = async () => {
    if (!mediaTrackId) return
    setSaving(true)
    setError(null)
    try {
      const response = await api.put<MediaTrackResponse>(
        `/api/tracking/${mediaTrackId}/review`,
        {
          rating: rating > 0 ? rating : null,
          opinion: opinion.trim() || null,
        }
      )
      onSuccess(response.data)
    } catch (caught) {
      const message =
        isAxiosError(caught) && caught.response?.status === 404
          ? "Este título não está mais na sua lista."
          : (isAxiosError(caught) && caught.response?.data?.message) ||
            "Não foi possível salvar a avaliação. Tente novamente."
      setError(message)
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] max-h-[90svh] overflow-y-auto rounded-[22px] border border-white/10 bg-[#161513] p-6 text-[#f6f4ec] shadow-[0_30px_80px_rgba(0,0,0,.6)] sm:w-full sm:max-w-[420px]"
      >
        <DialogHeader className="gap-1">
          <DialogTitle className="font-display text-[20px] font-bold tracking-tight text-[#f6f4ec]">
            Avalie &quot;{title}&quot;
          </DialogTitle>
          <DialogDescription className="text-[13px] text-[#a6a39a]">
            Seu par já avaliou. Dê sua nota também — ela e a opinião são opcionais.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-5">
          {/* Stars */}
          <div>
            <label className="mb-2 block text-[13px] font-semibold text-[#d8d3c5]">
              Sua nota{" "}
              <span className="font-normal text-[#a6a39a]">(opcional)</span>
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
              <span className="font-normal text-[#a6a39a]">(opcional)</span>
            </label>
            <textarea
              rows={3}
              value={opinion}
              onChange={(e) => setOpinion(e.target.value)}
              placeholder="O que você achou? Alguma cena inesquecível?"
              className="w-full resize-y rounded-xl border border-white/10 bg-[#201e18] px-3.5 py-3.5 text-sm text-[#f6f4ec] outline-none transition-shadow focus:border-[#ffcb2b] focus:shadow-[0_0_0_3px_rgba(255,203,43,.2)]"
            />
          </div>

          {error && (
            <p className="rounded-xl border border-[#ff6b6b]/30 bg-[#ff6b6b]/10 px-3.5 py-2.5 text-[13px] text-[#ffb3b3]">
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
              className="flex-1 rounded-xl border-white/10 bg-transparent py-3.5 text-sm font-semibold text-[#f6f4ec] hover:bg-white/[0.06]"
            >
              Cancelar
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={handleConfirm}
              disabled={saving}
              className="flex-[1.4] rounded-xl border border-[rgba(255,203,43,.35)] bg-[rgba(255,203,43,.14)] py-3.5 text-sm font-bold text-[#ffcb2b] shadow-[0_6px_20px_rgba(255,203,43,.15)] hover:bg-[rgba(255,203,43,.2)] disabled:opacity-60"
            >
              {saving ? "Salvando…" : "Salvar avaliação"}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
