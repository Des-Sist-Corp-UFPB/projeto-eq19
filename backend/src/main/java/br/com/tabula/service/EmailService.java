package br.com.tabula.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final String apiKey;
    private final String mailFrom;
    private final HttpClient httpClient;

    public EmailService() {
        this.apiKey = System.getenv("RESEND_API_KEY");
        this.mailFrom = System.getenv("MAIL_FROM");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // Constructor for testing
    public EmailService(String apiKey, String mailFrom) {
        this.apiKey = apiKey;
        this.mailFrom = mailFrom;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendVerificationEmail(String recipientEmail, String recipientName, String token, String backendUrl) {
        String verificationLink = backendUrl + "/api/auth/verify-email?token=" + token;

        if (apiKey == null || apiKey.isBlank() || apiKey.equals("change-me")) {
            LOGGER.warn("[EmailService] RESEND_API_KEY is not configured or set to default. Verification email NOT sent via API.");
            LOGGER.warn("[EmailService] Verification Link for {} ({}): {}", recipientName, recipientEmail, verificationLink);
            return;
        }

        if (mailFrom == null || mailFrom.isBlank()) {
            LOGGER.error("[EmailService] MAIL_FROM environment variable is not configured. Cannot send verification email.");
            throw new IllegalStateException("MAIL_FROM environment variable is not configured.");
        }

        try {
            String cleanFrom = mailFrom.trim();
            String cleanTo = recipientEmail.trim();
            String subject = "Verifique seu e-mail - Tabula";
            
            // Build simple HTML body
            String html = """
                    <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;">
                        <h2 style="color: #4f46e5; margin-bottom: 20px;">Confirme seu cadastro no Tabula</h2>
                        <p>Olá, <strong>%s</strong>!</p>
                        <p>Agradecemos por se cadastrar no Tabula. Para começar a usar a plataforma, por favor clique no botão abaixo para confirmar seu endereço de e-mail:</p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s" style="background-color: #4f46e5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Confirmar E-mail</a>
                        </div>
                        <p style="color: #6b7280; font-size: 0.9em;">Se o botão acima não funcionar, você também pode copiar e colar o seguinte link no seu navegador:</p>
                        <p style="word-break: break-all; color: #4f46e5;"><a href="%s">%s</a></p>
                        <hr style="border: 0; border-top: 1px solid #e0e0e0; margin: 20px 0;" />
                        <p style="color: #9ca3af; font-size: 0.8em; text-align: center;">Este e-mail expira em 24 horas. Se você não solicitou este cadastro, pode ignorar este e-mail.</p>
                    </div>
                    """.formatted(recipientName, verificationLink, verificationLink, verificationLink);

            // Escape strings for JSON manually
            String jsonPayload = """
                    {
                      "from": "%s",
                      "to": ["%s"],
                      "subject": "%s",
                      "html": "%s"
                    }
                    """.formatted(
                            escapeJson(cleanFrom),
                            escapeJson(cleanTo),
                            escapeJson(subject),
                            escapeJson(html)
                    );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            LOGGER.info("[EmailService] Sending verification email to {}...", recipientEmail);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                LOGGER.info("[EmailService] Verification email sent successfully to {} (Status: {})", recipientEmail, response.statusCode());
            } else {
                LOGGER.error("[EmailService] Failed to send verification email to {}. Status: {}, Response: {}", 
                        recipientEmail, response.statusCode(), response.body());
                throw new RuntimeException("Resend API returned status code " + response.statusCode() + ": " + response.body());
            }

        } catch (Exception e) {
            LOGGER.error("[EmailService] Exception occurred while sending verification email to {}", recipientEmail, e);
            throw new RuntimeException("Falha ao enviar e-mail de verificação: " + e.getMessage(), e);
        }
    }

    private static String escapeJson(String val) {
        if (val == null) return "";
        return val.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
