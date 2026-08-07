import { isAxiosError } from "axios"
import { Loader2, ShieldCheck } from "lucide-react"
import { useState } from "react"
import type { FormEvent } from "react"
import { useNavigate } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { PASSWORD_CHANGED_NOTICE } from "@/screens/account/helpers"
import { useAuthStore } from "@/stores/useAuthStore"
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from "@/types/auth"
import type { ChangePasswordRequest } from "@/types/auth"

/** Tempo que o aviso fica visível em /conta antes do redirect para /login. */
const REDIRECT_DELAY_MS = 2400

const CURRENT_ERROR_ID = "conta-senha-atual-erro"
const NEW_ERROR_ID = "conta-senha-nova-erro"
const CONFIRM_ERROR_ID = "conta-senha-confirmacao-erro"
const RANGE_HINT_ID = "conta-senha-faixa"

type FieldErrors = {
  currentPassword?: string
  newPassword?: string
}

const FIELD_LABEL_CLASS =
  "text-[11px] font-semibold tracking-[.08em] text-[#a6a39a] uppercase"

/**
 * A politica da senha e uma JANELA (8 a 72), nao um limiar — entao ela e desenhada como
 * uma janela: a regua mostra onde a senha digitada cai dentro dela, antes do submit, no
 * lugar de uma lista de requisitos que so vira verdade depois de errar.
 */
function LengthRail({ length }: { length: number }) {
  const insideWindow = length >= PASSWORD_MIN_LENGTH
  const filled = Math.min(length / PASSWORD_MAX_LENGTH, 1) * 100
  const minMark = (PASSWORD_MIN_LENGTH / PASSWORD_MAX_LENGTH) * 100

  return (
    <div className="flex flex-col gap-1.5">
      <div className="relative h-1 w-full overflow-hidden rounded-full bg-white/[0.07]">
        <div
          className={
            insideWindow
              ? "h-full rounded-full bg-[#ffcb2b] transition-[width] duration-200"
              : "h-full rounded-full bg-[#6f6c62] transition-[width] duration-200"
          }
          style={{ width: `${filled}%` }}
        />
        <span
          aria-hidden="true"
          className="absolute top-0 h-full w-px bg-[#f6f4ec]/40"
          style={{ left: `${minMark}%` }}
        />
      </div>
      <div id={RANGE_HINT_ID} className="flex justify-between text-[12px] text-[#6f6c62]">
        <span>
          De {PASSWORD_MIN_LENGTH} a {PASSWORD_MAX_LENGTH} caracteres
        </span>
        <span className={insideWindow ? "text-[#ffcb2b]" : undefined}>
          {length} / {PASSWORD_MAX_LENGTH}
        </span>
      </div>
    </div>
  )
}

/**
 * Bloco Senha da tela /conta (epico 9, US-010). Trocar a senha derruba TODAS as sessoes,
 * inclusive esta (E9.8) — o bloco diz isso antes do submit, repete depois e so entao leva
 * para /login, para que a sessao caindo nunca pareca um bug.
 */
export function PasswordSection() {
  const changePassword = useAuthStore((state) => state.changePassword)
  const clearSession = useAuthStore((state) => state.clearSession)
  const navigate = useNavigate()

  const [currentPassword, setCurrentPassword] = useState("")
  const [newPassword, setNewPassword] = useState("")
  const [confirmation, setConfirmation] = useState("")
  const [saving, setSaving] = useState(false)
  const [done, setDone] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)

  const lengthOk =
    newPassword.length >= PASSWORD_MIN_LENGTH && newPassword.length <= PASSWORD_MAX_LENGTH
  const mismatch = confirmation.length > 0 && confirmation !== newPassword
  const canSubmit =
    currentPassword.length > 0 && lengthOk && confirmation === newPassword && !saving

  function resetFeedback() {
    setFieldErrors({})
    setFormError(null)
  }

  function handleFailure(error: unknown) {
    if (isAxiosError(error)) {
      const status = error.response?.status

      if (status === 401) {
        console.warn("senha: senha atual incorreta")
        setFieldErrors({ currentPassword: "Essa não é a sua senha atual." })
        return
      }

      if (status === 400) {
        const body = error.response?.data as
          | { errors?: Partial<Record<keyof ChangePasswordRequest, string>> }
          | undefined
        console.warn("senha: 400 de validacao", body?.errors ?? body)
        setFieldErrors({
          currentPassword: body?.errors?.currentPassword,
          newPassword:
            body?.errors?.newPassword ??
            `A senha nova precisa ter de ${PASSWORD_MIN_LENGTH} a ${PASSWORD_MAX_LENGTH} caracteres.`,
        })
        return
      }

      if (status === 429) {
        console.warn("senha: rate limit de troca de senha atingido")
        setFormError("Você trocou a senha muitas vezes seguidas. Tente de novo mais tarde.")
        return
      }
    }

    console.error("senha: falha ao trocar", error)
    setFormError("Não foi possível trocar a senha agora. Tente de novo.")
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Confirmacao divergente para no cliente: a API nem chega a ser chamada.
    if (!canSubmit) {
      return
    }

    resetFeedback()
    setSaving(true)

    try {
      await changePassword({ currentPassword, newPassword })
      setCurrentPassword("")
      setNewPassword("")
      setConfirmation("")
      setDone(true)
      // O aviso fica visivel aqui antes de o app se desmontar sozinho; so depois o
      // estado local cai (WebSocket incluso) e a rota vai para /login com a explicacao.
      setTimeout(() => {
        clearSession()
        navigate("/login", { replace: true, state: { notice: PASSWORD_CHANGED_NOTICE } })
      }, REDIRECT_DELAY_MS)
    } catch (error) {
      handleFailure(error)
    } finally {
      setSaving(false)
    }
  }

  if (done) {
    return (
      <div
        role="status"
        aria-live="polite"
        className="flex flex-col gap-3 rounded-[14px] border border-[#ffcb2b]/25 bg-[#ffcb2b]/[0.06] px-5 py-4"
      >
        <div className="flex items-center gap-2.5">
          <ShieldCheck aria-hidden="true" className="size-5 flex-none text-[#ffcb2b]" />
          <p className="font-display m-0 text-[17px] font-bold tracking-tight text-[#f6f4ec]">
            Senha trocada. Todas as sessões foram encerradas.
          </p>
        </div>
        <p className="m-0 max-w-[56ch] text-[14px] leading-relaxed text-[#a6a39a]">
          Isso inclui esta aba e qualquer aparelho que ainda estivesse conectado — se alguém
          mais tinha acesso à sua conta, acabou de perder.
        </p>
        <p className="m-0 flex items-center gap-2 text-[13px] text-[#6f6c62]">
          <Loader2 aria-hidden="true" className="size-3.5 animate-spin text-[#ffcb2b]" />
          Levando você para entrar de novo…
        </p>
      </div>
    )
  }

  return (
    <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
      <label className="flex max-w-[420px] flex-col gap-1.5">
        <span className={FIELD_LABEL_CLASS}>Senha atual</span>
        <Input
          type="password"
          value={currentPassword}
          autoComplete="current-password"
          aria-invalid={fieldErrors.currentPassword ? true : undefined}
          aria-describedby={fieldErrors.currentPassword ? CURRENT_ERROR_ID : undefined}
          onChange={(event) => {
            setCurrentPassword(event.target.value)
            resetFeedback()
          }}
          placeholder="••••••••"
        />
        {fieldErrors.currentPassword ? (
          <p id={CURRENT_ERROR_ID} role="alert" className="m-0 text-[13px] text-[#ff8f7c]">
            {fieldErrors.currentPassword}
          </p>
        ) : null}
      </label>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label className="flex flex-col gap-1.5">
          <span className={FIELD_LABEL_CLASS}>Senha nova</span>
          <Input
            type="password"
            value={newPassword}
            autoComplete="new-password"
            maxLength={PASSWORD_MAX_LENGTH}
            aria-invalid={fieldErrors.newPassword ? true : undefined}
            aria-describedby={
              fieldErrors.newPassword ? `${NEW_ERROR_ID} ${RANGE_HINT_ID}` : RANGE_HINT_ID
            }
            onChange={(event) => {
              setNewPassword(event.target.value)
              resetFeedback()
            }}
            placeholder="••••••••"
          />
          <LengthRail length={newPassword.length} />
          {fieldErrors.newPassword ? (
            <p id={NEW_ERROR_ID} role="alert" className="m-0 text-[13px] text-[#ff8f7c]">
              {fieldErrors.newPassword}
            </p>
          ) : null}
        </label>

        <label className="flex flex-col gap-1.5">
          <span className={FIELD_LABEL_CLASS}>Repita a senha nova</span>
          <Input
            type="password"
            value={confirmation}
            autoComplete="new-password"
            maxLength={PASSWORD_MAX_LENGTH}
            aria-invalid={mismatch ? true : undefined}
            aria-describedby={mismatch ? CONFIRM_ERROR_ID : undefined}
            onChange={(event) => {
              setConfirmation(event.target.value)
              resetFeedback()
            }}
            placeholder="••••••••"
          />
          {mismatch ? (
            <p id={CONFIRM_ERROR_ID} role="alert" className="m-0 text-[13px] text-[#ff8f7c]">
              As duas senhas novas ainda não são iguais.
            </p>
          ) : null}
        </label>
      </div>

      {formError ? (
        <p
          role="alert"
          className="m-0 rounded-[12px] border border-[rgba(255,92,71,.28)] bg-[rgba(255,92,71,.08)] px-3.5 py-2.5 text-[13px] text-[#ff8f7c]"
        >
          {formError}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-white/[0.07] pt-4">
        <p className="m-0 flex min-w-0 flex-wrap items-center gap-2 text-[13px] text-[#6f6c62]">
          <span className="text-[#a6a39a]">Ao salvar:</span>
          <span className="inline-flex items-center gap-1.5">
            <span aria-hidden="true" className="size-1.5 rounded-full bg-[#ff5c47]" />
            esta aba sai
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span aria-hidden="true" className="size-1.5 rounded-full bg-[#ff5c47]" />
            os outros aparelhos também
          </span>
        </p>

        <Button
          type="submit"
          disabled={!canSubmit}
          className="bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffe08a] disabled:opacity-50"
        >
          {saving ? "Trocando…" : "Trocar senha"}
        </Button>
      </div>
    </form>
  )
}
