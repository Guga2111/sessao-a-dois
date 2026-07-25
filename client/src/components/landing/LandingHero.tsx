import { Link } from "react-router-dom"

export function LandingHero() {
  return (
    <main className="mx-auto flex max-w-3xl flex-col items-center gap-6 px-5 py-24 text-center sm:px-8">
      <span className="font-auth-body text-xs font-semibold tracking-[0.14em] text-[#ffcb2b] uppercase">
        Feito para dois
      </span>
      <h1 className="font-display text-[clamp(2rem,5vw,3.5rem)] leading-tight font-extrabold text-[#f6f4ec]">
        A lista de filmes e séries de vocês dois, num só lugar.
      </h1>
      <p className="max-w-xl text-[15px] text-[#a6a39a] sm:text-base">
        Curtam juntos, deem match e acompanhem tudo o que assistem a dois.
      </p>
      <div className="flex flex-wrap items-center justify-center gap-3">
        <Link
          to="/register"
          className="rounded-xl bg-[#ffcb2b] px-6 py-3 text-[15px] font-bold text-[#111] shadow-[0_10px_26px_rgba(255,203,43,.34)] transition-transform hover:-translate-y-px focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#ffcb2b]"
        >
          Começar a dois
        </Link>
        <Link
          to="/login"
          className="rounded-xl border border-white/[0.12] px-6 py-3 text-[15px] font-semibold text-[#f6f4ec] transition-colors hover:bg-white/[0.06] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#ffcb2b]"
        >
          Já temos conta
        </Link>
      </div>
    </main>
  )
}
