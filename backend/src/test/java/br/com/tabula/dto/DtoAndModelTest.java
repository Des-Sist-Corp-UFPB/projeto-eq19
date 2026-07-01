package br.com.tabula.dto;

import br.com.tabula.model.UserAccount;
import br.com.tabula.repository.VerificationTokenRepository.TokenInfo;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DtoAndModelTest {

    @Test
    void testLoginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("pass123");

        assertEquals("user@example.com", request.getEmail());
        assertEquals("pass123", request.getPassword());
    }

    @Test
    void testRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("pass123");

        assertEquals("Alice", request.getName());
        assertEquals("alice@example.com", request.getEmail());
        assertEquals("pass123", request.getPassword());
    }

    @Test
    void testResetPasswordRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("reset@example.com");
        request.setNewPassword("newpass123");

        assertEquals("reset@example.com", request.getEmail());
        assertEquals("newpass123", request.getNewPassword());
    }

    @Test
    void testVerifyEmailCodeRequest() {
        VerifyEmailCodeRequest request = new VerifyEmailCodeRequest();
        request.setEmail("verify@example.com");
        request.setCode("123456");

        assertEquals("verify@example.com", request.getEmail());
        assertEquals("123456", request.getCode());
    }

    @Test
    void testResendVerificationRequest() {
        ResendVerificationRequest request = new ResendVerificationRequest();
        request.setEmail("resend@example.com");

        assertEquals("resend@example.com", request.getEmail());
    }

    @Test
    void testUserAccountModel() {
        UserAccount account = new UserAccount(1L, "u_1", "Bob", "bob@example.com", "hash", "USER", true);

        assertEquals(1L, account.getId());
        assertEquals("u_1", account.getExternalId());
        assertEquals("Bob", account.getName());
        assertEquals("bob@example.com", account.getEmail());
        assertEquals("hash", account.getPasswordHash());
        assertEquals("USER", account.getRole());
        assertTrue(account.isEmailVerificado());
    }

    @Test
    void testTokenInfoModel() {
        Instant now = Instant.now();
        TokenInfo tokenInfo = new TokenInfo(42L, now);

        assertEquals(42L, tokenInfo.getUserId());
        assertEquals(now, tokenInfo.getExpiresAt());
    }
}
