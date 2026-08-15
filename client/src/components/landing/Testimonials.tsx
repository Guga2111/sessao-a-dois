type Testimonial = {
  quote: string
  names: string
  together: string
  hueA: number
  hueB: number
}

const TESTIMONIALS: Testimonial[] = [
  {
    quote:
      "A gente sempre discutia sobre o que assistir. Agora é só dar match — e olha que já rolaram uns 20 até hoje.",
    names: "Ana & Léo",
    together: "Juntos há 2 anos",
    hueA: 45,
    hueB: 34,
  },
  {
    quote:
      "O dashboard virou nosso momento favorito de domingo: ver quantas horas de série a gente maratonou juntos.",
    names: "Bia & Rafa",
    together: "Juntos há 8 meses",
    hueA: 18,
    hueB: 200,
  },
  {
    quote:
      "Cada um dá sua nota e escreve sua opinião — finalmente paramos de brigar sobre quem gostou mais do filme.",
    names: "Carol & Duda",
    together: "Juntas há 4 anos",
    hueA: 320,
    hueB: 45,
  },
]

export function Testimonials() {
  return (
    <section className="mx-auto max-w-6xl px-5 py-16 sm:px-8 sm:py-20 lg:py-24">
      <div className="mb-12 text-center sm:mb-16">
        <span className="font-auth-body text-xs font-semibold tracking-[0.14em] text-primary uppercase">
          Quem já usa
        </span>
        <h2 className="font-display mt-3 text-[clamp(1.75rem,3.5vw,2.5rem)] font-bold text-foreground">
          Casais que trocaram a discussão pelo match
        </h2>
      </div>

      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {TESTIMONIALS.map((testimonial) => (
          <figure
            key={testimonial.names}
            className="relative flex flex-col rounded-2xl border border-white/7 bg-card p-6 shadow-[var(--shadow-elevation-1)]"
          >
            <span
              aria-hidden="true"
              className="font-display absolute top-4 right-5 text-5xl leading-none text-primary/20"
            >
              &rdquo;
            </span>
            <blockquote className="font-auth-body relative text-[15px] leading-relaxed text-label-foreground">
              {testimonial.quote}
            </blockquote>
            <figcaption className="mt-6 flex items-center gap-3">
              <div aria-hidden="true" className="relative h-9 w-[46px] flex-none">
                <span
                  className="absolute top-0.5 left-0 size-8 rounded-full border-2 border-card"
                  style={{
                    background: `linear-gradient(160deg, hsl(${testimonial.hueA} 70% 55%), hsl(${testimonial.hueA} 60% 30%))`,
                  }}
                />
                <span
                  className="absolute top-0.5 right-0 size-8 rounded-full border-2 border-card"
                  style={{
                    background: `linear-gradient(160deg, hsl(${testimonial.hueB} 70% 55%), hsl(${testimonial.hueB} 60% 30%))`,
                  }}
                />
              </div>
              <div>
                <div className="font-display text-[14px] font-bold text-foreground">
                  {testimonial.names}
                </div>
                <div className="text-[12px] text-muted-foreground">{testimonial.together}</div>
              </div>
            </figcaption>
          </figure>
        ))}
      </div>
    </section>
  )
}
