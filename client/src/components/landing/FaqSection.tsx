import { Plus } from "lucide-react"

import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"

type Faq = {
  question: string
  answer: string
}

const FAQS: Faq[] = [
  {
    question: "Preciso pagar alguma coisa?",
    answer:
      "Não. Sessão a Dois é gratuito — criar conta, parear com seu par e usar a lista, o Match e o Dashboard não custa nada.",
  },
  {
    question: "Como funciona o código de convite?",
    answer:
      "Quem cria a conta primeiro gera um código único. A outra pessoa entra com esse código na hora do cadastro e as duas contas ficam vinculadas — a partir daí, tudo o que vocês adicionarem pertence ao casal, não a cada conta separada.",
  },
  {
    question: "De onde vêm os dados dos filmes e séries?",
    answer:
      "Da TMDB (The Movie Database), sempre em pt-BR — pôsteres, sinopses, gêneros e onde assistir vêm de lá. Vocês só pesquisam; a busca e os detalhes aparecem prontos.",
  },
  {
    question: "Meus dados são privados?",
    answer:
      "Sim. A lista, as notas e as opiniões ficam visíveis só para vocês dois — o casal vinculado ao código de convite. Ninguém de fora enxerga o que vocês assistem ou avaliam.",
  },
  {
    question: "Cada um dá sua própria nota?",
    answer:
      "Sim. Nota e opinião são individuais — cada pessoa avalia um título com sua própria estrela e seu próprio texto, lado a lado no mesmo card.",
  },
  {
    question: "O que acontece quando dá Match?",
    answer:
      "Quando os dois curtem o mesmo título na busca, ele entra automaticamente na lista \"Queremos Ver\" do casal — sem precisar adicionar na mão.",
  },
]

export function FaqSection() {
  return (
    <section
      id="faq"
      className="mx-auto max-w-3xl px-5 py-16 sm:px-8 sm:py-20 lg:py-24"
    >
      <div className="mb-12 text-center sm:mb-16">
        <span className="font-auth-body text-xs font-semibold tracking-[0.14em] text-[#ffcb2b] uppercase">
          Perguntas frequentes
        </span>
        <h2 className="font-display mt-3 text-[clamp(1.75rem,3.5vw,2.5rem)] font-bold text-[#f6f4ec]">
          Antes de criar conta
        </h2>
      </div>

      <div className="flex flex-col gap-3">
        {FAQS.map((faq) => (
          <Collapsible
            key={faq.question}
            className="rounded-[20px] border border-[rgba(255,255,255,.07)] bg-[#161513] shadow-[0_10px_26px_rgba(0,0,0,.35)]"
          >
            <CollapsibleTrigger className="group flex w-full items-center justify-between gap-4 px-6 py-5 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#ffcb2b]">
              <span className="font-display text-base font-bold text-[#f6f4ec] sm:text-lg">
                {faq.question}
              </span>
              <Plus
                className="size-5 flex-none text-[#ffcb2b] transition-transform duration-200 group-data-[panel-open]:rotate-45"
                aria-hidden="true"
              />
            </CollapsibleTrigger>
            <CollapsibleContent className="h-[var(--collapsible-panel-height)] overflow-hidden transition-[height] duration-200 ease-out data-[starting-style]:h-0 data-[ending-style]:h-0">
              <p className="font-auth-body px-6 pb-5 text-[14px] leading-relaxed text-[#a6a39a] sm:text-[15px]">
                {faq.answer}
              </p>
            </CollapsibleContent>
          </Collapsible>
        ))}
      </div>
    </section>
  )
}
