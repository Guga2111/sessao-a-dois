import { useState } from "react"

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
      <div className="flex min-w-0 flex-1 flex-col justify-center gap-1 px-5 py-6">
        <span className="text-[10px] font-bold tracking-[0.2em] uppercase opacity-60">
          Ingresso · duas cadeiras
        </span>
        <span className="font-display truncate text-lg leading-tight font-extrabold">
          Sessão a Dois
        </span>
        <span className="text-xs font-medium opacity-70">
          Válido até alguém usar
        </span>
      </div>

      <div className="relative flex w-[136px] shrink-0 flex-col items-center justify-center gap-2 border-l-2 border-dashed border-[#09090a]/25 px-3 py-6">
        <span
          className="absolute -top-2 left-1/2 size-4 -translate-x-1/2 rounded-full bg-[#0d0d0f]"
          aria-hidden
        />
        <span
          className="absolute -bottom-2 left-1/2 size-4 -translate-x-1/2 rounded-full bg-[#0d0d0f]"
          aria-hidden
        />
        <span className="font-mono text-[22px] font-bold tracking-[0.14em]">
          {code}
        </span>
        <button
          type="button"
          onClick={handleCopy}
          className="cursor-pointer rounded-full bg-[#09090a]/10 px-2.5 py-1 text-[11px] font-semibold tracking-wide uppercase hover:bg-[#09090a]/15"
        >
          {copied ? "Copiado!" : "Copiar"}
        </button>
      </div>
    </div>
  )
}
