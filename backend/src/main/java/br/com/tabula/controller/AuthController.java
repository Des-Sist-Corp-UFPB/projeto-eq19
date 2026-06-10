package br.com.tabula.controller;

import br.com.tabula.dto.RegisterRequest;
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

public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private AuthController() {
    }

    public static void register(Javalin app, HikariDataSource dataSource) {
        UserRepository userRepository = new UserRepository(dataSource);

        app.post("/auth/register", ctx -> {
            try {
                RegisterRequest request = ctx.bodyAsClass(RegisterRequest.class);
                String name = request.getName() == null ? "" : request.getName().trim();
                String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
                String password = request.getPassword() == null ? "" : request.getPassword();

                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    ctx.status(400).json(Map.of("error", "Nome, e-mail e senha são obrigatórios."));
                    return;
                }

                if (userRepository.emailExists(email)) {
                    ctx.status(409).json(Map.of("error", "Este e-mail já está cadastrado."));
                    return;
                }

                String passwordHash = sha256(password);
                long userId = userRepository.createUser(name, email, passwordHash);

                LOGGER.info("Registered user {} ({}) with id {}", name, email, userId);
                ctx.status(201).json(Map.of(
                        "ok", true,
                        "message", "Conta criada com sucesso.",
                        "userId", userId,
                        "email", email,
                        "name", name
                ));
            } catch (SQLException ex) {
                LOGGER.error("Failed to register user", ex);
                ctx.status(500).json(Map.of("error", "Não foi possível criar a conta no momento."));
            } catch (Exception ex) {
                LOGGER.error("Invalid registration payload", ex);
                ctx.status(400).json(Map.of("error", "Dados inválidos para cadastro."));
            }
        });
    }

    private static String sha256(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
