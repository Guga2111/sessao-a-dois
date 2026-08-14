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
                  ? "border-primary/50 bg-primary/12 text-primary hover:bg-primary/18 hover:text-primary"
                  : "border-white/10 bg-card text-foreground hover:bg-white/[0.06]"
              )}
            />
          }
        >
          {active ? <X className="size-4" /> : <Columns2 className="size-4" />}
          {active ? "Cancelar" : "Comparar"}
        </TooltipTrigger>
        <TooltipContent className="max-w-[240px] rounded-lg border border-border bg-[#201e18] px-3 py-2 text-foreground shadow-xl">
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
      <div className="flex max-w-[calc(100vw-32px)] flex-col items-center gap-2.5 rounded-2xl border border-white/10 bg-card/95 px-5 py-3 shadow-[0_20px_50px_rgba(0,0,0,.5)] backdrop-blur-md">
        <div className="flex items-center gap-3">
          <span className="text-[13.5px] font-semibold text-foreground">
            {count}/2 selecionados
          </span>
          <button
            type="button"
            onClick={onClear}
            aria-label="Limpar seleção"
            className="grid size-6 cursor-pointer place-items-center rounded-full bg-white/[0.08] text-muted-foreground transition hover:bg-white/[0.14] hover:text-foreground"
          >
            <X className="size-3.5" />
          </button>
        </div>
        {error && (
          <div className="flex items-center gap-2.5 text-[12.5px] text-destructive-foreground">
            <span>{error}</span>
            <button
              type="button"
              onClick={onRetry}
              className="cursor-pointer font-semibold text-primary hover:text-accent"
            >
              Tentar novamente
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
