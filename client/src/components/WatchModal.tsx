import { isAxiosError } from "axios"
import { X } from "lucide-react"
import { useState } from "react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog"
import { api } from "@/lib/api"
import type { MediaTrackResponse } from "@/types/tracking"

interface WatchModalProps {
  track: MediaTrackResponse | null
  onClose: () => void
  onSuccess: () => void
}

export function WatchModal({ track, onClose, onSuccess }: WatchModalProps) {
  const open = track !== null

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) onClose()
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] max-h-[90svh] gap-0 overflow-y-auto rounded-3xl border border-white/10 bg-card p-0 text-foreground shadow-[var(--shadow-elevation-10)] ring-0 sm:w-full sm:max-w-[420px]"
      >
        <DialogTitle className="sr-only">Marcar como visto</DialogTitle>
        <DialogDescription className="sr-only">
          Nota e opinião são opcionais.
        </DialogDescription>

        <WatchModalContent
          key={track?.id ?? "empty"}
          trackId={track?.id ?? null}
          onClose={onClose}
          onSuccess={onSuccess}
        />
      </DialogContent>
    </Dialog>
  )
}

interface WatchModalContentProps {
  trackId: string | null
  onClose: () => void
  onSuccess: () => void
}

function WatchModalContent({ trackId, onClose, onSuccess }: WatchModalContentProps) {
  const [rating, setRating] = useState(0)
  const [opinion, setOpinion] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    if (!trackId) return
    setSaving(true)
    setError(null)
    try {
      await api.patch(`/api/tracking/${trackId}/watch`, {
        rating: rating > 0 ? rating : null,
        opinion: opinion.trim() || null,
      })
      onSuccess()
    } catch (caught) {
      const message =
        (isAxiosError(caught) && caught.response?.data?.message) ||
        "Não foi possível marcar como visto. Tente novamente."
      setError(message)
      setSaving(false)
    }
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between gap-4 p-6 pb-5">
        <div>
          <h2 className="font-display text-[20px] font-bold tracking-tight">
            Marcar como visto
          </h2>
          <p className="mt-1 text-[13px] text-muted-foreground">
            Nota e opinião são opcionais.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          size="icon-sm"
          onClick={onClose}
          aria-label="Fechar"
          className="flex-none rounded-lg border-white/10 text-muted-foreground hover:bg-white/[0.06] hover:text-white"
        >
          <X className="size-4.5" />
        </Button>
      </div>

      <div className="flex flex-col gap-5 px-6 pb-6">
        {/* Stars */}
        <div>
          <label className="mb-2 block text-[13px] font-semibold text-label-foreground">
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
                  color: star <= rating ? "var(--rating)" : "rgba(255,255,255,.18)",
                }}
              >
                ★
              </Button>
            ))}
          </div>
        </div>

        {/* Opinion */}
        <div>
          <label className="mb-2 block text-[13px] font-semibold text-label-foreground">
            Sua opinião{" "}
            <span className="font-normal text-muted-foreground">(opcional)</span>
          </label>
          <textarea
            rows={3}
            value={opinion}
            onChange={(e) => setOpinion(e.target.value)}
            placeholder="O que você achou? Alguma cena inesquecível?"
            className="w-full resize-y rounded-xl border border-white/10 bg-surface-secondary px-3.5 py-3.5 text-sm text-foreground outline-none transition-shadow focus:border-primary focus:shadow-[var(--shadow-glow-primary-2)]"
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
            className="flex-[1.4] rounded-xl border border-success/35 bg-success/12 py-3.5 text-sm font-bold text-success shadow-[var(--shadow-glow-success-1)] hover:bg-success/18 disabled:opacity-60"
          >
            {saving ? "Salvando…" : "Confirmar"}
          </Button>
        </div>
      </div>
    </div>
  )
}
