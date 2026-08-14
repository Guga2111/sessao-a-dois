import { Heart } from "lucide-react"
import { Link } from "react-router-dom"

const ANCHOR_LINKS = [
  { href: "#como-funciona", label: "Como funciona" },
  { href: "#recursos", label: "Recursos" },
  { href: "#faq", label: "FAQ" },
]

export function LandingFooter() {
  return (
    <footer className="font-auth-body border-t border-white/[0.07] px-5 py-10 sm:px-8">
      <div className="mx-auto flex max-w-6xl flex-col items-center gap-6 text-center sm:flex-row sm:items-center sm:justify-between sm:text-left">
        <Link
          to="/"
          className="flex items-center gap-2.5 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
        >
          <div className="grid size-8 flex-none place-items-center rounded-lg bg-primary">
            <Heart className="size-3.5 fill-current text-[#111]" aria-hidden="true" />
          </div>
          <span className="font-display text-[15px] font-bold tracking-tight text-foreground">
            Sessão<span className="text-[#ff9e2c]">·</span>a·Dois
          </span>
        </Link>

        <nav
          aria-label="Secoes da pagina"
          className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2"
        >
          {ANCHOR_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            >
              {link.label}
            </a>
          ))}
          <Link
            to="/login"
            className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Entrar
          </Link>
          <Link
            to="/register"
            className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Criar conta
          </Link>
        </nav>
      </div>

      <p className="mt-8 text-center text-xs text-muted-foreground sm:text-left">
        © {new Date().getFullYear()} Sessão a Dois. Feito para casais que
        assistem juntos.
      </p>
    </footer>
  )
}
