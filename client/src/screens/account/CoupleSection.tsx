import { isAxiosError } from "axios"
import { HeartCrack, Link2Off, Loader2 } from "lucide-react"
import { useEffect, useState } from "react"
import { Link, useNavigate } from "react-router-dom"

import { CoupleAvatars } from "@/components/CoupleAvatars"
import { Button } from "@/components/ui/button"
import { useAuthStore } from "@/stores/useAuthStore"
import type { Couple } from "@/stores/useAuthStore"

const MS_PER_DAY = 86_400_000

function initialOf(name: string | undefined): string {
  return name?.trim().charAt(0).toUpperCase() || "?"
}

function formatStartDate(iso: string): string | null {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return date.toLocaleDateString("pt-BR", {
    day: "numeric",
    month: "long",
    year: "numeric",
  })
}

function daysSince(iso: string, now: number): number | null {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return null
  }
  return Math.max(0, Math.floor((now - date.getTime()) / MS_PER_DAY))
}

/**
 * O elemento de assinatura do bloco: o vinculo desenhado como uma LINHA entre os dois —
 * ambar e continua enquanto existe, coral e partida no meio quando a tela pergunta se
 * pode desfaze-lo. E o mesmo desenho nos dois lugares, e a unica coisa que muda e
 * exatamente o que a acao muda.
 */
function BondLine({ label, severed = false }: { label: string; severed?: boolean }) {
  return (
    <div className="flex min-w-0 items-center gap-2">
      <span
        aria-hidden="true"
        className={
          severed
            ? "h-px w-8 flex-none bg-coral-fade-h sm:w-12"
            : "h-px w-8 flex-none bg-primary-fade-h sm:w-12"
        }
      />
      <span
        className={
          severed
            ? "inline-flex flex-none items-center gap-1.5 rounded-full border border-coral/30 bg-coral/8 px-2.5 py-1 text-[11px] font-semibold tracking-[.02em] text-coral-chip"
            : "inline-flex flex-none items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-2.5 py-1 text-[11px] font-semibold tracking-[.02em] text-primary"
        }
      >
        {label}
      </span>
      <span
        aria-hidden="true"
        className={
          severed
            ? "h-px w-8 flex-none border-t border-dashed border-coral/45 sm:w-12"
            : "h-px w-8 flex-none bg-primary-fade-h-reverse sm:w-12"
        }
      />
    </div>
  )
}

interface DissolveDialogProps {
  partnerName: string
  bondLabel: string | null
  onClose: () => void
  onConfirmed: () => void
}

/**
 * Confirmacao explicita da dissolucao, no mesmo padrao estrutural do `DeleteTrackDialog`
 * (backdrop `fixed inset-0` proprio + Escape), com o peso maior que a acao pede: o dialogo
 * enumera o que acontece antes de oferecer o botao.
 */
function DissolveDialog({
  partnerName,
  bondLabel,
  onClose,
  onConfirmed,
}: DissolveDialogProps) {
  const dissolveCouple = useAuthStore((state) => state.dissolveCouple)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose()
    }
    document.addEventListener("keydown", onKeyDown)
    return () => document.removeEventListener("keydown", onKeyDown)
  }, [onClose])

  async function handleConfirm() {
    setWorking(true)
    setError(null)
    try {
      await dissolveCouple()
      onConfirmed()
    } catch (caught) {
      if (isAxiosError(caught)) {
        const status = caught.response?.status
        if (status === 404) {
          console.warn("casal: dissolucao sem casal ativo")
          setError("Você já não está em um casal. Atualize a página para ver o estado novo.")
          setWorking(false)
          return
        }
        if (status === 429) {
          console.warn("casal: rate limit de dissolucao atingido")
          setError("Muitas tentativas seguidas. Espere um pouco e tente de novo.")
          setWorking(false)
          return
        }
      }
      console.error("casal: falha ao desfazer o vinculo", caught)
      setError("Não foi possível desfazer o vínculo agora. Tente de novo.")
      setWorking(false)
    }
  }

  return (
    <div
      onClick={onClose}
      className="fixed inset-0 z-[60] grid place-items-center bg-backdrop/72 p-5 backdrop-blur-md"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="conta-casal-dialogo-titulo"
        onClick={(event) => event.stopPropagation()}
        className="animate-in fade-in zoom-in-95 max-h-[90svh] w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] overflow-y-auto rounded-[22px] border border-coral/28 bg-card text-foreground shadow-[var(--shadow-elevation-10)] duration-200 sm:w-full sm:max-w-[460px]"
      >
        <div className="flex items-start gap-4 p-6 pb-4">
          <div className="grid size-10 flex-none place-items-center rounded-full bg-coral/12 text-coral">
            <HeartCrack aria-hidden="true" className="size-5" />
          </div>
          <div className="min-w-0">
            <h2
              id="conta-casal-dialogo-titulo"
              className="font-display m-0 text-[20px] font-bold tracking-tight text-coral-foreground"
            >
              Desfazer o vínculo com {partnerName}?
            </h2>
            <p className="mt-1.5 text-[13px] leading-relaxed text-muted-foreground">
              Vale para vocês dois, na hora, sem aviso para o outro lado.
            </p>
          </div>
        </div>

        {bondLabel ? (
          <div className="flex justify-center px-6 pb-4">
            <BondLine label={bondLabel} severed />
          </div>
        ) : null}

        <ul className="m-0 flex list-none flex-col gap-2.5 px-6 pb-5 text-[13px] leading-relaxed text-muted-foreground">
          {[
            "O vínculo acaba para os dois — ninguém precisa confirmar do outro lado.",
            "O histórico do casal (títulos, avaliações e notificações) sai do alcance dos dois. Nada é apagado do banco.",
            "Vocês dois ficam livres para formar um casal novo, do zero.",
          ].map((line) => (
            <li key={line} className="flex gap-2.5">
              <span
                aria-hidden="true"
                className="mt-[7px] size-1.5 flex-none rounded-full bg-coral"
              />
              <span>{line}</span>
            </li>
          ))}
        </ul>

        <div className="flex flex-col gap-4 px-6 pb-6">
          {error ? (
            <p
              role="alert"
              className="m-0 rounded-[12px] border border-coral/28 bg-coral/8 px-3.5 py-2.5 text-[13px] text-coral-chip"
            >
              {error}
            </p>
          ) : null}

          <div className="flex flex-wrap gap-3">
            <Button
              type="button"
              variant="outline"
              onClick={onClose}
              disabled={working}
              className="flex-1 rounded-xl border-white/10 bg-transparent py-3.5 text-sm font-semibold text-foreground hover:bg-white/[0.06]"
            >
              Manter o vínculo
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={handleConfirm}
              disabled={working}
              className="flex-[1.4] rounded-xl border border-coral/35 bg-coral/12 py-3.5 text-sm font-bold text-coral shadow-[var(--shadow-glow-coral-1)] hover:bg-coral/18 disabled:opacity-60"
            >
              {working ? (
                <>
                  <Loader2 aria-hidden="true" className="size-4 animate-spin" />
                  Desfazendo…
                </>
              ) : (
                "Desfazer o vínculo"
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

function EmptyBond({ couple }: { couple: Couple | null }) {
  const waitingForPartner = couple !== null && couple.partner === null

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 rounded-[14px] border border-dashed border-white/10 px-4 py-4 sm:px-5">
      <div className="flex min-w-0 items-center gap-3.5">
        <div
          aria-hidden="true"
          className="grid size-10 flex-none place-items-center rounded-full border border-dashed border-white/15 text-tertiary-foreground"
        >
          <Link2Off className="size-[18px]" />
        </div>
        <div className="min-w-0">
          <p className="font-display m-0 text-[16px] font-bold tracking-tight text-foreground">
            {waitingForPartner ? "Seu convite está aberto" : "Você não está em um casal"}
          </p>
          <p className="mt-1 max-w-[46ch] text-[13px] leading-relaxed text-muted-foreground">
            {waitingForPartner
              ? "Falta alguém entrar com o seu código. Enquanto isso, o app fica esperando."
              : "Crie um convite ou entre com o código de quem já criou o seu."}
          </p>
        </div>
      </div>

      <Button
        render={<Link to="/join" />}
        className="rounded-xl bg-primary text-background hover:bg-accent"
      >
        {waitingForPartner ? "Ver o convite" : "Formar um casal"}
      </Button>
    </div>
  )
}

/**
 * Bloco Casal da tela /conta (epico 9, US-011). Mostra o vinculo como ele e — quem, desde
 * quando, ha quanto tempo — e oferece o unico caminho de volta que o app nunca teve.
 */
export function CoupleSection() {
  const user = useAuthStore((state) => state.user)
  const couple = useAuthStore((state) => state.couple)
  const navigate = useNavigate()
  // `Date.now()` direto no render reprova em `react-hooks/purity`: capturar uma vez.
  const [now] = useState(() => Date.now())
  const [confirming, setConfirming] = useState(false)

  const partner = couple?.partner ?? null

  if (!partner || !couple) {
    return <EmptyBond couple={couple ?? null} />
  }

  const startDate = formatStartDate(couple.createdAt)
  const days = daysSince(couple.createdAt, now)
  const bondLabel =
    days === null ? null : days === 1 ? "1 dia juntos" : `${days} dias juntos`

  return (
    <>
      <div className="flex flex-col gap-5">
        <div className="flex flex-wrap items-center gap-4 rounded-[14px] border border-white/[0.07] bg-white/[0.02] px-4 py-4 sm:px-5">
          <CoupleAvatars
            userInitial={initialOf(user?.name)}
            partnerInitial={initialOf(partner.name)}
          />
          <div className="min-w-0 flex-1">
            <p className="font-display m-0 truncate text-[17px] font-bold tracking-tight text-foreground">
              Você e {partner.name}
            </p>
            <p className="mt-1 text-[13px] text-muted-foreground">
              {startDate ? `Juntos no app desde ${startDate}` : "Vínculo ativo"}
            </p>
          </div>
          {bondLabel ? (
            <div className="w-full sm:w-auto">
              <BondLine label={bondLabel} />
            </div>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-white/[0.07] pt-4">
          <p className="m-0 max-w-[52ch] text-[13px] leading-relaxed text-tertiary-foreground">
            Desfazer é imediato e vale para os dois. O histórico não é apagado — ele só
            deixa de ser alcançável por vocês.
          </p>
          <Button
            type="button"
            variant="ghost"
            onClick={() => setConfirming(true)}
            className="rounded-xl border border-coral/35 bg-coral/10 text-sm font-bold text-coral hover:bg-coral/18"
          >
            Desfazer o vínculo
          </Button>
        </div>
      </div>

      {confirming ? (
        <DissolveDialog
          partnerName={partner.name}
          bondLabel={bondLabel}
          onClose={() => setConfirming(false)}
          onConfirmed={() => navigate("/join", { replace: true })}
        />
      ) : null}
    </>
  )
}
