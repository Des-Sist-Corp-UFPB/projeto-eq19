package br.com.tabula.controller;

import br.com.tabula.dto.ChangePasswordRequest;
import br.com.tabula.dto.LoginRequest;
import br.com.tabula.dto.RegisterRequest;
import br.com.tabula.dto.ResetPasswordRequest;
import br.com.tabula.dto.ResendVerificationRequest;
import br.com.tabula.model.UserAccount;
import br.com.tabula.repository.UserRepository;
import br.com.tabula.repository.VerificationTokenRepository;
import br.com.tabula.service.EmailService;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private AuthController() {
    }

    public static void register(Javalin app, HikariDataSource dataSource) {
        UserRepository userRepository = new UserRepository(dataSource);

        app.post("/auth/login", ctx -> {
            try {
                LoginRequest request = ctx.bodyAsClass(LoginRequest.class);
                String email = normalizeEmail(request.getEmail());
                String password = request.getPassword() == null ? "" : request.getPassword();

                if (email.isEmpty() || password.isEmpty()) {
                    ctx.status(400).json(Map.of("error", "E-mail e senha são obrigatórios."));
                    return;
                }

                Optional<UserAccount> account = userRepository.findByEmail(email);
                if (account.isEmpty() || !sha256(password).equals(account.get().getPasswordHash())) {
                    ctx.status(401).json(Map.of("error", "Credenciais inválidas."));
                    return;
                }

                if (!account.get().isEmailVerificado()) {
                    ctx.status(403).json(Map.of("error", "Please verify your email before signing in."));
                    return;
                }

                String token = userRepository.createAuthToken(account.get().getId());
                ctx.json(Map.of(
                        "ok", true,
                        "message", "Login realizado com sucesso.",
                        "token", token,
                        "user", toFrontendUser(account.get())
                ));
            } catch (SQLException ex) {
                LOGGER.error("Failed to login user", ex);
                ctx.status(500).json(Map.of("error", "Não foi possível fazer login no momento."));
            } catch (Exception ex) {
                LOGGER.error("Invalid login payload", ex);
                ctx.status(400).json(Map.of("error", "Dados inválidos para login."));
            }
        });

        app.post("/auth/register", ctx -> {
            try {
                RegisterRequest request = ctx.bodyAsClass(RegisterRequest.class);
                String name = request.getName() == null ? "" : request.getName().trim();
                String email = normalizeEmail(request.getEmail());
                String password = request.getPassword() == null ? "" : request.getPassword();

                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    ctx.status(400).json(Map.of("error", "Nome, e-mail e senha são obrigatórios."));
                    return;
                }

                if (userRepository.emailExists(email)) {
                    ctx.status(409).json(Map.of("error", "Este e-mail já está cadastrado."));
                    return;
                }

                UserAccount account = userRepository.createUser(
                        "u_" + UUID.randomUUID(),
                        name,
                        email,
                        sha256(password),
                        "USER",
                        false // email_verificado = false
                );

                LOGGER.info("Registered user {} ({}) with id {}", name, email, account.getId());

                // Generate secure random verification token (UUID)
                String token = UUID.randomUUID().toString();
                VerificationTokenRepository tokenRepository = new VerificationTokenRepository(dataSource);
                tokenRepository.createToken(account.getId(), token, Instant.now().plus(24, ChronoUnit.HOURS));

                // Send email verification link
                EmailService emailService = new EmailService();
                String backendUrl = buildBackendUrl(ctx);

                try {
                    emailService.sendVerificationEmail(email, name, token, backendUrl);
                    ctx.status(201).json(Map.of(
                            "ok", true,
                            "message", "Conta criada com sucesso. Por favor, verifique seu e-mail para ativar sua conta."
                    ));
                } catch (Exception ex) {
                    LOGGER.error("Failed to send verification email during registration for {}", email, ex);
                    // As requested: Do NOT delete the user. Keep unverified and return clear error
                    ctx.status(201).json(Map.of(
                            "ok", true,
                            "message", "Conta criada com sucesso, mas não foi possível enviar o e-mail de verificação. Por favor, utilize a opção de reenviar verificação para ativar sua conta."
                    ));
                }
            } catch (SQLException ex) {
                LOGGER.error("Failed to register user", ex);
                ctx.status(500).json(Map.of("error", "Não foi possível criar a conta no momento."));
            } catch (Exception ex) {
                LOGGER.error("Invalid registration payload", ex);
                ctx.status(400).json(Map.of("error", "Dados inválidos para cadastro."));
            }
        });

        io.javalin.http.Handler verifyEmailHandler = ctx -> {
            String token = ctx.queryParam("token");
            String frontendUrl = System.getenv("FRONTEND_URL");
            if (frontendUrl == null || frontendUrl.isBlank()) {
                frontendUrl = buildBackendUrl(ctx);
            }

            if (token == null || token.isBlank()) {
                ctx.status(400).html(renderErrorPage("O token de verificação está ausente.", frontendUrl));
                return;
            }

            try {
                VerificationTokenRepository tokenRepository = new VerificationTokenRepository(dataSource);
                Optional<VerificationTokenRepository.TokenInfo> tokenInfoOpt = tokenRepository.findToken(token);

                if (tokenInfoOpt.isEmpty()) {
                    ctx.status(400).html(renderErrorPage("Código de verificação inválido ou já utilizado.", frontendUrl));
                    return;
                }

                VerificationTokenRepository.TokenInfo tokenInfo = tokenInfoOpt.get();
                if (tokenInfo.getExpiresAt().isBefore(Instant.now())) {
                    ctx.status(400).html(renderErrorPage("Este link de verificação expirou (limite de 24 horas).", frontendUrl));
                    return;
                }

                // Activate user
                userRepository.verifyEmail(tokenInfo.getUserId());

                // Invalidate/delete token
                tokenRepository.deleteToken(token);

                ctx.html(renderSuccessPage(frontendUrl));
            } catch (SQLException ex) {
                LOGGER.error("Database error during email verification", ex);
                ctx.status(500).html(renderErrorPage("Erro interno no servidor ao verificar o e-mail.", frontendUrl));
            }
        };
        app.get("/auth/verify-email", verifyEmailHandler);
        app.get("/api/auth/verify-email", verifyEmailHandler);

        app.post("/auth/resend-verification", ctx -> {
            try {
                ResendVerificationRequest request = ctx.bodyAsClass(ResendVerificationRequest.class);
                String email = normalizeEmail(request.getEmail());

                if (email.isEmpty()) {
                    ctx.status(400).json(Map.of("error", "O e-mail é obrigatório."));
                    return;
                }

                Optional<UserAccount> accountOpt = userRepository.findByEmail(email);
                if (accountOpt.isEmpty()) {
                    ctx.status(404).json(Map.of("error", "Nenhuma conta encontrada com este e-mail."));
                    return;
                }

                UserAccount account = accountOpt.get();
                if (account.isEmailVerificado()) {
                    ctx.status(400).json(Map.of("error", "Este e-mail já está verificado."));
                    return;
                }

                // Generate new secure random token
                String token = UUID.randomUUID().toString();
                VerificationTokenRepository tokenRepository = new VerificationTokenRepository(dataSource);

                // Clean up previous tokens
                tokenRepository.deleteTokensByUser(account.getId());

                // Store new token
                tokenRepository.createToken(account.getId(), token, Instant.now().plus(24, ChronoUnit.HOURS));

                // Send email verification link
                EmailService emailService = new EmailService();
                String backendUrl = buildBackendUrl(ctx);

                try {
                    emailService.sendVerificationEmail(account.getEmail(), account.getName(), token, backendUrl);
                    ctx.json(Map.of("ok", true, "message", "E-mail de verificação reenviado com sucesso."));
                } catch (Exception ex) {
                    LOGGER.error("Failed to send verification email during resend for {}", email, ex);
                    ctx.status(500).json(Map.of("error", "Falha ao enviar o e-mail de verificação. Por favor, tente novamente."));
                }
            } catch (Exception ex) {
                LOGGER.error("Invalid resend verification payload", ex);
                ctx.status(400).json(Map.of("error", "Dados inválidos para reenvio de verificação."));
            }
        });

        app.post("/auth/reset-password", ctx -> {
            try {
                ResetPasswordRequest request = ctx.bodyAsClass(ResetPasswordRequest.class);
                String email = normalizeEmail(request.getEmail());
                String newPassword = request.getNewPassword() == null ? "" : request.getNewPassword();

                if (email.isEmpty() || newPassword.isEmpty()) {
                    ctx.status(400).json(Map.of("error", "E-mail e nova senha são obrigatórios."));
                    return;
                }

                if (userRepository.findByEmail(email).isEmpty()) {
                    ctx.status(404).json(Map.of("error", "Nenhuma conta encontrada com este e-mail."));
                    return;
                }

                userRepository.updatePassword(email, sha256(newPassword));
                ctx.json(Map.of("ok", true, "message", "Senha redefinida com sucesso."));
            } catch (SQLException ex) {
                LOGGER.error("Failed to reset password", ex);
                ctx.status(500).json(Map.of("error", "Não foi possível redefinir a senha."));
            }
        });

        app.post("/auth/change-password", ctx -> {
            try {
                ChangePasswordRequest request = ctx.bodyAsClass(ChangePasswordRequest.class);
                String email = normalizeEmail(request.getEmail());
                String currentPassword = request.getCurrentPassword() == null ? "" : request.getCurrentPassword();
                String newPassword = request.getNewPassword() == null ? "" : request.getNewPassword();

                if (email.isEmpty() || currentPassword.isEmpty() || newPassword.isEmpty()) {
                    ctx.status(400).json(Map.of("error", "Senha atual e nova senha são obrigatórias."));
                    return;
                }

                Optional<UserAccount> account = userRepository.findByEmail(email);
                if (account.isEmpty() || !sha256(currentPassword).equals(account.get().getPasswordHash())) {
                    ctx.status(401).json(Map.of("error", "Senha atual incorreta."));
                    return;
                }

                userRepository.updatePassword(email, sha256(newPassword));
                ctx.json(Map.of("ok", true, "message", "Senha alterada com sucesso."));
            } catch (SQLException ex) {
                LOGGER.error("Failed to change password", ex);
                ctx.status(500).json(Map.of("error", "Não foi possível alterar a senha."));
            }
        });
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private static Map<String, Object> toFrontendUser(UserAccount account) {
        boolean admin = "ADMIN".equalsIgnoreCase(account.getRole());
        String[] parts = account.getName().trim().split("\\s+");
        String initials = "U";
        if (parts.length >= 2) {
            initials = (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        } else if (parts.length == 1 && !parts[0].isBlank()) {
            initials = parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }

        return Map.of(
                "id", account.getExternalId(),
                "name", account.getName(),
                "email", account.getEmail(),
                "role", admin ? "admin" : "student",
                "course", admin ? "Administração do clube" : "Sem curso informado",
                "avatar", initials,
                "winCount", 0,
                "favoriteGames", java.util.List.of(),
                "joinedAt", java.time.Instant.now().toString(),
                "bio", admin ? "Conta administrativa do Tabula." : "Novo membro do Tabula."
        );
    }

    private static String sha256(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String renderSuccessPage(String frontendUrl) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>E-mail Verificado - Tabula</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;800&display=swap" rel="stylesheet">
                    <style>
                        :root {
                            --color-bg: #0b0f19;
                            --color-card: rgba(22, 28, 45, 0.6);
                            --color-primary: #6366f1;
                            --color-primary-hover: #4f46e5;
                            --color-success: #10b981;
                            --color-text: #f3f4f6;
                            --color-text-muted: #9ca3af;
                        }
                        body {
                            margin: 0;
                            padding: 0;
                            background-color: var(--color-bg);
                            color: var(--color-text);
                            font-family: 'Outfit', sans-serif;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            min-height: 100vh;
                            background-image: 
                                radial-gradient(at 0% 0%, rgba(99, 102, 241, 0.15) 0px, transparent 50%),
                                radial-gradient(at 100% 100%, rgba(16, 185, 129, 0.1) 0px, transparent 50%);
                        }
                        .container {
                            width: 100%;
                            max-width: 480px;
                            padding: 20px;
                        }
                        .card {
                            background: var(--color-card);
                            backdrop-filter: blur(12px);
                            -webkit-backdrop-filter: blur(12px);
                            border: 1px solid rgba(255, 255, 255, 0.08);
                            border-radius: 24px;
                            padding: 40px 32px;
                            text-align: center;
                            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
                            animation: fadeIn 0.8s ease-out;
                        }
                        @keyframes fadeIn {
                            from { opacity: 0; transform: translateY(20px); }
                            to { opacity: 1; transform: translateY(0); }
                        }
                        .icon-wrapper {
                            width: 80px;
                            height: 80px;
                            background: rgba(16, 185, 129, 0.1);
                            border-radius: 50%;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            margin: 0 auto 24px;
                            box-shadow: 0 0 20px rgba(16, 185, 129, 0.2);
                            animation: pulse 2s infinite;
                        }
                        @keyframes pulse {
                            0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4); }
                            70% { box-shadow: 0 0 0 15px rgba(16, 185, 129, 0); }
                            100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
                        }
                        .icon {
                            font-size: 40px;
                            color: var(--color-success);
                        }
                        h1 {
                            font-size: 28px;
                            font-weight: 800;
                            margin: 0 0 12px;
                            background: linear-gradient(135deg, #fff 0%, var(--color-text-muted) 100%);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                        }
                        p {
                            font-size: 16px;
                            line-height: 1.6;
                            color: var(--color-text-muted);
                            margin: 0 0 32px;
                        }
                        .btn {
                            display: inline-block;
                            background: var(--color-primary);
                            color: #ffffff;
                            font-weight: 600;
                            font-size: 16px;
                            padding: 14px 32px;
                            text-decoration: none;
                            border-radius: 12px;
                            box-shadow: 0 8px 16px rgba(99, 102, 241, 0.25);
                            transition: all 0.3s ease;
                        }
                        .btn:hover {
                            background: var(--color-primary-hover);
                            transform: translateY(-2px);
                            box-shadow: 0 12px 20px rgba(99, 102, 241, 0.35);
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="card">
                            <div class="icon-wrapper">
                                <span class="icon">✓</span>
                            </div>
                            <h1>E-mail Verificado!</h1>
                            <p>Sua conta foi ativada com sucesso. Agora você pode entrar na plataforma e aproveitar tudo o que o Tabula oferece.</p>
                            <a href="%s/#/login" class="btn">Ir para o Login</a>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(frontendUrl);
    }

    private static String renderErrorPage(String message, String frontendUrl) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Falha na Verificação - Tabula</title>
                    <link rel="preconnect" href="https://fonts.googleapis.com">
                    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;800&display=swap" rel="stylesheet">
                    <style>
                        :root {
                            --color-bg: #0b0f19;
                            --color-card: rgba(22, 28, 45, 0.6);
                            --color-primary: #6366f1;
                            --color-primary-hover: #4f46e5;
                            --color-error: #ef4444;
                            --color-text: #f3f4f6;
                            --color-text-muted: #9ca3af;
                        }
                        body {
                            margin: 0;
                            padding: 0;
                            background-color: var(--color-bg);
                            color: var(--color-text);
                            font-family: 'Outfit', sans-serif;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            min-height: 100vh;
                            background-image: 
                                radial-gradient(at 0% 0%, rgba(239, 68, 68, 0.1) 0px, transparent 50%),
                                radial-gradient(at 100% 100%, rgba(99, 102, 241, 0.1) 0px, transparent 50%);
                        }
                        .container {
                            width: 100%;
                            max-width: 480px;
                            padding: 20px;
                        }
                        .card {
                            background: var(--color-card);
                            backdrop-filter: blur(12px);
                            -webkit-backdrop-filter: blur(12px);
                            border: 1px solid rgba(255, 255, 255, 0.08);
                            border-radius: 24px;
                            padding: 40px 32px;
                            text-align: center;
                            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
                            animation: fadeIn 0.8s ease-out;
                        }
                        @keyframes fadeIn {
                            from { opacity: 0; transform: translateY(20px); }
                            to { opacity: 1; transform: translateY(0); }
                        }
                        .icon-wrapper {
                            width: 80px;
                            height: 80px;
                            background: rgba(239, 68, 68, 0.1);
                            border-radius: 50%;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            margin: 0 auto 24px;
                            box-shadow: 0 0 20px rgba(239, 68, 68, 0.2);
                            animation: pulse 2s infinite;
                        }
                        @keyframes pulse {
                            0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4); }
                            70% { box-shadow: 0 0 0 15px rgba(239, 68, 68, 0); }
                            100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
                        }
                        .icon {
                            font-size: 40px;
                            color: var(--color-error);
                            font-weight: bold;
                        }
                        h1 {
                            font-size: 28px;
                            font-weight: 800;
                            margin: 0 0 12px;
                            background: linear-gradient(135deg, #fff 0%, var(--color-text-muted) 100%);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                        }
                        p {
                            font-size: 16px;
                            line-height: 1.6;
                            color: var(--color-text-muted);
                            margin: 0 0 32px;
                        }
                        .btn {
                            display: inline-block;
                            background: var(--color-primary);
                            color: #ffffff;
                            font-weight: 600;
                            font-size: 16px;
                            padding: 14px 32px;
                            text-decoration: none;
                            border-radius: 12px;
                            box-shadow: 0 8px 16px rgba(99, 102, 241, 0.25);
                            transition: all 0.3s ease;
                        }
                        .btn:hover {
                            background: var(--color-primary-hover);
                            transform: translateY(-2px);
                            box-shadow: 0 12px 20px rgba(99, 102, 241, 0.35);
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="card">
                            <div class="icon-wrapper">
                                <span class="icon">✗</span>
                            </div>
                            <h1>Link Inválido ou Expirado</h1>
                            <p>%s</p>
                            <a href="%s/#/login" class="btn">Voltar para o Tabula</a>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(message, frontendUrl);
    }

    private static String buildBackendUrl(io.javalin.http.Context ctx) {
        String backendUrl = System.getenv("BACKEND_URL");
        if (backendUrl != null && !backendUrl.isBlank()) {
            return backendUrl.trim().replaceAll("/+$", "");
        }
        
        String proto = ctx.header("X-Forwarded-Proto");
        if (proto == null || proto.isBlank()) {
            proto = ctx.scheme();
        }
        String host = ctx.header("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = ctx.host();
        }
        return proto + "://" + host;
    }
}
