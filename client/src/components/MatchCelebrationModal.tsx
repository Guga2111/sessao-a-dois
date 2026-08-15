import { useNavigate } from "react-router-dom"

import { Bell } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog"
import { useMatchStore } from "@/stores/useMatchStore"

export function MatchCelebrationModal() {
  const matchOpen = useMatchStore((state) => state.matchOpen)
  const matchData = useMatchStore((state) => state.matchData)
  const closeMatch = useMatchStore((state) => state.closeMatch)
  const navigate = useNavigate()

  const open = matchOpen && matchData !== null

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) closeMatch()
  }

  const handleGoToHub = () => {
    closeMatch()
    navigate("/hub")
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        showCloseButton={false}
        // color-ok: #251e0c e o stop escuro do gradiente de celebracao do match, ilustrativo (nao e cor de interface reutilizada em outro lugar)
        className="z-[70] w-full max-w-[400px] gap-0 rounded-3xl border border-series/35 bg-gradient-to-b from-[#251e0c] to-card p-9 text-center text-foreground shadow-[var(--shadow-elevation-10)] ring-0 sm:max-w-[400px]"
      >
        <DialogTitle className="sr-only">É um Match!</DialogTitle>
        <DialogDescription className="sr-only">
          Vocês dois curtiram este título! Ele foi adicionado automaticamente à
          lista Queremos Ver.
        </DialogDescription>

        <div className="bg-primary bg-clip-text text-[14px] font-extrabold tracking-[.22em] text-transparent uppercase">
          É um Match!
        </div>
        <div className="my-3.5 text-[52px] leading-none">💜</div>
        <h2 className="font-display m-0 mb-2.5 text-2xl font-bold tracking-tight">
          {matchData?.title}
        </h2>
        <p className="text-wrap-pretty m-0 text-sm leading-relaxed text-muted-foreground">
          Vocês dois curtiram este título! Ele foi adicionado automaticamente à
          lista <b className="text-accent">Queremos Ver</b>.
        </p>
        <div className="mt-4.5 mb-6 flex items-center justify-center gap-2 text-[12.5px] text-muted-foreground">
          <Bell className="size-3.5" /> Notificação enviada para os dois
        </div>
        <div className="flex gap-3">
          <Button
            type="button"
            variant="outline"
            onClick={handleGoToHub}
            className="flex-1 rounded-xl border-white/10 bg-transparent py-3.5 text-sm font-semibold text-foreground hover:bg-white/[0.06]"
          >
            Ver na lista
          </Button>
          <Button
            type="button"
            variant="default"
            onClick={closeMatch}
            className="flex-1 rounded-xl border-none bg-primary py-3.5 text-sm font-bold text-on-primary"
          >
            Continuar
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
