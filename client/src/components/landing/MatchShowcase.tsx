import { Heart, Sparkles } from "lucide-react"

const LIKED_TITLE = {
  title: "Nós Dois à Noite",
  year: "2023",
  hue: 45,
}

const VIEWERS = [
  { name: "Ana", from: "from-[#ffcb2b] to-[#c98f00]" },
  { name: "Léo", from: "from-[#ff9e2c] to-[#8a4a00]" },
] as const

function LikedCard({ name, gradient }: { name: string; gradient: string }) {
  return (
    <div className="flex flex-col items-center gap-2.5">
      <div className="relative">
        <span
          aria-hidden="true"
          className="absolute inset-0 rounded-[16px] border-2 border-[#ffcb2b] opacity-0 motion-safe:animate-[sd-match-ring_6s_ease-in-out_infinite]"
        />
        <div className="relative w-[104px] overflow-hidden rounded-[16px] border border-white/[0.07] bg-[#0f0e0c] sm:w-[120px]">
          <div
            className="relative aspect-[3/4]"
            style={{
              background: `linear-gradient(160deg, hsl(${LIKED_TITLE.hue} 42% 24%), hsl(${LIKED_TITLE.hue} 46% 11%))`,
            }}
          >
            <div className="absolute inset-0 grid place-items-center">
              <span
                className="grid size-9 place-items-center rounded-full bg-[#09090a]/70 opacity-100 backdrop-blur-sm motion-safe:animate-[sd-match-like_6s_ease-in-out_infinite]"
                aria-hidden="true"
              >
                <Heart className="size-4 fill-[#ffcb2b] text-[#ffcb2b]" />
              </span>
            </div>
          </div>
        </div>
      </div>
      <div className="flex items-center gap-1.5">
        <span
          className={`size-4 flex-none rounded-full bg-gradient-to-br ${gradient}`}
          aria-hidden="true"
        />
        <span className="font-auth-body text-[12.5px] font-semibold text-[#a6a39a]">
          {name} curtiu
        </span>
      </div>
    </div>
  )
}

export function MatchShowcase() {
  return (
    <section className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20 lg:py-24">
      <div className="mb-12 text-center sm:mb-16">
        <span className="font-auth-body text-xs font-semibold tracking-[0.14em] text-[#ffcb2b] uppercase">
          O momento Match
        </span>
        <h2 className="font-display mt-3 text-[clamp(1.75rem,3.5vw,2.5rem)] font-bold text-[#f6f4ec]">
          Quando os dois curtem o mesmo título, dá Match
        </h2>
        <p className="font-auth-body mx-auto mt-3 max-w-md text-[15px] text-[#a6a39a]">
          Sem combinar antes: cada um curte na sua tela e, se bater, os dois ficam sabendo na hora.
        </p>
      </div>

      <div className="relative mx-auto flex max-w-lg flex-col items-center">
        <div className="flex w-full items-center justify-center gap-6 sm:gap-10">
          <LikedCard name={VIEWERS[0].name} gradient={VIEWERS[0].from} />

          <div className="relative grid size-16 flex-none place-items-center sm:size-20">
            <span
              aria-hidden="true"
              className="absolute inset-0 rounded-full bg-[#ffcb2b]/20 opacity-0 blur-lg motion-safe:animate-[sd-match-burst_6s_ease-in-out_infinite]"
            />
            <Heart
              aria-hidden="true"
              className="relative size-8 fill-[#ffcb2b] text-[#ffcb2b] opacity-100 sm:size-9 motion-safe:animate-[sd-match-burst_6s_ease-in-out_infinite]"
            />
            <Sparkles
              aria-hidden="true"
              className="absolute -top-1 -right-1 size-4 text-[#ffe08a] opacity-0 motion-safe:animate-[sd-match-sparkle_6s_ease-in-out_infinite]"
            />
            <Sparkles
              aria-hidden="true"
              className="absolute -bottom-1 -left-1 size-3.5 text-[#ffe08a] opacity-0 motion-safe:animate-[sd-match-sparkle_6s_ease-in-out_infinite_.3s]"
            />
          </div>

          <LikedCard name={VIEWERS[1].name} gradient={VIEWERS[1].from} />
        </div>

        <span
          className="font-display mt-5 text-lg font-extrabold text-[#ffdd7a] opacity-100 motion-safe:animate-[sd-match-burst_6s_ease-in-out_infinite]"
          role="status"
        >
          Deu Match!
        </span>

        <div
          className="mt-4 flex items-center gap-2.5 rounded-full border border-[#ffcb2b]/25 bg-[#161513] py-2 pr-4 pl-2.5 opacity-100 motion-safe:animate-[sd-match-pill_6s_ease-in-out_infinite]"
        >
          <span className="rounded-full bg-[#ffcb2b]/12 px-2.5 py-1 text-[11px] font-semibold text-[#ffdd7a]">
            Queremos Ver
          </span>
          <span className="font-auth-body text-[13px] font-semibold text-[#f6f4ec]">
            {LIKED_TITLE.title}
          </span>
        </div>
      </div>
    </section>
  )
}
