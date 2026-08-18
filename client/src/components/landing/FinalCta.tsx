import { Heart } from "lucide-react"
import { Link } from "react-router-dom"

export function FinalCta() {
  return (
    <section className="relative overflow-hidden px-5 py-20 sm:px-8 sm:py-24 lg:py-28">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0"
        style={{
          background:
            "radial-gradient(900px 480px at 50% 0%, rgba(255,203,43,.14), transparent 60%)",
        }}
      />

      <div className="relative mx-auto flex max-w-2xl flex-col items-center gap-6 text-center">
        <div className="grid size-14 flex-none place-items-center rounded-2xl bg-primary shadow-[var(--shadow-glow-primary-5)]">
          <Heart className="size-6 fill-current text-on-primary" aria-hidden="true" />
        </div>

        <h2 className="font-display text-[clamp(1.75rem,4vw,2.75rem)] leading-tight font-extrabold text-foreground">
          Comecem a assistir a dois hoje
        </h2>
        <p className="max-w-md text-[15px] text-muted-foreground sm:text-base">
          Sem cartão, sem enrolação. Criem a conta, pareiem com o código e a
          lista de vocês dois já começa a se preencher.
        </p>

        <div className="mt-2 flex flex-wrap items-center justify-center gap-3">
          <Link
            to="/register"
            className="rounded-xl bg-primary px-7 py-3.5 text-[15px] font-bold text-on-primary shadow-[var(--shadow-glow-primary-4)] transition-transform hover:-translate-y-px focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Começar a dois
          </Link>
          <Link
            to="/login"
            className="rounded-xl border border-white/[0.12] px-7 py-3.5 text-[15px] font-semibold text-foreground transition-colors hover:bg-white/[0.06] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Já temos conta
          </Link>
        </div>
      </div>
    </section>
  )
}
