import { isAxiosError } from "axios"
import { Loader2, Lock, LockOpen, Trash2 } from "lucide-react"
import { useState } from "react"
import { useNavigate } from "react-router-dom"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogTitle } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { ACCOUNT_DELETE_CONFIRMATION_WORD } from "@/screens/account/helpers"
import { useAuthStore } from "@/stores/useAuthStore"

/** Tempo que a despedida fica visível antes de o app voltar para a landing. */
const REDIRECT_DELAY_MS = 2200

const PASSWORD_ERROR_ID = "conta-excluir-senha-erro"
const WORD_HINT_ID = "conta-excluir-palavra-dica"

const FIELD_LABEL_CLASS =
  "text-[11px] font-semibold tracking-[.08em] text-muted-foreground uppercase"

const LEAVING = [
  "Sua conta e o seu acesso a ela",
  "Suas avaliações e opiniões",
  "Suas notificações",
  "Todas as sessões abertas, em qualquer aparelho",
]

function staying(partnerName: string | null): string[] {
  return [
    partnerName
      ? `O histórico de títulos do casal, que continua com ${partnerName}`
      : "O histórico de títulos do casal, que continua com quem dividiu o app com você",
    "Seu e-mail, que volta a ficar livre para um cadastro novo — do zero, sem nada do antigo",
  ]
}

/**
 * O elemento de assinatura do bloco: a exclusao e ASSIMETRICA, e o desenho e essa
 * assimetria. Duas colunas separadas por uma regua em gradiente coral -> ambar, uma para o
 * que sai com o usuario e outra para o que fica onde esta. E o que a AC pede ("dizer o que
 * apaga e o que permanece") virado em estrutura, em vez de dois paragrafos seguidos.
 */
function Inventory({ partnerName }: { partnerName: string | null }) {
  return (
    <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 sm:gap-0">
      <div className="sm:pr-6">
        <p className="m-0 text-[11px] font-semibold tracking-[.1em] text-coral-chip uppercase">
          Sai com você
        </p>
        <ul className="m-0 mt-2.5 flex list-none flex-col gap-2 p-0 text-[13px] leading-relaxed text-foreground">
          {LEAVING.map((item) => (
            <li key={item} className="flex gap-2.5">
              <span
                aria-hidden="true"
                className="mt-[7px] h-px w-2.5 flex-none bg-coral"
              />
              <span>{item}</span>
            </li>
          ))}
        </ul>
      </div>

      <div className="relative border-t border-white/[0.07] pt-5 sm:border-t-0 sm:pt-0 sm:pl-6">
        {/* A regua e a fronteira: coral em cima, ambar embaixo — a propria travessia. */}
        <span
          aria-hidden="true"
          className="absolute top-0 left-0 hidden h-full w-px bg-coral-divider-v sm:block"
        />
        <p className="m-0 text-[11px] font-semibold tracking-[.1em] text-primary uppercase">
          Fica onde está
        </p>
        <ul className="m-0 mt-2.5 flex list-none flex-col gap-2 p-0 text-[13px] leading-relaxed text-muted-foreground">
          {staying(partnerName).map((item) => (
            <li key={item} className="flex gap-2.5">
              <span
                aria-hidden="true"
                className="mt-[7px] size-1.5 flex-none rounded-full bg-primary"
              />
              <span>{item}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

/**
 * As duas barreiras (E9.12 + AC) mostradas pelo que elas sao: duas travas com estado real —
 * fechada enquanto segura, aberta quando satisfeita. Nao ha numeracao 01/02 porque a ordem
 * entre elas nao importa; o que importa e quantas ainda seguram.
 */
function Latch({ open, label }: { open: boolean; label: string }) {
  const Icon = open ? LockOpen : Lock
  return (
    <Badge
      tone={open ? "primary" : "neutral"}
      className={
        open
          ? "tracking-normal"
          : "border-white/[0.08] bg-white/[0.04] text-tertiary-foreground tracking-normal"
      }
    >
      <Icon aria-hidden="true" className="size-3" />
      {label}
    </Badge>
  )
}

interface DeleteDialogProps {
  partnerName: string | null
  onClose: () => void
}

function DeleteDialog({ partnerName, onClose }: DeleteDialogProps) {
  const deleteAccount = useAuthStore((state) => state.deleteAccount)
  const clearSession = useAuthStore((state) => state.clearSession)
  const navigate = useNavigate()

  const [password, setPassword] = useState("")
  const [word, setWord] = useState("")
  const [working, setWorking] = useState(false)
  const [done, setDone] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const passwordLatched = password.length > 0
  const wordLatched =
    word.trim().toUpperCase() === ACCOUNT_DELETE_CONFIRMATION_WORD
  const canSubmit = passwordLatched && wordLatched && !working && !done

  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen && !done) onClose()
  }

  function handleFailure(error: unknown) {
    if (isAxiosError(error)) {
      const status = error.response?.status

      // 401 nao fecha o dialogo: a senha errada e um erro do formulario, nao da sessao.
      if (status === 401) {
        console.warn("conta: senha incorreta na exclusao")
        setPasswordError("Essa não é a sua senha. A conta continua no lugar.")
        return
      }

      if (status === 400) {
        const body = error.response?.data as
          | { errors?: { password?: string } }
          | undefined
        console.warn("conta: 400 de validacao na exclusao", body?.errors ?? body)
        setPasswordError(body?.errors?.password ?? "Digite a sua senha.")
        return
      }

      if (status === 429) {
        console.warn("conta: rate limit de exclusao atingido")
        setFormError("Muitas tentativas seguidas. Espere um pouco e tente de novo.")
        return
      }
    }

    console.error("conta: falha ao excluir a conta", error)
    setFormError("Não foi possível excluir a conta agora. Tente de novo.")
  }

  async function handleConfirm() {
    if (!canSubmit) {
      return
    }

    setPasswordError(null)
    setFormError(null)
    setWorking(true)

    try {
      await deleteAccount({ password })
      setPassword("")
      setDone(true)
      // A despedida fica visivel aqui antes de o app se desmontar sozinho; so depois o
      // estado local cai (WebSocket incluso) e a rota volta para a landing.
      setTimeout(() => {
        clearSession()
        navigate("/", { replace: true })
      }, REDIRECT_DELAY_MS)
    } catch (error) {
      handleFailure(error)
      setWorking(false)
    }
  }

  return (
    <Dialog open onOpenChange={handleOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="max-h-[90svh] w-[calc(100vw-32px)] max-w-[calc(100vw-32px)] gap-0 overflow-y-auto rounded-3xl border border-coral/28 bg-card p-0 text-foreground shadow-[var(--shadow-elevation-10)] ring-0 sm:w-full sm:max-w-[560px]"
      >
        <div className="flex items-start gap-4 p-6 pb-4">
          <div className="grid size-10 flex-none place-items-center rounded-full bg-coral/12 text-coral">
            <Trash2 aria-hidden="true" className="size-5" />
          </div>
          <div className="min-w-0">
            <DialogTitle className="font-display m-0 text-[20px] font-bold tracking-tight text-coral-foreground">
              {done ? "Conta excluída." : "Excluir a sua conta?"}
            </DialogTitle>
            <p className="mt-1.5 text-[13px] leading-relaxed text-muted-foreground">
              {done
                ? "Foi tudo embora, agora."
                : "É definitivo. Não existe desfazer, nem no suporte."}
            </p>
          </div>
        </div>

        {done ? (
          <div
            role="status"
            aria-live="polite"
            className="flex flex-col gap-3 px-6 pb-6"
          >
            <p className="m-0 max-w-[56ch] text-[14px] leading-relaxed text-muted-foreground">
              Sua conta, suas avaliações, suas notificações e todas as suas sessões foram
              apagadas. O histórico de títulos do casal ficou onde estava.
            </p>
            <p className="m-0 flex items-center gap-2 text-[13px] text-tertiary-foreground">
              <Loader2 aria-hidden="true" className="size-3.5 animate-spin text-coral" />
              Levando você para o início…
            </p>
          </div>
        ) : (
          <>
            <div className="px-6 pb-5">
              <Inventory partnerName={partnerName} />
            </div>

            <div className="flex flex-col gap-4 border-t border-white/[0.07] px-6 pt-5 pb-6">
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <label className="flex flex-col gap-1.5">
                  <span className={FIELD_LABEL_CLASS}>Sua senha</span>
                  <Input
                    type="password"
                    value={password}
                    autoComplete="current-password"
                    aria-invalid={passwordError ? true : undefined}
                    aria-describedby={passwordError ? PASSWORD_ERROR_ID : undefined}
                    onChange={(event) => {
                      setPassword(event.target.value)
                      setPasswordError(null)
                      setFormError(null)
                    }}
                    placeholder="••••••••"
                  />
                  {passwordError ? (
                    <p
                      id={PASSWORD_ERROR_ID}
                      role="alert"
                      className="m-0 text-[13px] text-coral-chip"
                    >
                      {passwordError}
                    </p>
                  ) : null}
                </label>

                <label className="flex flex-col gap-1.5">
                  <span className={FIELD_LABEL_CLASS}>
                    Digite {ACCOUNT_DELETE_CONFIRMATION_WORD}
                  </span>
                  <Input
                    type="text"
                    value={word}
                    autoComplete="off"
                    autoCapitalize="characters"
                    spellCheck={false}
                    aria-describedby={WORD_HINT_ID}
                    onChange={(event) => setWord(event.target.value)}
                    placeholder={ACCOUNT_DELETE_CONFIRMATION_WORD}
                    className="font-display tracking-[.18em] uppercase"
                  />
                  <p id={WORD_HINT_ID} className="m-0 text-[12px] text-tertiary-foreground">
                    A palavra existe para que ninguém apague uma conta sem querer.
                  </p>
                </label>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <Latch open={passwordLatched} label="Senha" />
                <Latch
                  open={wordLatched}
                  label={ACCOUNT_DELETE_CONFIRMATION_WORD}
                />
              </div>

              {formError ? (
                <p
                  role="alert"
                  className="m-0 rounded-chip border border-coral/28 bg-coral/8 px-3.5 py-2.5 text-[13px] text-coral-chip"
                >
                  {formError}
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
                  Manter minha conta
                </Button>
                <Button
                  type="button"
                  onClick={handleConfirm}
                  disabled={!canSubmit}
                  className="flex-[1.4] rounded-xl border border-coral/35 bg-coral/12 py-3.5 text-sm font-bold text-coral shadow-[var(--shadow-glow-coral-1)] hover:bg-coral/18 disabled:opacity-45"
                >
                  {working ? (
                    <>
                      <Loader2 aria-hidden="true" className="size-4 animate-spin" />
                      Excluindo…
                    </>
                  ) : (
                    "Excluir para sempre"
                  )}
                </Button>
              </div>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}

/**
 * Bloco Excluir conta da tela /conta (epico 9, US-012). Fora do dialogo o bloco fica quieto:
 * o peso todo — o inventario do que sai e do que fica, e as duas barreiras — mora na
 * confirmacao, que e onde a decisao realmente acontece.
 */
export function DeleteAccountSection() {
  const couple = useAuthStore((state) => state.couple)
  const [confirming, setConfirming] = useState(false)

  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="m-0 max-w-[52ch] text-[13px] leading-relaxed text-tertiary-foreground">
          A exclusão pede a sua senha e mais uma confirmação digitada. Antes de concluir,
          a tela lista o que sai com você e o que fica onde está.
        </p>
        <Button
          type="button"
          variant="ghost"
          onClick={() => setConfirming(true)}
          className="rounded-xl border border-coral/35 bg-coral/10 text-sm font-bold text-coral hover:bg-coral/18"
        >
          Excluir minha conta
        </Button>
      </div>

      {confirming ? (
        <DeleteDialog
          partnerName={couple?.partner?.name ?? null}
          onClose={() => setConfirming(false)}
        />
      ) : null}
    </>
  )
}
