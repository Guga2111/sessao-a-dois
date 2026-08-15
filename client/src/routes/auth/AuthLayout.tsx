import { Heart } from "lucide-react"
import type { ReactNode } from "react"
import { Link } from "react-router-dom"

interface AuthLayoutProps {
  eyebrow: string
  title: string
  subtitle: string
  children: ReactNode
  footer?: ReactNode
}

export function AuthLayout({
  eyebrow,
  title,
  subtitle,
  children,
  footer,
}: AuthLayoutProps) {
  return (
    <div
      className="font-auth-body flex min-h-svh items-center justify-center px-5 py-12 text-foreground"
      style={{
        background:
          "radial-gradient(1200px 700px at 78% -8%, rgba(255,203,43,.16), transparent 55%), radial-gradient(1000px 600px at 5% 8%, rgba(255,158,44,.10), transparent 50%), var(--background)",
      }}
    >
      <div className="flex w-full max-w-[440px] flex-col items-center">
        <Link
          to="/login"
          className="mb-8 flex items-center gap-3 no-underline"
        >
          <span
            className="grid size-10 place-items-center rounded-xl bg-primary shadow-[var(--shadow-glow-primary-12)]"
            aria-hidden
          >
            <Heart className="size-[18px] fill-current text-on-primary" />
          </span>
          <span className="font-display text-[19px] font-bold tracking-tight text-foreground">
            Sessão<span className="text-series">·</span>a·Dois
          </span>
        </Link>

        <div className="w-full rounded-3xl border border-white/[0.08] bg-white/[0.045] p-8 shadow-[var(--shadow-elevation-9)] backdrop-blur-2xl sm:p-9">
          <p className="text-xs font-semibold tracking-[0.16em] text-series uppercase">
            {eyebrow}
          </p>
          <h1 className="font-display mt-2 text-2xl font-bold tracking-tight text-balance sm:text-[28px]">
            {title}
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            {subtitle}
          </p>

          <div className="mt-7">{children}</div>
        </div>

        {footer ? (
          <div className="mt-6 text-sm text-muted-foreground">{footer}</div>
        ) : null}
      </div>
    </div>
  )
}
