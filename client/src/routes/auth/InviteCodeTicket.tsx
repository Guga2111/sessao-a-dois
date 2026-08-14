import { useState } from "react"

import { Button } from "@/components/ui/button"
import { useAuthStore } from "@/stores/useAuthStore"

interface InviteCodeTicketProps {
  code: string
  expiresAt: string | null
}

const DAY_MS = 24 * 60 * 60 * 1000
const HOUR_MS = 60 * 60 * 1000
const URGENT_THRESHOLD_MS = DAY_MS

function formatTimeLeft(msLeft: number): string {
  if (msLeft > DAY_MS) {
    const days = Math.ceil(msLeft / DAY_MS)
    return `Expira em ${days} dias`
  }
  const hours = Math.ceil(msLeft / HOUR_MS)
  if (hours <= 1) {
    return "Expira em menos de 1h"
  }
  return `Expira em ${hours}h`
}

export function InviteCodeTicket({ code, expiresAt }: InviteCodeTicketProps) {
  const regenerateInviteCode = useAuthStore((state) => state.regenerateInviteCode)
  const [copied, setCopied] = useState(false)
  const [isRegenerating, setIsRegenerating] = useState(false)
  const [regenerateError, setRegenerateError] = useState<string | null>(null)
  const [now] = useState(() => Date.now())

  const msLeft = expiresAt ? new Date(expiresAt).getTime() - now : null
  const expired = msLeft !== null && msLeft <= 0
  const urgent = msLeft !== null && msLeft > 0 && msLeft <= URGENT_THRESHOLD_MS

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      setCopied(false)
    }
  }

  async function handleRegenerate() {
    setRegenerateError(null)
    setIsRegenerating(true)
    try {
      await regenerateInviteCode()
    } catch {
      setRegenerateError("Não foi possível gerar um novo código agora. Tente de novo.")
    } finally {
      setIsRegenerating(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div
        className={`relative isolate flex overflow-hidden rounded-2xl transition-colors ${
          expired
            ? "bg-card text-foreground shadow-[0_16px_40px_-12px_rgba(0,0,0,.5)]"
            : "bg-primary text-background shadow-[0_16px_40px_-12px_rgba(255,203,43,.45)]"
        }`}
      >
        <div className="flex min-w-0 flex-1 flex-col justify-center gap-1 px-4 py-5 sm:px-5 sm:py-6">
          <span
            className={`text-[9px] font-bold tracking-[0.12em] uppercase sm:text-[10px] sm:tracking-[0.2em] ${
              expired ? "opacity-50" : "opacity-60"
            }`}
          >
            Ingresso · duas cadeiras
          </span>
          <span
            className={`font-display truncate text-base leading-tight font-extrabold sm:text-lg ${
              expired ? "opacity-70" : ""
            }`}
          >
            Sessão a Dois
          </span>
          <span
            className={`text-xs font-medium ${
              expired
                ? "text-[#ff9b9b]"
                : urgent
                  ? "text-[#a3560a]"
                  : "opacity-70"
            }`}
          >
            {expired ? "Ingresso expirado" : msLeft !== null ? formatTimeLeft(msLeft) : "Não expira"}
          </span>
        </div>

        {expired ? (
          <div className="relative flex w-[136px] shrink-0 flex-col items-center justify-center gap-2 border-l-2 border-dashed border-foreground/15 px-3 py-5 sm:py-6">
            <span
              className="absolute -top-2 left-1/2 size-4 -translate-x-1/2 rounded-full bg-background"
              aria-hidden
            />
            <span
              className="absolute -bottom-2 left-1/2 size-4 -translate-x-1/2 rounded-full bg-background"
              aria-hidden
            />
            <span
              className="pointer-events-none absolute top-3 -rotate-[10deg] rounded border-2 border-destructive px-2 py-0.5 text-[10px] font-black tracking-[0.14em] text-destructive uppercase"
              aria-hidden
            >
              Expirado
            </span>
            <Button
              type="button"
              size="xs"
              onClick={handleRegenerate}
              disabled={isRegenerating}
              className="mt-4 rounded-full bg-primary px-2.5 py-1 text-[11px] font-semibold tracking-wide text-background uppercase hover:bg-accent disabled:opacity-60"
            >
              {isRegenerating ? "Gerando…" : "Gerar novo"}
            </Button>
          </div>
        ) : (
          <div className="relative flex w-[104px] shrink-0 flex-col items-center justify-center gap-2 border-l-2 border-dashed border-background/25 px-2 py-5 sm:w-[136px] sm:px-3 sm:py-6">
            <span
              className="absolute -top-2 left-1/2 size-4 -translate-x-1/2 rounded-full bg-[#0d0d0f]"
              aria-hidden
            />
            <span
              className="absolute -bottom-2 left-1/2 size-4 -translate-x-1/2 rounded-full bg-[#0d0d0f]"
              aria-hidden
            />
            <span className="font-mono text-base font-bold tracking-[0.08em] sm:text-[22px] sm:tracking-[0.14em]">
              {code}
            </span>
            <Button
              type="button"
              variant="ghost"
              size="xs"
              onClick={handleCopy}
              className="rounded-full bg-background/10 px-2.5 py-1 text-[11px] font-semibold tracking-wide uppercase hover:bg-background/15"
            >
              {copied ? "Copiado!" : "Copiar"}
            </Button>
          </div>
        )}
      </div>

      {regenerateError ? (
        <p
          role="alert"
          className="rounded-xl border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-[#ff9b9b] text-center"
        >
          {regenerateError}
        </p>
      ) : null}
    </div>
  )
}
