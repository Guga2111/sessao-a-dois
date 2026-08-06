import { ChevronDown, ChevronUp, Search, SlidersHorizontal } from "lucide-react"

import { Button } from "@/components/ui/button"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import {
  Slider,
  SliderControl,
  SliderIndicator,
  SliderThumb,
  SliderTrack,
} from "@/components/ui/slider"
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group"
import { cn } from "@/lib/utils"
import type { MediaGenre } from "@/types/media"

import { CompareToggleButton } from "./compareUi"
import { formatRuntimeRangeLabel, formatVoteRangeLabel } from "./helpers"
import { SORT_OPTIONS } from "./useDiscoverSearch"

const DECADE_OPTIONS = [
  { value: "2020", label: "Anos 2020" },
  { value: "2010", label: "Anos 2010" },
  { value: "2000", label: "Anos 2000" },
  { value: "1990", label: "Anos 1990" },
  { value: "1900", label: "Anterior" },
] as const

const CERTIFICATION_OPTIONS = [
  { value: "Livre", label: "Livre" },
  { value: "10", label: "10" },
  { value: "12", label: "12" },
  { value: "14", label: "14" },
  { value: "16", label: "16" },
  { value: "18", label: "18" },
] as const

export function FiltersPanel({
  filtersOpen,
  setFiltersOpen,
  query,
  setQuery,
  sortBy,
  handleSortChange,
  hasQuery,
  activeFilterCount,
  selectedDecades,
  setSelectedDecades,
  selectedCertifications,
  setSelectedCertifications,
  genres,
  selectedGenres,
  setSelectedGenres,
  voteRange,
  setVoteRange,
  runtimeRange,
  setRuntimeRange,
  handleClearFilters,
  handleApplyFilters,
  searching,
  compareActive,
  onCompareToggle,
}: {
  filtersOpen: boolean
  setFiltersOpen: (open: boolean) => void
  query: string
  setQuery: (query: string) => void
  sortBy: string
  handleSortChange: (value: string | null) => void
  hasQuery: boolean
  activeFilterCount: number
  selectedDecades: string[]
  setSelectedDecades: (decades: string[]) => void
  selectedCertifications: string[]
  setSelectedCertifications: (certifications: string[]) => void
  genres: MediaGenre[]
  selectedGenres: string[]
  setSelectedGenres: (genres: string[]) => void
  voteRange: [number, number]
  setVoteRange: (range: [number, number]) => void
  runtimeRange: [number, number]
  setRuntimeRange: (range: [number, number]) => void
  handleClearFilters: () => void
  handleApplyFilters: () => void
  searching: boolean
  compareActive: boolean
  onCompareToggle: () => void
}) {
  return (
    <Collapsible
      open={filtersOpen}
      onOpenChange={setFiltersOpen}
      className="mx-auto mb-8 max-w-[820px]"
    >
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative w-full sm:flex-1">
          <Search className="pointer-events-none absolute top-1/2 left-4 size-4.5 -translate-y-1/2 text-[#a6a39a]" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Ex.: Coracao de Vidro, Fronteira Norte..."
            className="w-full rounded-2xl border border-white/10 bg-[#161513] py-3.5 pr-4 pl-11 text-sm text-[#f6f4ec] outline-none transition-shadow focus:border-[#ffcb2b] focus:shadow-[0_0_0_3px_rgba(255,203,43,.2)]"
          />
        </div>

        <div className="ml-auto flex items-center gap-3">
          <Select
            items={SORT_OPTIONS}
            value={sortBy}
            onValueChange={handleSortChange}
            disabled={hasQuery}
          >
            <SelectTrigger className="rounded-full border-white/10 bg-[#161513] px-4 py-2.5 text-[13px] font-semibold text-[#f6f4ec] hover:bg-white/[0.06] disabled:cursor-not-allowed disabled:opacity-40 data-[popup-open]:bg-white/[0.06]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent className="border border-white/10 bg-[#161513] text-[#f6f4ec]">
              {SORT_OPTIONS.map((option) => (
                <SelectItem
                  key={option.value}
                  value={option.value}
                  className="data-highlighted:bg-white/[0.08]"
                >
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>

          <CollapsibleTrigger
            disabled={hasQuery}
            className={cn(
              "flex cursor-pointer items-center gap-2 rounded-full border px-4 py-2.5 text-[13px] font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-40",
              filtersOpen || activeFilterCount > 0
                ? "border-[rgba(255,203,43,.5)] bg-[rgba(255,203,43,.12)] text-[#ffcb2b]"
                : "border-white/10 bg-[#161513] text-[#f6f4ec] hover:bg-white/[0.06]"
            )}
          >
            <SlidersHorizontal className="size-4" />
            Filtros
            {activeFilterCount > 0 && (
              <span className="flex size-5 items-center justify-center rounded-full bg-[#ffcb2b] text-[11px] font-bold text-[#09090a]">
                {activeFilterCount}
              </span>
            )}
            {filtersOpen ? (
              <ChevronUp className="size-4" />
            ) : (
              <ChevronDown className="size-4" />
            )}
          </CollapsibleTrigger>

          <CompareToggleButton active={compareActive} onToggle={onCompareToggle} />
        </div>
      </div>

      <CollapsibleContent className="mt-4 overflow-hidden rounded-2xl border border-white/10 bg-[#161513] px-5 py-5 data-[ending-style]:animate-out data-[starting-style]:animate-in data-[ending-style]:fade-out data-[starting-style]:fade-in">
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div>
            <div className="mb-2.5 text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
              Data de lancamento
            </div>
            <ToggleGroup
              multiple
              value={selectedDecades}
              onValueChange={setSelectedDecades}
            >
              {DECADE_OPTIONS.map((option) => (
                <ToggleGroupItem key={option.value} value={option.value}>
                  {option.label}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
          </div>

          <div>
            <div className="mb-2.5 text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
              Classificacao indicativa
            </div>
            <ToggleGroup
              multiple
              value={selectedCertifications}
              onValueChange={setSelectedCertifications}
            >
              {CERTIFICATION_OPTIONS.map((option) => (
                <ToggleGroupItem key={option.value} value={option.value}>
                  {option.label}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
          </div>
        </div>

        {genres.length > 0 && (
          <div className="mt-6 border-t border-white/10 pt-5">
            <div className="mb-2.5 text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
              Generos
            </div>
            <ToggleGroup
              multiple
              value={selectedGenres}
              onValueChange={setSelectedGenres}
            >
              {genres.map((genre) => (
                <ToggleGroupItem key={genre.id} value={String(genre.id)}>
                  {genre.name}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
          </div>
        )}

        <div className="mt-6 grid grid-cols-1 gap-6 border-t border-white/10 pt-5 sm:grid-cols-2">
          <div>
            <div className="mb-3 flex items-center justify-between">
              <span className="text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
                Nota TMDB
              </span>
              <span className="text-[13px] font-bold text-[#ffcb2b]">
                {formatVoteRangeLabel(voteRange)}
              </span>
            </div>
            <Slider
              min={0}
              max={10}
              step={0.5}
              minStepsBetweenValues={1}
              value={voteRange}
              onValueChange={(v) => setVoteRange(v as [number, number])}
            >
              <SliderControl>
                <SliderTrack>
                  <SliderIndicator />
                </SliderTrack>
                <SliderThumb index={0} />
                <SliderThumb index={1} />
              </SliderControl>
            </Slider>
            <div className="mt-1.5 flex justify-between text-[11px] text-[#a6a39a]">
              <span>0</span>
              <span>10</span>
            </div>
          </div>

          <div>
            <div className="mb-3 flex items-center justify-between">
              <span className="text-[11px] font-semibold tracking-[.1em] text-[#a6a39a] uppercase">
                Duracao
              </span>
              <span className="text-[13px] font-bold text-[#ffcb2b] uppercase">
                {formatRuntimeRangeLabel(runtimeRange)}
              </span>
            </div>
            <Slider
              min={0}
              max={240}
              step={5}
              minStepsBetweenValues={1}
              value={runtimeRange}
              onValueChange={(v) => setRuntimeRange(v as [number, number])}
            >
              <SliderControl>
                <SliderTrack>
                  <SliderIndicator />
                </SliderTrack>
                <SliderThumb index={0} />
                <SliderThumb index={1} />
              </SliderControl>
            </Slider>
            <div className="mt-1.5 flex justify-between text-[11px] text-[#a6a39a]">
              <span>0 min</span>
              <span>240 min</span>
            </div>
          </div>
        </div>

        <div className="mt-6 flex items-center justify-end gap-3 border-t border-white/10 pt-5">
          <button
            type="button"
            onClick={handleClearFilters}
            disabled={activeFilterCount === 0}
            className="text-[13px] font-semibold text-[#a6a39a] transition-colors hover:text-[#f6f4ec] disabled:cursor-not-allowed disabled:opacity-40"
          >
            Limpar tudo
          </button>
          <Button
            type="button"
            onClick={handleApplyFilters}
            disabled={hasQuery || searching}
            className="rounded-full bg-[#ffcb2b] px-5 py-2.5 text-[13px] font-bold text-[#09090a] hover:bg-[#ffdd7a] disabled:cursor-not-allowed disabled:opacity-40"
          >
            Aplicar filtros
          </Button>
        </div>
      </CollapsibleContent>
    </Collapsible>
  )
}
