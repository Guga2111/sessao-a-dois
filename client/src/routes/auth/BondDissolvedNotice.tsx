import { Button } from "@/components/ui/button"

/**
 * Aviso estatico do ex-parceiro (US-013, E9.10): o casal foi desfeito pelo outro membro,
 * sem STOMP e sem notificacao — quem chega aqui so viu o vinculo sumir.
 *
 * O elemento de assinatura e a MESMA linha do vinculo do bloco Casal da `/conta`
 * (`screens/account/CoupleSection.tsx`), agora vista depois do corte: os dois lados coral,
 * o da direita tracejado, e no meio, no lugar do chip que contava os dias juntos, o que
 * restou dela — o historico, guardado. Quem recebe o aviso ja conhecia a linha inteira.
 */
export function BondDissolvedNotice({ onDismiss }: { onDismiss: () => void }) {
  return (
    <section
      role="status"
      className="mb-6 rounded-2xl border border-[rgba(255,92,71,.28)] bg-[linear-gradient(180deg,rgba(255,92,71,.07),transparent)] px-5 py-4"
    >
      <h2 className="font-display m-0 text-[15px] font-bold tracking-tight text-[#ffb3a5]">
        O vínculo foi desfeito
      </h2>
      <p className="mt-1.5 mb-0 text-[13px] leading-relaxed text-muted-foreground">
        Seu par encerrou o casal de vocês. O histórico de títulos e avaliações não foi
        apagado — ele continua sendo daquele casal e sai do seu alcance. Um casal novo
        começa do zero.
      </p>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <span
            aria-hidden="true"
            className="h-px w-8 flex-none bg-[linear-gradient(90deg,transparent,rgba(255,92,71,.55))] sm:w-10"
          />
          <span className="inline-flex flex-none items-center rounded-full border border-[rgba(255,92,71,.3)] bg-[rgba(255,92,71,.08)] px-2.5 py-1 text-[11px] font-semibold tracking-[.02em] text-[#ff8f7c]">
            histórico guardado
          </span>
          <span
            aria-hidden="true"
            className="h-px w-8 flex-none border-t border-dashed border-[rgba(255,92,71,.45)] sm:w-10"
          />
        </div>

        <Button
          type="button"
          variant="ghost"
          onClick={onDismiss}
          className="h-8 rounded-lg px-3 text-[13px] font-medium text-muted-foreground hover:bg-white/[0.06] hover:text-foreground"
        >
          Entendi
        </Button>
      </div>
    </section>
  )
}
