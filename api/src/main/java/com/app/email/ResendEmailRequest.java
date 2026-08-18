package com.app.email;

/**
 * Payload enviado ao POST https://api.resend.com/emails. Contem apenas o necessario
 * para entregar a mensagem - nenhuma senha, nenhum dado extra.
 */
record ResendEmailRequest(String from, String to, String subject, String text, String html) {
}
