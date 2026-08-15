import { isAxiosError } from "axios"
import { Trash2 } from "lucide-react"
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

interface DeleteTrackDialogProps {
  track: MediaTrackResponse | null
  onClose: () => void
  onSuccess: (track: MediaTrackResponse) => void
}

export function DeleteTrackDialog({
  track,
  onClose,
  onSuccess,
}: DeleteTrackDialogProps) {
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
        <DeleteTrackDialogContent
          key={track?.id ?? "empty"}
          track={track}
          onClose={onClose}
          onSuccess={onSuccess}
        />
      </DialogContent>
    </Dialog>
  )
}

interface DeleteTrackDialogContentProps {
  track: MediaTrackResponse | null
  onClose: () => void
  onSuccess: (track: MediaTrackResponse) => void
}

function DeleteTrackDialogContent({
  track,
  onClose,
  onSuccess,
}: DeleteTrackDialogContentProps) {
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    if (!track) return
    setDeleting(true)
    setError(null)
    try {
      await api.delete(`/api/tracking/${track.id}`)
      onSuccess(track)
    } catch (caught) {
      const message =
        (isAxiosError(caught) && caught.response?.data?.message) ||
        "Não foi possível excluir o título. Tente novamente."
      setError(message)
      setDeleting(false)
    }
  }

  return (
    <>
      <div className="flex items-start gap-4 p-6 pb-5">
        <div className="grid size-10 flex-none place-items-center rounded-full bg-destructive/12 text-destructive">
          <Trash2 className="size-5" />
        </div>
        <div>
          <DialogTitle className="font-display text-[20px] font-bold tracking-tight">
            Excluir título
          </DialogTitle>
          <DialogDescription className="mt-1 text-[13px] text-muted-foreground">
            Essa ação é compartilhada entre os dois e não pode ser desfeita.
            As avaliações de vocês dois também serão apagadas.
          </DialogDescription>
        </div>
      </div>

      <div className="flex flex-col gap-5 px-6 pb-6">
        {error && (
          <p className="rounded-xl border border-destructive/30 bg-destructive/10 px-3.5 py-2.5 text-[13px] text-destructive-foreground">
            {error}
          </p>
        )}

        <div className="flex gap-3">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={deleting}
            className="flex-1 rounded-xl border-white/10 bg-transparent py-3.5 text-sm font-semibold text-foreground hover:bg-white/[0.06]"
          >
            Cancelar
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={handleConfirm}
            disabled={deleting}
            className="flex-[1.4] rounded-xl border border-destructive/35 bg-destructive/12 py-3.5 text-sm font-bold text-destructive shadow-[var(--shadow-glow-destructive-1)] hover:bg-destructive/18 disabled:opacity-60"
          >
            {deleting ? "Excluindo…" : "Excluir"}
          </Button>
        </div>
      </div>
    </>
  )
}
