package br.com.tabula.controller;

import br.com.tabula.model.UserAccount;
import br.com.tabula.dto.RegisterRequest;
import br.com.tabula.dto.ResendVerificationRequest;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void shouldNormalizeEmailToLowercaseAndTrim() throws Exception {
        Method method = AuthController.class.getDeclaredMethod("normalizeEmail", String.class);
        method.setAccessible(true);

        String normalized = (String) method.invoke(null, "  Alice@Example.COM  ");

        assertEquals("alice@example.com", normalized);
    }

    @Test
    void shouldBuildFrontendUserWithInitialsFromName() throws Exception {
        Method method = AuthController.class.getDeclaredMethod("toFrontendUser", UserAccount.class);
        method.setAccessible(true);

        UserAccount account = new UserAccount(1L, "u_1", "Alice Silva", "alice@example.com", "hash", "USER", true);
        Map<String, Object> frontendUser = (Map<String, Object>) method.invoke(null, account);

        assertNotNull(frontendUser);
        assertEquals("AS", frontendUser.get("avatar"));
        assertEquals("student", frontendUser.get("role"));
    }

    @Test
    void shouldHashPasswordToHexString() throws Exception {
        Method method = AuthController.class.getDeclaredMethod("sha256", String.class);
        method.setAccessible(true);

        String hash = (String) method.invoke(null, "secret123");

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void shouldRenderVerificationPagesAndBuildBackendUrl() throws Exception {
        Method successMethod = AuthController.class.getDeclaredMethod("renderSuccessPage", String.class);
        successMethod.setAccessible(true);
        String successPage = (String) successMethod.invoke(null, "https://tabula.example");
        assertTrue(successPage.contains("E-mail Verificado!"));
        assertTrue(successPage.contains("https://tabula.example/#/login"));

        Method errorMethod = AuthController.class.getDeclaredMethod("renderErrorPage", String.class, String.class);
        errorMethod.setAccessible(true);
        String errorPage = (String) errorMethod.invoke(null, "Link inválido", "https://tabula.example");
        assertTrue(errorPage.contains("Link Inválido ou Expirado"));
        assertTrue(errorPage.contains("Link inválido"));
        assertTrue(errorPage.contains("https://tabula.example/#/login"));

        Method buildUrlMethod = AuthController.class.getDeclaredMethod("buildBackendUrl", Context.class);
        buildUrlMethod.setAccessible(true);

        Context context = mock(Context.class);
        when(context.header("X-Forwarded-Proto")).thenReturn("https");
        when(context.header("X-Forwarded-Host")).thenReturn("example.com");
        String builtUrl = (String) buildUrlMethod.invoke(null, context);
        assertEquals("https://example.com", builtUrl);

        when(context.header("X-Forwarded-Proto")).thenReturn(null);
        when(context.header("X-Forwarded-Host")).thenReturn(null);
        when(context.scheme()).thenReturn("http");
        when(context.host()).thenReturn("localhost:8080");
        String fallbackUrl = (String) buildUrlMethod.invoke(null, context);
        assertEquals("http://localhost:8080", fallbackUrl);
    }

    @Test
    void shouldUseAdminAndSingleNameFallbackInFrontendUser() throws Exception {
        Method method = AuthController.class.getDeclaredMethod("toFrontendUser", UserAccount.class);
        method.setAccessible(true);

        UserAccount admin = new UserAccount(2L, "u_2", "Admin", "admin@example.com", "hash", "ADMIN", true);
        Map<String, Object> frontendUser = (Map<String, Object>) method.invoke(null, admin);

        assertEquals("AD", frontendUser.get("avatar"));
        assertEquals("admin", frontendUser.get("role"));
        assertEquals("Administração do clube", frontendUser.get("course"));
    }

    @Test
    void shouldReturnErrorPageForLegacyVerifyEmailRoute() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/auth/verify-email");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Links de verificação"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldHandleInvalidLoginPayload() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendRaw(app, "/auth/login", "{invalid-json");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Dados inválidos para login"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldHandleInvalidRegistrationPayload() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendRaw(app, "/auth/register", "{invalid-json");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Dados inválidos para cadastro"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectResendVerificationWhenAccountIsAlreadyVerified() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(16L);
        when(resultSet.getString("external_id")).thenReturn("u_16");
        when(resultSet.getString("nome")).thenReturn("Grace");
        when(resultSet.getString("email")).thenReturn("grace@example.com");
        when(resultSet.getString("senha_hash")).thenReturn("hash");
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(true);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/resend-verification", "{\"email\":\"grace@example.com\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("já está verificado"), response.body());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectResetPasswordWhenFieldsAreMissing() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/reset-password", "{\"email\":\"\",\"newPassword\":\"\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("obrigatórios"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldLoginExistingVerifiedUser() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement createTokenStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement, createTokenStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(7L);
        when(resultSet.getString("external_id")).thenReturn("u_7");
        when(resultSet.getString("nome")).thenReturn("Alice");
        when(resultSet.getString("email")).thenReturn("alice@example.com");
        when(resultSet.getString("senha_hash")).thenReturn(sha256("secret123"));
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(true);
        when(createTokenStatement.executeUpdate()).thenReturn(1);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/login", "{\"email\":\"alice@example.com\",\"password\":\"secret123\"}");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"token\""));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectLoginForUnverifiedUser() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(7L);
        when(resultSet.getString("external_id")).thenReturn("u_7");
        when(resultSet.getString("nome")).thenReturn("Alice");
        when(resultSet.getString("email")).thenReturn("alice@example.com");
        when(resultSet.getString("senha_hash")).thenReturn(sha256("secret123"));
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(false);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/login", "{\"email\":\"alice@example.com\",\"password\":\"secret123\"}");
            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("verify your email"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectLoginWhenFieldsAreMissing() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);

        try {
            HttpResponse<String> response = sendJson(app, "/auth/login", "{\"email\":\"\",\"password\":\"\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("obrigatórios"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRegisterNewUserAndReturnCreatedStatus() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement emailCheckStatement = mock(PreparedStatement.class);
        PreparedStatement createUserStatement = mock(PreparedStatement.class);
        PreparedStatement deleteTokensStatement = mock(PreparedStatement.class);
        PreparedStatement createTokenStatement = mock(PreparedStatement.class);
        ResultSet emailCheckResultSet = mock(ResultSet.class);
        ResultSet createUserResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(emailCheckStatement, createUserStatement, deleteTokensStatement, createTokenStatement);
        when(emailCheckStatement.executeQuery()).thenReturn(emailCheckResultSet);
        when(emailCheckResultSet.next()).thenReturn(false);
        when(createUserStatement.executeQuery()).thenReturn(createUserResultSet);
        when(createUserResultSet.next()).thenReturn(true, false);
        when(createUserResultSet.getLong("id")).thenReturn(10L);
        when(createUserResultSet.getString("external_id")).thenReturn("u_10");
        when(createUserResultSet.getString("nome")).thenReturn("Bob");
        when(createUserResultSet.getString("email")).thenReturn("bob@example.com");
        when(createUserResultSet.getString("senha_hash")).thenReturn(sha256("password"));
        when(createUserResultSet.getString("role")).thenReturn("USER");
        when(createUserResultSet.getBoolean("email_verificado")).thenReturn(false);
        when(deleteTokensStatement.executeUpdate()).thenReturn(1);
        when(createTokenStatement.executeUpdate()).thenReturn(1);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/register", "{\"name\":\"Bob\",\"email\":\"bob@example.com\",\"password\":\"password\"}");
            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("Conta criada com sucesso"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnConflictWhenRegisteringExistingEmail() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement emailCheckStatement = mock(PreparedStatement.class);
        ResultSet emailCheckResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(emailCheckStatement);
        when(emailCheckStatement.executeQuery()).thenReturn(emailCheckResultSet);
        when(emailCheckResultSet.next()).thenReturn(true);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/register", "{\"name\":\"Bob\",\"email\":\"bob@example.com\",\"password\":\"password\"}");
            assertEquals(409, response.statusCode());
            assertTrue(response.body().contains("já está cadastrado"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldVerifyEmailWithValidCode() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement findCodeStatement = mock(PreparedStatement.class);
        PreparedStatement verifyEmailStatement = mock(PreparedStatement.class);
        PreparedStatement deleteTokensStatement = mock(PreparedStatement.class);
        ResultSet findUserResultSet = mock(ResultSet.class);
        ResultSet findCodeResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement, findCodeStatement, verifyEmailStatement, deleteTokensStatement);
        when(findUserStatement.executeQuery()).thenReturn(findUserResultSet);
        when(findUserResultSet.next()).thenReturn(true, false);
        when(findUserResultSet.getLong("id")).thenReturn(12L);
        when(findUserResultSet.getString("external_id")).thenReturn("u_12");
        when(findUserResultSet.getString("nome")).thenReturn("Cara");
        when(findUserResultSet.getString("email")).thenReturn("cara@example.com");
        when(findUserResultSet.getString("senha_hash")).thenReturn("hash");
        when(findUserResultSet.getString("role")).thenReturn("USER");
        when(findUserResultSet.getBoolean("email_verificado")).thenReturn(false);
        when(findCodeStatement.executeQuery()).thenReturn(findCodeResultSet);
        when(findCodeResultSet.next()).thenReturn(true, false);
        when(findCodeResultSet.getLong("usuario_id")).thenReturn(12L);
        when(findCodeResultSet.getTimestamp("expiracao")).thenReturn(Timestamp.from(Instant.now().plusSeconds(600)));
        when(verifyEmailStatement.executeUpdate()).thenReturn(1);
        when(deleteTokensStatement.executeUpdate()).thenReturn(1);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/verify-email", "{\"email\":\"cara@example.com\",\"code\":\"123456\"}");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("E-mail verificado"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectVerifyEmailWhenCodeFormatIsInvalid() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);

        try {
            HttpResponse<String> response = sendJson(app, "/auth/verify-email", "{\"email\":\"cara@example.com\",\"code\":\"12345\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("6 dígitos"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnNotFoundWhenVerifyEmailAccountMissing() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/verify-email", "{\"email\":\"missing@example.com\",\"code\":\"123456\"}");
            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("Nenhuma conta encontrada"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnBadRequestWhenVerifyEmailAlreadyVerified() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(12L);
        when(resultSet.getString("external_id")).thenReturn("u_12");
        when(resultSet.getString("nome")).thenReturn("Cara");
        when(resultSet.getString("email")).thenReturn("cara@example.com");
        when(resultSet.getString("senha_hash")).thenReturn("hash");
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(true);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/verify-email", "{\"email\":\"cara@example.com\",\"code\":\"123456\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("e-mail") && response.body().contains("verificado"), response.body());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldResendVerificationCode() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement deleteTokensStatement = mock(PreparedStatement.class);
        PreparedStatement createTokenStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement, deleteTokensStatement, createTokenStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(13L);
        when(resultSet.getString("external_id")).thenReturn("u_13");
        when(resultSet.getString("nome")).thenReturn("Dana");
        when(resultSet.getString("email")).thenReturn("dana@example.com");
        when(resultSet.getString("senha_hash")).thenReturn("hash");
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(false);
        when(deleteTokensStatement.executeUpdate()).thenReturn(1);
        when(createTokenStatement.executeUpdate()).thenReturn(1);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/resend-verification", "{\"email\":\"dana@example.com\"}");
            assertEquals(500, response.statusCode());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnNotFoundWhenResettingPasswordForMissingAccount() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/reset-password", "{\"email\":\"eve@example.com\",\"newPassword\":\"freshPass\"}");
            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("Nenhuma conta encontrada"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldResetPasswordForExistingAccount() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement updatePasswordStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement, updatePasswordStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(14L);
        when(resultSet.getString("external_id")).thenReturn("u_14");
        when(resultSet.getString("nome")).thenReturn("Eve");
        when(resultSet.getString("email")).thenReturn("eve@example.com");
        when(resultSet.getString("senha_hash")).thenReturn("oldhash");
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(true);
        when(updatePasswordStatement.executeUpdate()).thenReturn(1);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/reset-password", "{\"email\":\"eve@example.com\",\"newPassword\":\"freshPass\"}");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Senha redefinida"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldReturnUnauthorizedWhenCurrentPasswordIsWrong() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(15L);
        when(resultSet.getString("external_id")).thenReturn("u_15");
        when(resultSet.getString("nome")).thenReturn("Finn");
        when(resultSet.getString("email")).thenReturn("finn@example.com");
        when(resultSet.getString("senha_hash")).thenReturn(sha256("differentpass"));
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(true);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/change-password", "{\"email\":\"finn@example.com\",\"currentPassword\":\"oldpass\",\"newPassword\":\"newpass\"}");
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("Senha atual incorreta"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldChangePasswordForValidCurrentPassword() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement updatePasswordStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement, updatePasswordStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(15L);
        when(resultSet.getString("external_id")).thenReturn("u_15");
        when(resultSet.getString("nome")).thenReturn("Finn");
        when(resultSet.getString("email")).thenReturn("finn@example.com");
        when(resultSet.getString("senha_hash")).thenReturn(sha256("oldpass"));
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(true);
        when(updatePasswordStatement.executeUpdate()).thenReturn(1);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/change-password", "{\"email\":\"finn@example.com\",\"currentPassword\":\"oldpass\",\"newPassword\":\"newpass\"}");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("Senha alterada"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldCoverPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<AuthController> constructor = AuthController.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AuthController instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void shouldHandleSqlExceptionDuringLogin() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Database down"));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/login", "{\"email\":\"alice@example.com\",\"password\":\"secret\"}");
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Não foi possível fazer login"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectLoginWithNullPassword() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/login", "{\"email\":\"alice@example.com\",\"password\":null}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("E-mail e senha são obrigatórios"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectRegisterWithNullFields() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/register", "{\"name\":null,\"email\":\"a@b.com\",\"password\":null}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Nome, e-mail e senha são obrigatórios"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldHandleSqlExceptionDuringRegister() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Database down"));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/register", "{\"name\":\"Bob\",\"email\":\"bob@example.com\",\"password\":\"password\"}");
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Não foi possível criar a conta"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRegisterNewUserAndSendEmailSuccessfully() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement emailCheckStatement = mock(PreparedStatement.class);
        PreparedStatement createUserStatement = mock(PreparedStatement.class);
        PreparedStatement deleteTokensStatement = mock(PreparedStatement.class);
        PreparedStatement createTokenStatement = mock(PreparedStatement.class);
        ResultSet emailCheckResultSet = mock(ResultSet.class);
        ResultSet createUserResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(emailCheckStatement, createUserStatement, deleteTokensStatement, createTokenStatement);
        when(emailCheckStatement.executeQuery()).thenReturn(emailCheckResultSet);
        when(emailCheckResultSet.next()).thenReturn(false);
        when(createUserStatement.executeQuery()).thenReturn(createUserResultSet);
        when(createUserResultSet.next()).thenReturn(true, false);
        when(createUserResultSet.getLong("id")).thenReturn(10L);
        when(createUserResultSet.getString("external_id")).thenReturn("u_10");
        when(createUserResultSet.getString("nome")).thenReturn("Bob");
        when(createUserResultSet.getString("email")).thenReturn("bob@example.com");
        when(createUserResultSet.getString("senha_hash")).thenReturn(sha256("password"));
        when(createUserResultSet.getString("role")).thenReturn("USER");
        when(createUserResultSet.getBoolean("email_verificado")).thenReturn(false);
        when(deleteTokensStatement.executeUpdate()).thenReturn(1);
        when(createTokenStatement.executeUpdate()).thenReturn(1);

        Javalin app = mock(Javalin.class);
        org.mockito.ArgumentCaptor<io.javalin.http.Handler> handlerCaptor = org.mockito.ArgumentCaptor.forClass(io.javalin.http.Handler.class);
        AuthController.register(app, dataSource);
        org.mockito.Mockito.verify(app).post(org.mockito.Mockito.eq("/auth/register"), handlerCaptor.capture());
        io.javalin.http.Handler registerHandler = handlerCaptor.getValue();

        Context ctx = mock(Context.class);
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setName("Bob");
        registerRequest.setEmail("bob@example.com");
        registerRequest.setPassword("password");
        when(ctx.bodyAsClass(RegisterRequest.class)).thenReturn(registerRequest);
        when(ctx.status(org.mockito.Mockito.anyInt())).thenReturn(ctx);

        try (var mockedEmailService = org.mockito.Mockito.mockConstruction(br.com.tabula.service.EmailService.class)) {
            registerHandler.handle(ctx);

            org.mockito.Mockito.verify(ctx).status(201);
            assertEquals(1, mockedEmailService.constructed().size());
            br.com.tabula.service.EmailService emailService = mockedEmailService.constructed().get(0);
            org.mockito.InOrder order = org.mockito.Mockito.inOrder(connection, emailService);
            order.verify(connection).commit();
            order.verify(emailService).sendVerificationCode(
                    org.mockito.Mockito.eq("bob@example.com"),
                    org.mockito.Mockito.eq("Bob"),
                    org.mockito.Mockito.anyString(),
                    org.mockito.Mockito.eq("u_10")
            );
        }
    }

    @Test
    void shouldReturnErrorPageForLegacyApiVerifyEmailRoute() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendGet(app, "/api/auth/verify-email");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Links de verificação"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectVerifyEmailWithMissingFields() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response1 = sendJson(app, "/auth/verify-email", "{\"email\":\"\",\"code\":\"123456\"}");
            assertEquals(400, response1.statusCode());
            assertTrue(response1.body().contains("e-mail é obrigatório"));

            HttpResponse<String> response2 = sendJson(app, "/auth/verify-email", "{\"email\":\"user@example.com\",\"code\":\"\"}");
            assertEquals(400, response2.statusCode());
            assertTrue(response2.body().contains("código de verificação é obrigatório"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectVerifyEmailWithExpiredCode() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement findCodeStatement = mock(PreparedStatement.class);
        ResultSet findUserResultSet = mock(ResultSet.class);
        ResultSet findCodeResultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement, findCodeStatement);
        when(findUserStatement.executeQuery()).thenReturn(findUserResultSet);
        when(findUserResultSet.next()).thenReturn(true, false);
        when(findUserResultSet.getLong("id")).thenReturn(12L);
        when(findUserResultSet.getString("external_id")).thenReturn("u_12");
        when(findUserResultSet.getString("nome")).thenReturn("Cara");
        when(findUserResultSet.getString("email")).thenReturn("cara@example.com");
        when(findUserResultSet.getString("senha_hash")).thenReturn("hash");
        when(findUserResultSet.getString("role")).thenReturn("USER");
        when(findUserResultSet.getBoolean("email_verificado")).thenReturn(false);
        when(findCodeStatement.executeQuery()).thenReturn(findCodeResultSet);
        when(findCodeResultSet.next()).thenReturn(true, false);
        when(findCodeResultSet.getLong("usuario_id")).thenReturn(12L);
        when(findCodeResultSet.getTimestamp("expiracao")).thenReturn(Timestamp.from(Instant.now().minusSeconds(3600)));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/verify-email", "{\"email\":\"cara@example.com\",\"code\":\"123456\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("expirou"), response.body());
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldHandleSqlExceptionDuringVerifyEmail() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Database down"));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/verify-email", "{\"email\":\"cara@example.com\",\"code\":\"123456\"}");
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Erro interno no servidor ao verificar"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectResendVerificationWithMissingEmail() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/resend-verification", "{\"email\":\"\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("e-mail é obrigatório"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectResendVerificationWhenUserNotFound() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/resend-verification", "{\"email\":\"missing@example.com\"}");
            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("Nenhuma conta encontrada"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldResendVerificationAndSendEmailSuccessfully() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement deleteTokensStatement = mock(PreparedStatement.class);
        PreparedStatement createTokenStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement, deleteTokensStatement, createTokenStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(13L);
        when(resultSet.getString("external_id")).thenReturn("u_13");
        when(resultSet.getString("nome")).thenReturn("Dana");
        when(resultSet.getString("email")).thenReturn("dana@example.com");
        when(resultSet.getString("senha_hash")).thenReturn("hash");
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(false);
        when(deleteTokensStatement.executeUpdate()).thenReturn(1);
        when(createTokenStatement.executeUpdate()).thenReturn(1);

        Javalin app = mock(Javalin.class);
        org.mockito.ArgumentCaptor<io.javalin.http.Handler> handlerCaptor = org.mockito.ArgumentCaptor.forClass(io.javalin.http.Handler.class);
        AuthController.register(app, dataSource);
        org.mockito.Mockito.verify(app).post(org.mockito.Mockito.eq("/auth/resend-verification"), handlerCaptor.capture());
        io.javalin.http.Handler resendHandler = handlerCaptor.getValue();

        Context ctx = mock(Context.class);
        ResendVerificationRequest resendRequest = new ResendVerificationRequest();
        resendRequest.setEmail("dana@example.com");
        when(ctx.bodyAsClass(ResendVerificationRequest.class)).thenReturn(resendRequest);
        when(ctx.status(org.mockito.Mockito.anyInt())).thenReturn(ctx);

        try (var mockedEmailService = org.mockito.Mockito.mockConstruction(br.com.tabula.service.EmailService.class)) {
            resendHandler.handle(ctx);

            assertEquals(1, mockedEmailService.constructed().size());
            br.com.tabula.service.EmailService emailService = mockedEmailService.constructed().get(0);
            org.mockito.InOrder order = org.mockito.Mockito.inOrder(connection, emailService);
            order.verify(connection).commit();
            order.verify(emailService).sendVerificationCode(
                    org.mockito.Mockito.eq("dana@example.com"),
                    org.mockito.Mockito.eq("Dana"),
                    org.mockito.Mockito.anyString(),
                    org.mockito.Mockito.eq("u_13")
            );
        }
    }

    @Test
    void shouldHandleSqlExceptionDuringResendVerification() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Database down"));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/resend-verification", "{\"email\":\"dana@example.com\"}");
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Não foi possível reenviar"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldHandleSqlExceptionDuringResetPassword() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Database down"));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/reset-password", "{\"email\":\"eve@example.com\",\"newPassword\":\"freshPass\"}");
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Não foi possível redefinir"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectChangePasswordWithMissingFields() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/change-password", "{\"email\":\"\",\"currentPassword\":\"\",\"newPassword\":\"\"}");
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("obrigatórias"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldRejectChangePasswordWhenUserNotFound() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(findUserStatement);
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/change-password", "{\"email\":\"finn@example.com\",\"currentPassword\":\"oldpass\",\"newPassword\":\"newpass\"}");
            assertEquals(401, response.statusCode());
            assertTrue(response.body().contains("Senha atual incorreta"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldHandleSqlExceptionDuringChangePassword() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Database down"));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(app, "/auth/change-password", "{\"email\":\"finn@example.com\",\"currentPassword\":\"oldpass\",\"newPassword\":\"newpass\"}");
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("Não foi possível alterar a senha"));
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldHandleToFrontendUserInitialsEdgeCases() throws Exception {
        Method method = AuthController.class.getDeclaredMethod("toFrontendUser", UserAccount.class);
        method.setAccessible(true);

        UserAccount spaceName = new UserAccount(1L, "u_1", "   ", "email@example.com", "hash", "USER", true);
        Map<String, Object> user1 = (Map<String, Object>) method.invoke(null, spaceName);
        assertEquals("U", user1.get("avatar"));

        UserAccount singleCharName = new UserAccount(1L, "u_1", "  X  ", "email@example.com", "hash", "USER", true);
        Map<String, Object> user2 = (Map<String, Object>) method.invoke(null, singleCharName);
        assertEquals("X", user2.get("avatar"));
    }

    @Test
    void shouldRollbackLoginWhenTransactionalAuditInsertFails() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement createTokenStatement = mock(PreparedStatement.class);
        PreparedStatement auditStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM usuarios")) return findUserStatement;
            if (sql.contains("INSERT INTO auth_tokens")) return createTokenStatement;
            if (sql.contains("INSERT INTO audit_logs")) return auditStatement;
            throw new AssertionError("Unexpected SQL: " + sql);
        });
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("id")).thenReturn(7L);
        when(resultSet.getString("external_id")).thenReturn("u_7");
        when(resultSet.getString("nome")).thenReturn("Alice");
        when(resultSet.getString("email")).thenReturn("alice@example.com");
        when(resultSet.getString("senha_hash")).thenReturn(sha256("secret123"));
        when(resultSet.getString("role")).thenReturn("USER");
        when(resultSet.getBoolean("email_verificado")).thenReturn(true);
        when(createTokenStatement.executeUpdate()).thenReturn(1);
        when(auditStatement.executeUpdate()).thenThrow(new SQLException("audit insert failed"));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(
                    app, "/auth/login",
                    "{\"email\":\"alice@example.com\",\"password\":\"secret123\"}"
            );

            assertEquals(500, response.statusCode(), response.body());
            verify(connection).rollback();
            verify(connection, never()).commit();
        } finally {
            app.stop();
        }
    }

    @Test
    void shouldKeepUnauthorizedResponseWhenRejectedLoginAuditFailsBestEffort() throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement auditStatement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM usuarios")) return findUserStatement;
            if (sql.contains("INSERT INTO audit_logs")) return auditStatement;
            throw new AssertionError("Unexpected SQL: " + sql);
        });
        when(findUserStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        when(auditStatement.executeUpdate()).thenThrow(new SQLException("audit unavailable"));

        Javalin app = startAuthApp(dataSource);
        try {
            HttpResponse<String> response = sendJson(
                    app, "/auth/login",
                    "{\"email\":\"missing@example.com\",\"password\":\"secret123\"}"
            );

            assertEquals(401, response.statusCode(), response.body());
            assertTrue(response.body().contains("Credenciais"), response.body());
        } finally {
            app.stop();
        }
    }

    private static Javalin startAuthApp(HikariDataSource dataSource) {
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false);
        AuthController.register(app, dataSource);
        app.start(0);
        return app;
    }

    private static HttpResponse<String> sendGet(Javalin app, String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> sendJson(Javalin app, String path, String body) throws Exception {
        return sendRaw(app, path, body);
    }

    private static HttpResponse<String> sendRaw(Javalin app, String path, String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String sha256(String value) throws Exception {
        Method method = AuthController.class.getDeclaredMethod("sha256", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }
}
