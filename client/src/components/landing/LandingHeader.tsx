import { Heart } from "lucide-react"
import { Link } from "react-router-dom"

const ANCHOR_LINKS = [
  { href: "#como-funciona", label: "Como funciona" },
  { href: "#recursos", label: "Recursos" },
  { href: "#faq", label: "FAQ" },
]

export function LandingHeader() {
  return (
    <header className="font-auth-body sticky top-0 z-40 flex items-center justify-between gap-4 border-b border-white/[0.07] bg-background/72 px-5 py-4 backdrop-blur-xl sm:gap-6 sm:px-8">
      <Link to="/" className="flex min-w-0 items-center gap-3 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
        <div className="grid size-9.5 flex-none place-items-center rounded-xl bg-primary shadow-[var(--shadow-glow-primary-12)]">
          <Heart className="size-[18px] fill-current text-on-primary" aria-hidden="true" />
        </div>
        <span className="font-display truncate text-[19px] font-bold tracking-tight text-foreground">
          Sessão<span className="text-series">·</span>a·Dois
        </span>
      </Link>

      <nav aria-label="Secoes da pagina" className="hidden items-center gap-1 lg:flex">
        {ANCHOR_LINKS.map((link) => (
          <a
            key={link.href}
            href={link.href}
            className="rounded-lg px-3.5 py-2 text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            {link.label}
          </a>
        ))}
      </nav>

      <div className="flex flex-none items-center gap-1.5 sm:gap-2">
        <Link
          to="/login"
          className="rounded-xl px-3 py-2 text-sm font-semibold whitespace-nowrap text-foreground transition-colors hover:bg-white/[0.06] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:px-4"
        >
          Entrar
        </Link>
        <Link
          to="/register"
          className="rounded-xl bg-primary px-3.5 py-2 text-sm font-bold whitespace-nowrap text-on-primary shadow-[var(--shadow-glow-primary-4)] transition-transform hover:-translate-y-px focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary sm:px-4"
        >
          Criar conta
        </Link>
      </div>
    </header>
  )
}
