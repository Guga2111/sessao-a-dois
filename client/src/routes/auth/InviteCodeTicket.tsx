import { useState } from "react"

import { Button } from "@/components/ui/button"

interface InviteCodeTicketProps {
  code: string
}

export function InviteCodeTicket({ code }: InviteCodeTicketProps) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="relative isolate flex overflow-hidden rounded-2xl bg-[#ffcb2b] text-[#09090a] shadow-[0_16px_40px_-12px_rgba(255,203,43,.45)]">
      <div className="flex min-w-0 flex-1 flex-col justify-center gap-1 px-4 py-5 sm:px-5 sm:py-6">
        <span className="text-[9px] font-bold tracking-[0.12em] uppercase opacity-60 sm:text-[10px] sm:tracking-[0.2em]">
          Ingresso · duas cadeiras
        </span>
        <span className="font-display truncate text-base leading-tight font-extrabold sm:text-lg">
          Sessão a Dois
        </span>
        <span className="text-xs font-medium opacity-70">
          Válido até alguém usar
        </span>
      </div>

      <div className="relative flex w-[104px] shrink-0 flex-col items-center justify-center gap-2 border-l-2 border-dashed border-[#09090a]/25 px-2 py-5 sm:w-[136px] sm:px-3 sm:py-6">
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
          className="rounded-full bg-[#09090a]/10 px-2.5 py-1 text-[11px] font-semibold tracking-wide uppercase hover:bg-[#09090a]/15"
        >
          {copied ? "Copiado!" : "Copiar"}
        </Button>
      </div>
    </div>
  )
}
