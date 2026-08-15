import { isAxiosError } from "axios"
import { Check, Loader2 } from "lucide-react"
import { useState } from "react"
import type { FormEvent } from "react"

import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { useAuthStore } from "@/stores/useAuthStore"
import type { UpdateProfileRequest, ValidationErrorResponse } from "@/types/user"

type FieldKey = keyof UpdateProfileRequest

const FIELD_LABEL: Record<FieldKey, string> = {
  name: "Nome",
  email: "E-mail",
}

const EMAIL_ERROR_ID = "conta-perfil-email-erro"
const NAME_ERROR_ID = "conta-perfil-nome-erro"

/**
 * Bloco Perfil da tela /conta (epico 9, US-009). O PATCH so leva os campos que mudaram —
 * a faixa de estado abaixo do formulario mostra exatamente esses campos antes do envio,
 * para que "salvar" nunca seja uma caixa preta.
 */
export function ProfileSection() {
  const user = useAuthStore((state) => state.user)
  const updateProfile = useAuthStore((state) => state.updateProfile)

  const [name, setName] = useState(user?.name ?? "")
  const [email, setEmail] = useState(user?.email ?? "")
  const [saving, setSaving] = useState(false)
  const [savedFields, setSavedFields] = useState<FieldKey[] | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<FieldKey, string>>>({})
  const [formError, setFormError] = useState<string | null>(null)

  const trimmedName = name.trim()
  const trimmedEmail = email.trim()

  const changedFields: FieldKey[] = []
  if (trimmedName !== (user?.name ?? "")) {
    changedFields.push("name")
  }
  if (trimmedEmail !== (user?.email ?? "")) {
    changedFields.push("email")
  }

  const hasChanges = changedFields.length > 0

  function resetFeedback() {
    setSavedFields(null)
    setFieldErrors({})
    setFormError(null)
  }

  function handleFailure(error: unknown) {
    if (isAxiosError(error)) {
      const status = error.response?.status

      if (status === 409) {
        console.warn("perfil: e-mail ja em uso")
        setFieldErrors({ email: "Este e-mail já pertence a outra conta." })
        return
      }

      if (status === 400) {
        const body = error.response?.data as ValidationErrorResponse | undefined
        const errors = body?.errors
        console.warn("perfil: 400 de validacao", errors ?? body)
        if (errors && (errors.name || errors.email)) {
          setFieldErrors({ name: errors.name, email: errors.email })
        } else {
          setFormError("Confira os dados: algum campo não foi aceito.")
        }
        return
      }

      if (status === 429) {
        console.warn("perfil: rate limit de edicao atingido")
        setFormError("Você trocou seus dados muitas vezes seguidas. Tente de novo mais tarde.")
        return
      }
    }

    console.error("perfil: falha ao salvar", error)
    setFormError("Não foi possível salvar agora. Tente de novo.")
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!hasChanges || saving) {
      return
    }

    resetFeedback()
    setSaving(true)

    // Semantica de PATCH: so o que mudou entra no corpo.
    const request: UpdateProfileRequest = {}
    if (changedFields.includes("name")) {
      request.name = trimmedName
    }
    if (changedFields.includes("email")) {
      request.email = trimmedEmail
    }

    const sentFields = [...changedFields]

    try {
      const updated = await updateProfile(request)
      setName(updated.name)
      setEmail(updated.email)
      setSavedFields(sentFields)
    } catch (error) {
      handleFailure(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label className="flex flex-col gap-1.5">
          <span className="text-[11px] font-semibold tracking-[.08em] text-muted-foreground uppercase">
            Nome
          </span>
          <Input
            value={name}
            autoComplete="name"
            maxLength={255}
            aria-invalid={fieldErrors.name ? true : undefined}
            aria-describedby={fieldErrors.name ? NAME_ERROR_ID : undefined}
            onChange={(event) => {
              setName(event.target.value)
              resetFeedback()
            }}
            placeholder="Como você aparece no app"
          />
          {fieldErrors.name ? (
            <p id={NAME_ERROR_ID} role="alert" className="m-0 text-[13px] text-coral-chip">
              {fieldErrors.name}
            </p>
          ) : null}
        </label>

        <label className="flex flex-col gap-1.5">
          <span className="text-[11px] font-semibold tracking-[.08em] text-muted-foreground uppercase">
            E-mail
          </span>
          <Input
            type="email"
            value={email}
            autoComplete="email"
            maxLength={255}
            aria-invalid={fieldErrors.email ? true : undefined}
            aria-describedby={fieldErrors.email ? EMAIL_ERROR_ID : undefined}
            onChange={(event) => {
              setEmail(event.target.value)
              resetFeedback()
            }}
            placeholder="voce@exemplo.com"
          />
          {fieldErrors.email ? (
            <p id={EMAIL_ERROR_ID} role="alert" className="m-0 text-[13px] text-coral-chip">
              {fieldErrors.email}
            </p>
          ) : null}
        </label>
      </div>

      {formError ? (
        <p
          role="alert"
          className="m-0 rounded-chip border border-coral/28 bg-coral/8 px-3.5 py-2.5 text-[13px] text-coral-chip"
        >
          {formError}
        </p>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-white/[0.07] pt-4">
        <div
          role="status"
          aria-live="polite"
          className="flex min-h-[28px] min-w-0 flex-wrap items-center gap-2 text-[13px] text-tertiary-foreground"
        >
          {saving ? (
            <>
              <Loader2 aria-hidden="true" className="size-3.5 animate-spin text-primary" />
              Salvando…
            </>
          ) : savedFields ? (
            <>
              <Check aria-hidden="true" className="size-3.5 text-primary" />
              <span className="text-foreground">
                {savedFields.length === 2
                  ? "Nome e e-mail salvos."
                  : `${FIELD_LABEL[savedFields[0]]} salvo.`}
              </span>
            </>
          ) : hasChanges ? (
            <>
              <span>Vai ser enviado:</span>
              {changedFields.map((field) => (
                <span
                  key={field}
                  className="inline-flex items-center rounded-full border border-primary/30 bg-primary/10 px-2.5 py-0.5 text-[11px] font-semibold tracking-[.02em] text-primary"
                >
                  {FIELD_LABEL[field]}
                </span>
              ))}
            </>
          ) : (
            <span>Nada para salvar por enquanto.</span>
          )}
        </div>

        <Button
          type="submit"
          disabled={!hasChanges || saving}
          className="bg-primary text-background hover:bg-accent disabled:opacity-50"
        >
          {saving ? "Salvando…" : "Salvar alterações"}
        </Button>
      </div>
    </form>
  )
}
