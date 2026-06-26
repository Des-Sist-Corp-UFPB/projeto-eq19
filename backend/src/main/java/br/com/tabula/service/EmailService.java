package br.com.tabula.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUser;
    private final String smtpPassword;
    private final String smtpFrom;
    private final String smtpFromName;

    public EmailService() {
        this.smtpHost = getEnvOrDefault("SMTP_HOST", "smtp.gmail.com");
        this.smtpPort = parsePort(getEnvOrDefault("SMTP_PORT", "587"));
        this.smtpUser = System.getenv("SMTP_USER");
        this.smtpPassword = System.getenv("SMTP_PASSWORD");
        this.smtpFrom = getEnvOrDefault("SMTP_FROM", this.smtpUser);
        this.smtpFromName = getEnvOrDefault("SMTP_FROM_NAME", "Tabula");
    }

    public EmailService(String smtpHost, int smtpPort, String smtpUser, String smtpPassword, String smtpFrom, String smtpFromName) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUser = smtpUser;
        this.smtpPassword = smtpPassword;
        this.smtpFrom = smtpFrom;
        this.smtpFromName = smtpFromName;
    }

    public void sendVerificationCode(String recipientEmail, String recipientName, String code) {
        if (isBlank(smtpUser) || isBlank(smtpPassword)) {
            LOGGER.error("[EmailService] SMTP_USER ou SMTP_PASSWORD não configurados. E-mail de verificação não enviado.");
            throw new IllegalStateException("SMTP_USER e SMTP_PASSWORD precisam estar configurados.");
        }

        if (isBlank(smtpFrom)) {
            LOGGER.error("[EmailService] SMTP_FROM não configurado. E-mail de verificação não enviado.");
            throw new IllegalStateException("SMTP_FROM precisa estar configurado.");
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.ssl.trust", smtpHost);
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser.trim(), smtpPassword.trim());
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(
                    smtpFrom.trim(),
                    smtpFromName == null ? "Tabula" : smtpFromName.trim(),
                    StandardCharsets.UTF_8.name()
            ));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail.trim(), false));
            message.setSubject("Código de verificação - Tabula", StandardCharsets.UTF_8.name());
            message.setContent(buildVerificationHtml(recipientName, code), "text/html; charset=UTF-8");

            LOGGER.info("[EmailService] Enviando código de verificação para {} via SMTP {}:{}...", recipientEmail, smtpHost, smtpPort);
            Transport.send(message);
            LOGGER.info("[EmailService] Código de verificação enviado com sucesso para {}.", recipientEmail);
        } catch (Exception e) {
            LOGGER.error("[EmailService] Falha ao enviar código de verificação para {}", recipientEmail, e);
            throw new RuntimeException("Falha ao enviar e-mail de verificação: " + e.getMessage(), e);
        }
    }

    private static String buildVerificationHtml(String recipientName, String code) {
        String safeName = escapeHtml(isBlank(recipientName) ? "jogador" : recipientName.trim());
        String safeCode = escapeHtml(code);

        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f5f3ff;font-family:Arial,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 0;">
                    <tr>
                    <td align="center">
                        <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;padding:36px;box-shadow:0 4px 18px rgba(79,70,229,0.18);">
                        <tr>
                            <td align="center">
                            <div style="font-size:42px;margin-bottom:10px;">🎲</div>
                            <h1 style="margin:0;color:#4f46e5;font-size:28px;">Tabula</h1>
                            <h2 style="margin:24px 0 8px;color:#111827;font-size:22px;">Confirme seu e-mail</h2>

                            <p style="font-size:16px;color:#374151;line-height:1.6;margin:0 0 18px;">
                                Olá, <strong>%s</strong>! Use o código abaixo para ativar sua conta.
                            </p>

                            <div style="margin:28px auto;background:#eef2ff;border:2px dashed #4f46e5;border-radius:14px;width:280px;padding:22px 16px;">
                                <span style="font-size:40px;letter-spacing:10px;color:#4f46e5;font-weight:800;">%s</span>
                            </div>

                            <p style="font-size:14px;color:#6b7280;margin:0;">
                                Este código expira em <strong>15 minutos</strong>.
                            </p>

                            <hr style="margin:30px 0;border:none;border-top:1px solid #e5e7eb;">

                            <p style="font-size:13px;color:#9ca3af;line-height:1.5;margin:0;">
                                Se você não criou uma conta no Tabula, pode ignorar este e-mail.
                            </p>
                            </td>
                        </tr>
                        </table>
                    </td>
                    </tr>
                </table>
                </body>
                </html>
                """.formatted(safeName, safeCode);
    }
    
    private static String getEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return isBlank(value) ? defaultValue : value;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return 587;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}