import { useNavigate } from "react-router-dom"

import { Bell } from "lucide-react"

import { Button } from "@/components/ui/button"
import { useMatchStore } from "@/stores/useMatchStore"

export function MatchCelebrationModal() {
  const matchOpen = useMatchStore((state) => state.matchOpen)
  const matchData = useMatchStore((state) => state.matchData)
  const closeMatch = useMatchStore((state) => state.closeMatch)
  const navigate = useNavigate()

  if (!matchOpen || !matchData) return null

  const handleGoToHub = () => {
    closeMatch()
    navigate("/")
  }

  return (
    <div
      onClick={closeMatch}
      className="fixed inset-0 z-[70] grid place-items-center bg-[rgba(8,7,11,.82)] p-5 backdrop-blur-md"
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="w-full max-w-[400px] animate-in rounded-[24px] border border-[rgba(255,158,44,.35)] bg-gradient-to-b from-[#251e0c] to-[#161513] p-9 text-center text-[#f6f4ec] shadow-[0_30px_80px_rgba(0,0,0,.6)] duration-300 zoom-in-95 fade-in"
      >
        <div className="bg-[#ffcb2b] bg-clip-text text-[14px] font-extrabold tracking-[.22em] text-transparent uppercase">
          É um Match!
        </div>
        <div className="my-3.5 text-[52px] leading-none">💜</div>
        <h2 className="font-display m-0 mb-2.5 text-2xl font-bold tracking-tight">
          {matchData.title}
        </h2>
        <p className="text-wrap-pretty m-0 text-sm leading-relaxed text-[#a6a39a]">
          Vocês dois curtiram este título! Ele foi adicionado automaticamente à
          lista <b className="text-[#ffe08a]">Queremos Ver</b>.
        </p>
        <div className="mt-4.5 mb-6 flex items-center justify-center gap-2 text-[12.5px] text-muted-foreground">
          <Bell className="size-3.5" /> Notificação enviada para os dois
        </div>
        <div className="flex gap-3">
          <Button
            type="button"
            variant="outline"
            onClick={handleGoToHub}
            className="flex-1 rounded-xl border-white/10 bg-transparent py-3.5 text-sm font-semibold text-[#f6f4ec] hover:bg-white/[0.06]"
          >
            Ver na lista
          </Button>
          <Button
            type="button"
            variant="default"
            onClick={closeMatch}
            className="flex-1 rounded-xl border-none bg-[#ffcb2b] py-3.5 text-sm font-bold text-[#111]"
          >
            Continuar
          </Button>
        </div>
      </div>
    </div>
  )
}
