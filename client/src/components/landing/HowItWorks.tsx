import { Heart, KeyRound, UserPlus } from "lucide-react"
import type { LucideIcon } from "lucide-react"

type Step = {
  icon: LucideIcon
  title: string
  copy: string
}

const STEPS: Step[] = [
  {
    icon: UserPlus,
    title: "Criem a conta",
    copy: "Cada um entra com o próprio e-mail — leva menos de um minuto.",
  },
  {
    icon: KeyRound,
    title: "Pareiem com o código",
    copy: "Um de vocês gera o código de convite, o outro digita. Prontinho, o casal está ligado.",
  },
  {
    icon: Heart,
    title: "Assistam e avaliem juntos",
    copy: "Toda nota e opinião ficam registradas a dois, no mesmo lugar.",
  },
]

export function HowItWorks() {
  return (
    <section
      id="como-funciona"
      className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20 lg:py-24"
    >
      <div className="mb-12 text-center sm:mb-16">
        <span className="font-auth-body text-xs font-semibold tracking-[0.14em] text-primary uppercase">
          Como funciona
        </span>
        <h2 className="font-display mt-3 text-[clamp(1.75rem,3.5vw,2.5rem)] font-bold text-foreground">
          Três passos e vocês já estão a dois
        </h2>
      </div>

      <div className="relative grid grid-cols-1 gap-[clamp(1.5rem,4vw,2.5rem)] sm:grid-cols-3">
        <div
          aria-hidden="true"
          className="absolute top-[29px] right-[calc(16.6667%+1.25rem)] left-[calc(16.6667%+1.25rem)] hidden border-t-2 border-dashed border-primary/25 sm:block"
        />

        {STEPS.map((step, index) => {
          const Icon = step.icon
          return (
            <div key={step.title} className="relative flex flex-col items-center text-center sm:items-start sm:text-left">
              <div className="relative z-10 flex items-center gap-3">
                <div className="grid size-14 flex-none place-items-center rounded-2xl border border-primary/25 bg-card shadow-[var(--shadow-elevation-1)]">
                  <Icon className="size-6 text-primary" aria-hidden="true" />
                </div>
                <span
                  aria-hidden="true"
                  className="font-display text-[13px] font-bold text-muted-foreground"
                >
                  {`0${index + 1}`}
                </span>
              </div>

              <h3 className="font-display mt-5 text-lg font-bold text-foreground">
                {step.title}
              </h3>
              <p className="font-auth-body mt-2 max-w-[26ch] text-[14px] leading-relaxed text-muted-foreground">
                {step.copy}
              </p>
            </div>
          )
        })}
      </div>
    </section>
  )
}
