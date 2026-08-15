import {
  Heart,
  LayoutDashboard,
  ListChecks,
  NotebookPen,
} from "lucide-react"
import type { LucideIcon } from "lucide-react"

type Feature = {
  icon: LucideIcon
  title: string
  copy: string
}

const FEATURES: Feature[] = [
  {
    icon: ListChecks,
    title: "Lista compartilhada",
    copy: "Assistindo, Queremos Ver e Já Vimos — um único lugar pros dois, sempre em dia.",
  },
  {
    icon: Heart,
    title: "Match em tempo real",
    copy: "Curtiram o mesmo título? Vocês recebem a notícia na hora, os dois juntos.",
  },
  {
    icon: NotebookPen,
    title: "Notas & opiniões individuais",
    copy: "Cada um avalia com sua própria nota e sua própria opinião, lado a lado.",
  },
  {
    icon: LayoutDashboard,
    title: "Dashboard do casal",
    copy: "Horas juntos, filmes vs. séries e gêneros favoritos — o retrato do que vocês assistem.",
  },
]

export function FeatureGrid() {
  return (
    <section
      id="recursos"
      className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20 lg:py-24"
    >
      <div className="mb-12 text-center sm:mb-16">
        <span className="font-auth-body text-xs font-semibold tracking-[0.14em] text-primary uppercase">
          Recursos
        </span>
        <h2 className="font-display mt-3 text-[clamp(1.75rem,3.5vw,2.5rem)] font-bold text-foreground">
          Feito pra ser dos dois, do começo ao fim
        </h2>
      </div>

      <div
        className="grid gap-[clamp(1rem,2.5vw,1.5rem)]"
        style={{
          gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 260px), 1fr))",
        }}
      >
        {FEATURES.map((feature) => {
          const Icon = feature.icon
          return (
            <div
              key={feature.title}
              className="rounded-[20px] border border-white/7 bg-card p-6 shadow-[var(--shadow-elevation-1)]"
            >
              <div className="grid size-12 flex-none place-items-center rounded-2xl border border-primary/25 bg-background">
                <Icon className="size-5 text-primary" aria-hidden="true" />
              </div>
              <h3 className="font-display mt-5 text-lg font-bold text-foreground">
                {feature.title}
              </h3>
              <p className="font-auth-body mt-2 text-[14px] leading-relaxed text-muted-foreground">
                {feature.copy}
              </p>
            </div>
          )
        })}
      </div>
    </section>
  )
}
