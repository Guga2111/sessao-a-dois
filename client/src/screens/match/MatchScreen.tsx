import { useState } from "react"

import { Header } from "@/components/Header"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

import { SearchTab } from "../MatchScreen"
import { SuggestionsTab } from "./SuggestionsTab"

type ActiveTab = "suggestions" | "search"

export function MatchScreen() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("suggestions")

  return (
    <div
      className="font-auth-body min-h-svh text-[#f6f4ec]"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), #09090a",
      }}
    >
      <Header />
      <main className="mx-auto max-w-[1240px] px-5 pt-10 pb-32 sm:px-8 sm:pt-11">
        <div className="mx-auto mb-9 max-w-[560px] text-center">
          <div className="mb-2 text-[13px] font-semibold tracking-[.14em] text-[#ff9e2c] uppercase">
            Match a Dois
          </div>
          <h1 className="font-display text-[clamp(26px,4vw,38px)] font-bold tracking-tight">
            Descubram o proximo juntos
          </h1>
          <p className="mt-2 text-[15px] text-[#a6a39a]">
            Filtrem o catalogo do TMDB e curtam. Quando os dois curtirem o
            mesmo, vira um match.
          </p>
        </div>

        <div className="mx-auto mb-10 flex w-fit gap-1 rounded-xl bg-[rgba(255,255,255,.06)] p-1">
          <Button
            type="button"
            variant="ghost"
            onClick={() => setActiveTab("search")}
            className={cn(
              "cursor-pointer rounded-lg px-4 py-2 text-[13px] font-semibold transition-colors",
              activeTab === "search"
                ? "bg-[#ffcb2b] text-[#09090a]"
                : "text-[#a6a39a] hover:text-[#f6f4ec]"
            )}
          >
            Descobrir
          </Button>
          <Button
            type="button"
            variant="ghost"
            onClick={() => setActiveTab("suggestions")}
            className={cn(
              "cursor-pointer rounded-lg px-4 py-2 text-[13px] font-semibold transition-colors",
              activeTab === "suggestions"
                ? "bg-[#ffcb2b] text-[#09090a]"
                : "text-[#a6a39a] hover:text-[#f6f4ec]"
            )}
          >
            Sugestoes
          </Button>
        </div>

        {activeTab === "suggestions" && <SuggestionsTab />}
        {activeTab === "search" && <SearchTab />}
      </main>
    </div>
  )
}
