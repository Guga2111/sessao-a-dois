import { Columns2, X } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip"
import { cn } from "@/lib/utils"

const COMPARE_TOOLTIP =
  "Selecione 2 títulos para comparar informações como notas, gêneros e onde assistir."

export function CompareToggleButton({
  active,
  onToggle,
}: {
  active: boolean
  onToggle: () => void
}) {
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger
          render={
            <Button
              type="button"
              variant="outline"
              onClick={onToggle}
              aria-label={active ? "Cancelar comparação" : "Comparar títulos"}
              className={cn(
                "inline-flex cursor-pointer items-center gap-2 rounded-full border px-4 py-2.5 text-[13px] font-semibold transition-colors",
                active
                  ? "border-[rgba(255,203,43,.5)] bg-[rgba(255,203,43,.12)] text-[#ffcb2b] hover:bg-[rgba(255,203,43,.18)] hover:text-[#ffcb2b]"
                  : "border-white/10 bg-[#161513] text-[#f6f4ec] hover:bg-white/[0.06]"
              )}
            />
          }
        >
          {active ? <X className="size-4" /> : <Columns2 className="size-4" />}
          {active ? "Cancelar" : "Comparar"}
        </TooltipTrigger>
        <TooltipContent className="max-w-[240px] rounded-lg border border-[rgba(255,255,255,.1)] bg-[#201e18] px-3 py-2 text-[#f6f4ec] shadow-xl">
          {COMPARE_TOOLTIP}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}

export function CompareSelectionChip({
  count,
  onClear,
  error,
  onRetry,
}: {
  count: number
  onClear: () => void
  error: string | null
  onRetry: () => void
}) {
  return (
    <div className="fixed inset-x-0 bottom-8 z-[35] flex justify-center px-4">
      <div className="flex max-w-[calc(100vw-32px)] flex-col items-center gap-2.5 rounded-2xl border border-white/10 bg-[#161513]/95 px-5 py-3 shadow-[0_20px_50px_rgba(0,0,0,.5)] backdrop-blur-md">
        <div className="flex items-center gap-3">
          <span className="text-[13.5px] font-semibold text-[#f6f4ec]">
            {count}/2 selecionados
          </span>
          <button
            type="button"
            onClick={onClear}
            aria-label="Limpar seleção"
            className="grid size-6 cursor-pointer place-items-center rounded-full bg-white/[0.08] text-[#a6a39a] transition hover:bg-white/[0.14] hover:text-[#f6f4ec]"
          >
            <X className="size-3.5" />
          </button>
        </div>
        {error && (
          <div className="flex items-center gap-2.5 text-[12.5px] text-[#ffb3b3]">
            <span>{error}</span>
            <button
              type="button"
              onClick={onRetry}
              className="cursor-pointer font-semibold text-[#ffcb2b] hover:text-[#ffe08a]"
            >
              Tentar novamente
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
