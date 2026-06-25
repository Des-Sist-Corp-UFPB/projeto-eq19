package br.com.tabula.controller;

import br.com.tabula.dto.ChangePasswordRequest;
import br.com.tabula.dto.LoginRequest;
import br.com.tabula.dto.RegisterRequest;
import br.com.tabula.dto.ResetPasswordRequest;
import br.com.tabula.model.UserAccount;
import br.com.tabula.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
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
                        "USER"
                );

                LOGGER.info("Registered user {} ({}) with id {}", name, email, account.getId());
                String token = userRepository.createAuthToken(account.getId());
                ctx.status(201).json(Map.of(
                        "ok", true,
                        "message", "Conta criada com sucesso.",
                        "token", token,
                        "user", toFrontendUser(account)
                ));
            } catch (SQLException ex) {
                LOGGER.error("Failed to register user", ex);
                ctx.status(500).json(Map.of("error", "Não foi possível criar a conta no momento."));
            } catch (Exception ex) {
                LOGGER.error("Invalid registration payload", ex);
                ctx.status(400).json(Map.of("error", "Dados inválidos para cadastro."));
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
}
