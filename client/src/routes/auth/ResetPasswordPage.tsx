import { isAxiosError } from "axios"
import { useState } from "react"
import type { FormEvent } from "react"
import { Link, useNavigate, useSearchParams } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { api } from "@/lib/api"
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from "@/types/auth"
import type { ResetPasswordRequest } from "@/types/auth"

import { AuthLayout } from "./AuthLayout"

/** Tempo que a confirmacao fica visivel antes do redirect automatico para /login. */
const REDIRECT_DELAY_MS = 2400

const CONFIRM_ERROR_ID = "redefinir-senha-confirmacao-erro"
const RANGE_HINT_ID = "redefinir-senha-faixa"

const BROKEN_LINK_MESSAGE =
  "Esse link não funciona mais. Ele pode ter expirado, já ter sido usado ou estar incompleto."

/**
 * A politica de senha e uma janela (8 a 72), nao um limiar minimo — mesma regua visual
 * de `screens/account/PasswordSection.tsx`, reusada aqui em vez de importada porque
 * aquele arquivo pertence a uma tela autenticada e este fluxo e publico.
 */
function LengthHint({ length }: { length: number }) {
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
      <div id={RANGE_HINT_ID} className="flex justify-between text-xs text-[#6f6c62]">
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

function BrokenLinkState() {
  return (
    <AuthLayout
      eyebrow="Link expirado"
      title="Esse link não funciona mais"
      subtitle={`${BROKEN_LINK_MESSAGE} Peça um novo para continuar.`}
      footer={
        <>
          Lembrou a senha?{" "}
          <Link to="/login" className="font-medium text-[#ffcb2b]">
            Entrar
          </Link>
        </>
      }
    >
      <Button
        type="button"
        size="lg"
        render={<Link to="/esqueci-senha" />}
        className="w-full bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffe08a]"
      >
        Pedir um novo link
      </Button>
    </AuthLayout>
  )
}

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get("token")
  const navigate = useNavigate()

  const [newPassword, setNewPassword] = useState("")
  const [confirmation, setConfirmation] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [done, setDone] = useState(false)
  const [tokenRejected, setTokenRejected] = useState(false)

  const lengthOk =
    newPassword.length >= PASSWORD_MIN_LENGTH && newPassword.length <= PASSWORD_MAX_LENGTH
  const mismatch = confirmation.length > 0 && confirmation !== newPassword
  const canSubmit = lengthOk && confirmation === newPassword && !isSubmitting

  if (!token) {
    return <BrokenLinkState />
  }

  if (tokenRejected) {
    return <BrokenLinkState />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!canSubmit || !token) {
      return
    }

    setError(null)
    setIsSubmitting(true)
    try {
      await api.post<void, unknown, ResetPasswordRequest>("/api/auth/reset-password", {
        token,
        newPassword,
      })
      setDone(true)
      setTimeout(() => {
        navigate("/login", { replace: true })
      }, REDIRECT_DELAY_MS)
    } catch (submitError) {
      if (isAxiosError(submitError) && submitError.response?.status === 400) {
        setTokenRejected(true)
      } else if (isAxiosError(submitError) && submitError.response?.status === 429) {
        setError("Muitas tentativas. Aguarde um pouco antes de tentar de novo.")
      } else {
        setError("Não foi possível redefinir sua senha agora. Tente novamente.")
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (done) {
    return (
      <AuthLayout
        eyebrow="Prontinho"
        title="Senha redefinida"
        subtitle="Você já pode entrar com a senha nova. Estamos te levando para o login…"
      >
        <Button
          type="button"
          size="lg"
          render={<Link to="/login" />}
          className="w-full bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffe08a]"
        >
          Ir para o login
        </Button>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      eyebrow="Nova senha"
      title="Redefinir senha"
      subtitle="Escolha uma senha nova para a sua conta."
      footer={
        <>
          Lembrou a senha?{" "}
          <Link to="/login" className="font-medium text-[#ffcb2b]">
            Entrar
          </Link>
        </>
      }
    >
      <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-[#a6a39a]">Senha nova</span>
          <Input
            type="password"
            autoComplete="new-password"
            required
            maxLength={PASSWORD_MAX_LENGTH}
            aria-describedby={RANGE_HINT_ID}
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            placeholder="••••••••"
          />
          <LengthHint length={newPassword.length} />
        </label>

        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-medium text-[#a6a39a]">Confirmar senha nova</span>
          <Input
            type="password"
            autoComplete="new-password"
            required
            maxLength={PASSWORD_MAX_LENGTH}
            aria-invalid={mismatch}
            aria-describedby={mismatch ? CONFIRM_ERROR_ID : undefined}
            value={confirmation}
            onChange={(event) => setConfirmation(event.target.value)}
            placeholder="••••••••"
          />
          {mismatch ? (
            <span id={CONFIRM_ERROR_ID} className="text-xs text-[#ff9b9b]">
              As duas senhas ainda não são iguais.
            </span>
          ) : null}
        </label>

        {error ? (
          <p
            role="alert"
            className="rounded-xl border border-[#ff6b6b]/30 bg-[#ff6b6b]/10 px-3 py-2 text-sm text-[#ff9b9b]"
          >
            {error}
          </p>
        ) : null}

        <Button
          type="submit"
          size="lg"
          disabled={!canSubmit}
          className="mt-1 w-full bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffe08a] disabled:opacity-60"
        >
          {isSubmitting ? "Salvando…" : "Redefinir senha"}
        </Button>
      </form>
    </AuthLayout>
  )
}
