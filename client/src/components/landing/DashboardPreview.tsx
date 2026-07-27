import { CalendarClock, Clapperboard, Sparkles } from "lucide-react"
import { useEffect, useRef, useState } from "react"

const GENRES = [
  { name: "Comédia romântica", percentage: 38 },
  { name: "Suspense", percentage: 27 },
  { name: "Animação", percentage: 19 },
]

const MOVIE_PERCENTAGE = 64
const TV_PERCENTAGE = 36

function useRevealOnView<T extends HTMLElement>() {
  const ref = useRef<T | null>(null)
  const [revealed, setRevealed] = useState(false)

  useEffect(() => {
    const node = ref.current
    if (!node) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setRevealed(true)
          observer.disconnect()
        }
      },
      { threshold: 0.35 },
    )

    observer.observe(node)
    return () => observer.disconnect()
  }, [])

  return { ref, revealed }
}

function KpiTeaser({
  icon,
  iconColor,
  label,
  value,
  suffix,
}: {
  icon: React.ReactNode
  iconColor: string
  label: string
  value: string
  suffix: string
}) {
  return (
    <div className="rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] p-5.5">
      <div className="flex items-center gap-2 text-[13px] font-semibold text-[#a6a39a]">
        <span style={{ color: iconColor }}>{icon}</span> {label}
      </div>
      <div className="font-display mt-3 text-[32px] font-bold tracking-tight text-[#f6f4ec] sm:text-[36px]">
        {value} <span className="text-[16px] text-[#a6a39a] sm:text-[18px]">{suffix}</span>
      </div>
    </div>
  )
}

export function DashboardPreview() {
  const { ref, revealed } = useRevealOnView<HTMLDivElement>()

  return (
    <section className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20 lg:py-24">
      <div className="mb-12 text-center sm:mb-16">
        <span className="font-auth-body text-xs font-semibold tracking-[0.14em] text-[#ffcb2b] uppercase">
          Dashboard do casal
        </span>
        <h2 className="font-display mt-3 text-[clamp(1.75rem,3.5vw,2.5rem)] font-bold text-[#f6f4ec]">
          O retrato do que vocês assistem juntos
        </h2>
        <p className="font-auth-body mx-auto mt-3 max-w-md text-[15px] text-[#a6a39a]">
          Um exemplo do painel que a conta de vocês vai ganhar assim que começarem a assistir juntos.
        </p>
      </div>

      <div
        ref={ref}
        className="grid grid-cols-1 gap-4.5 sm:grid-cols-2 lg:grid-cols-[1fr_1fr_1.4fr]"
      >
        <KpiTeaser
          icon={<CalendarClock className="size-3.5" aria-hidden="true" />}
          iconColor="#ffcb2b"
          label="Tempo juntos"
          value="128h"
          suffix="42m"
        />
        <KpiTeaser
          icon={<Clapperboard className="size-3.5" aria-hidden="true" />}
          iconColor="#ff9e2c"
          label="Filmes vs Séries"
          value={`${MOVIE_PERCENTAGE}%`}
          suffix="filmes"
        />

        <div className="rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] p-5.5 sm:col-span-2 lg:col-span-1">
          <div className="flex items-center gap-2 text-[13px] font-semibold text-[#a6a39a]">
            <Sparkles className="size-3.5 text-[#3ddc97]" aria-hidden="true" />
            Gêneros favoritos
          </div>
          <div className="mt-4 flex flex-col gap-3">
            {GENRES.map((genre) => (
              <div key={genre.name}>
                <div className="mb-1.5 flex justify-between text-[12.5px]">
                  <span className="font-semibold text-[#f6f4ec]">{genre.name}</span>
                  <span className="text-[#a6a39a]">{genre.percentage}%</span>
                </div>
                <div className="h-[8px] rounded-[20px] bg-white/[0.06]">
                  <div
                    className="h-full rounded-[20px] bg-[#3ddc97] motion-safe:transition-[width] motion-safe:duration-700 motion-safe:ease-out"
                    style={{ width: revealed ? `${genre.percentage}%` : "0%" }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="mt-4.5 rounded-[18px] border border-[rgba(255,255,255,.07)] bg-[#161513] p-5.5">
        <div className="mb-3 flex items-center justify-between text-[13px] font-semibold text-[#a6a39a]">
          <span>Filmes vs Séries</span>
          <span>{MOVIE_PERCENTAGE}% · {TV_PERCENTAGE}%</span>
        </div>
        <div className="flex h-2.5 overflow-hidden rounded-[20px] bg-white/[0.06]">
          <div
            className="bg-[#ffcb2b] motion-safe:transition-[width] motion-safe:duration-700 motion-safe:ease-out"
            style={{ width: revealed ? `${MOVIE_PERCENTAGE}%` : "0%" }}
          />
          <div
            className="bg-[#ff9e2c] motion-safe:transition-[width] motion-safe:duration-700 motion-safe:ease-out motion-safe:delay-100"
            style={{ width: revealed ? `${TV_PERCENTAGE}%` : "0%" }}
          />
        </div>
      </div>
    </section>
  )
}
