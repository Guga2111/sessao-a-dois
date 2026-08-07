// Espelhado a mao a partir de `com.app.user` (nao ha codegen — ver client/CLAUDE.md).

/** Corpo de `PATCH /api/user/me`. Semantica de PATCH: campo ausente nao e alterado. */
export interface UpdateProfileRequest {
  name?: string
  email?: string
}

/** Resposta de `PATCH /api/user/me` (record `UserProfileResponse`). */
export interface UserProfileResponse {
  id: string
  name: string
  email: string
}

/**
 * Corpo de 400 do `GlobalExceptionHandler`: `{ message, errors: { campo: mensagem } }`.
 * As chaves de `errors` sao os nomes dos campos do request.
 */
export interface ValidationErrorResponse {
  message?: string
  errors?: Partial<Record<keyof UpdateProfileRequest, string>>
}
