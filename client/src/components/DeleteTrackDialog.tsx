import { isAxiosError } from "axios"
import { Trash2 } from "lucide-react"
import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"
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
    <DeleteTrackDialogContent
      key={track.id}
      track={track}
      onClose={onClose}
      onSuccess={onSuccess}
    />
  )
}

interface DeleteTrackDialogContentProps {
  track: MediaTrackResponse
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
    <div
      onClick={onClose}
      className="fixed inset-0 z-[60] grid place-items-center bg-[rgba(8,7,11,.72)] p-5 backdrop-blur-md"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="animate-in fade-in zoom-in-95 w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] max-h-[90svh] overflow-y-auto rounded-[22px] border border-white/10 bg-card text-foreground shadow-[0_30px_80px_rgba(0,0,0,.6)] duration-200 sm:w-full sm:max-w-[420px]"
      >
        <div className="flex items-start gap-4 p-6 pb-5">
          <div className="grid size-10 flex-none place-items-center rounded-full bg-[rgba(255,90,90,.12)] text-destructive">
            <Trash2 className="size-5" />
          </div>
          <div>
            <h2 className="font-display text-[20px] font-bold tracking-tight">
              Excluir título
            </h2>
            <p className="mt-1 text-[13px] text-muted-foreground">
              Essa ação é compartilhada entre os dois e não pode ser desfeita.
              As avaliações de vocês dois também serão apagadas.
            </p>
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
              className="flex-[1.4] rounded-xl border border-destructive/35 bg-destructive/12 py-3.5 text-sm font-bold text-destructive shadow-[0_6px_20px_rgba(255,107,107,.15)] hover:bg-destructive/18 disabled:opacity-60"
            >
              {deleting ? "Excluindo…" : "Excluir"}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
