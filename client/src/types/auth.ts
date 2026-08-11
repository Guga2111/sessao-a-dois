// Espelhado a mao a partir de `com.app.auth` (nao ha codegen — ver client/CLAUDE.md).

/** Politica da senha nova, identica a do cadastro (`@Size(min = 8, max = 72)`). */
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 72

/** Corpo de `PUT /api/auth/password` (record `ChangePasswordRequest`). Responde 204. */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** Corpo de `POST /api/auth/forgot-password` (record `ForgotPasswordRequest`). Responde 202 sempre. */
export interface ForgotPasswordRequest {
  email: string
}

/** Corpo de `POST /api/auth/reset-password` (record `ResetPasswordRequest`). Responde 204, sem cookie de sessao. */
export interface ResetPasswordRequest {
  token: string
  newPassword: string
}
