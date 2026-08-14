import { Star } from "lucide-react"
import { Link } from "react-router-dom"

const PREVIEW_TITLES = [
  {
    title: "Nossas Noites",
    year: "2017",
    genre: "Romance",
    type: "Filme",
    rating: "4,5",
    poster: "https://image.tmdb.org/t/p/w500/u55x5RZ8xP80URk9nfQMmFc1cJM.jpg",
    provider: "https://image.tmdb.org/t/p/w92/pbpMk2JmcoNnQwx5JGpXngfoWtp.jpg",
    providerName: "Netflix",
  },
  {
    title: "Fleabag",
    year: "2016",
    genre: "Drama",
    type: "Série",
    rating: "5,0",
    poster: "https://image.tmdb.org/t/p/w500/27vEYsRKa3eAniwmoccOoluEXQ1.jpg",
    provider: "https://image.tmdb.org/t/p/w92/pvske1MyAoymrs5bguRfVqYiM9a.jpg",
    providerName: "Prime Video",
  },
]

export function LandingHero() {
  return (
    <section className="mx-auto grid max-w-6xl items-center gap-12 px-5 py-16 sm:px-8 sm:py-20 md:grid-cols-[1.05fr_0.95fr] md:gap-10 lg:py-24">
      <div className="flex flex-col items-center gap-6 text-center md:items-start md:text-left">
        <span className="font-auth-body text-xs font-semibold tracking-[0.14em] text-primary uppercase">
          Feito para dois
        </span>
        <h1 className="font-display text-[clamp(2rem,5vw,3.5rem)] leading-tight font-extrabold text-foreground">
          A lista de filmes e séries de vocês dois, num só lugar.
        </h1>
        <p className="max-w-xl text-[15px] text-muted-foreground sm:text-base">
          Curtam juntos, deem match e acompanhem tudo o que assistem a dois.
        </p>
        <div className="flex flex-wrap items-center justify-center gap-3 md:justify-start">
          <Link
            to="/register"
            className="rounded-xl bg-primary px-6 py-3 text-[15px] font-bold text-[#111] shadow-[0_10px_26px_rgba(255,203,43,.34)] transition-transform hover:-translate-y-px focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Começar a dois
          </Link>
          <Link
            to="/login"
            className="rounded-xl border border-white/[0.12] px-6 py-3 text-[15px] font-semibold text-foreground transition-colors hover:bg-white/[0.06] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Já temos conta
          </Link>
        </div>
      </div>

      <div
        aria-hidden="true"
        className="w-full max-w-[420px] justify-self-center rounded-[22px] border border-white/[0.07] bg-card p-4 shadow-[0_30px_70px_rgba(0,0,0,.45)] sm:p-5 md:justify-self-end"
      >
        <div className="flex items-center justify-between gap-3 px-1 pb-4">
          <div className="flex items-center gap-2.5">
            <div className="flex -space-x-2">
              <span className="size-7 rounded-full border-2 border-card bg-gradient-to-br from-primary to-[#c98f00]" />
              <span className="size-7 rounded-full border-2 border-card bg-gradient-to-br from-[#ff9e2c] to-[#8a4a00]" />
            </div>
            <span className="font-auth-body text-[13px] font-semibold text-foreground">
              Lista de vocês dois
            </span>
          </div>
          <span className="rounded-full bg-primary/12 px-2.5 py-1 text-[11px] font-semibold text-[#ffdd7a]">
            Queremos Ver
          </span>
        </div>

        <div className="grid grid-cols-2 gap-3">
          {PREVIEW_TITLES.map((item) => (
            <div
              key={item.title}
              className="overflow-hidden rounded-[14px] border border-white/[0.07] bg-[#0f0e0c]"
            >
              <div className="relative aspect-3/4 overflow-hidden bg-[#1a1816]">
                <img
                  src={item.poster}
                  alt={item.title}
                  className="size-full object-cover"
                  loading="lazy"
                />
                <span className="absolute top-2 left-2 rounded-md bg-background/60 px-2 py-0.5 text-[10px] font-semibold text-foreground backdrop-blur-sm">
                  {item.genre}
                </span>
                <img
                  src={item.provider}
                  alt={item.providerName}
                  className="absolute right-2 bottom-2 size-7 rounded-lg shadow-[0_2px_8px_rgba(0,0,0,.5)]"
                  loading="lazy"
                />
              </div>
              <div className="p-2.5">
                <div className="flex items-baseline justify-between gap-1">
                  <span className="font-display truncate text-[12.5px] font-bold text-foreground">
                    {item.title}
                  </span>
                  <span className="flex-none text-[10.5px] text-muted-foreground">{item.year}</span>
                </div>
                <div className="mt-1.5 flex items-center gap-1">
                  <Star className="size-3 fill-[#ffb443] text-[#ffb443]" aria-hidden="true" />
                  <span className="text-[10.5px] text-muted-foreground">{item.rating}</span>
                  <span className="ml-auto rounded-md border border-primary/25 bg-primary/12 px-1.5 py-0.5 text-[9.5px] font-semibold text-[#ffdd7a]">
                    {item.type}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
