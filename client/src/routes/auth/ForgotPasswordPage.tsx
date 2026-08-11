import { isAxiosError } from "axios"
import { useState } from "react"
import type { FormEvent } from "react"
import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { api } from "@/lib/api"
import type { ForgotPasswordRequest } from "@/types/auth"

import { AuthLayout } from "./AuthLayout"

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [sent, setSent] = useState(false)

  const emailInvalid = email.length > 0 && !EMAIL_PATTERN.test(email)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    if (!EMAIL_PATTERN.test(email)) {
      setError("Digite um e-mail válido.")
      return
    }

    setIsSubmitting(true)
    try {
      await api.post<void, unknown, ForgotPasswordRequest>(
        "/api/auth/forgot-password",
        { email },
      )
      setSent(true)
    } catch (submitError) {
      if (isAxiosError(submitError) && submitError.response?.status === 429) {
        setError("Muitas tentativas. Aguarde um pouco antes de pedir outro link.")
      } else {
        setError("Não foi possível enviar o link agora. Tente novamente.")
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  if (sent) {
    return (
      <AuthLayout
        eyebrow="Verifique seu e-mail"
        title="Instruções a caminho"
        subtitle="Se existir uma conta com esse e-mail, enviamos as instruções para redefinir sua senha. Confira também a caixa de spam e se digitou o endereço certo."
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
          render={<Link to="/login" />}
          className="w-full bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffe08a]"
        >
          Voltar para o login
        </Button>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout
      eyebrow="Recuperar acesso"
      title="Esqueci minha senha"
      subtitle="Informe o e-mail da sua conta e enviaremos um link para você criar uma senha nova."
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
          <span className="text-xs font-medium text-[#a6a39a]">E-mail</span>
          <Input
            type="email"
            autoComplete="email"
            required
            aria-invalid={emailInvalid}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="voce@exemplo.com"
          />
          {emailInvalid ? (
            <span className="text-xs text-[#ff9b9b]">
              Esse e-mail não parece válido.
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
          disabled={isSubmitting}
          className="mt-1 w-full bg-[#ffcb2b] text-[#09090a] hover:bg-[#ffe08a] disabled:opacity-60"
        >
          {isSubmitting ? "Enviando…" : "Enviar link de redefinição"}
        </Button>
      </form>
    </AuthLayout>
  )
}
