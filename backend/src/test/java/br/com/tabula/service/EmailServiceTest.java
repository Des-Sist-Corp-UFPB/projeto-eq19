package br.com.tabula.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailServiceTest {

    @Test
    void shouldRequireSmtpCredentialsBeforeSending() {
        EmailService service = new EmailService("smtp.example.com", 587, "", "", "sender@example.com", "Tabula");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sendVerificationCode("recipient@example.com", "Recipient", "123456", "u_test")
        );

        assertTrue(exception.getMessage().contains("SMTP_USER"));
    }

    @Test
    void shouldRequireFromAddressBeforeSending() {
        EmailService service = new EmailService("smtp.example.com", 587, "user", "pass", "", "Tabula");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sendVerificationCode("recipient@example.com", "Recipient", "123456", "u_test")
        );

        assertTrue(exception.getMessage().contains("SMTP_FROM"));
    }

    @Test
    void shouldEscapeHtmlValuesInVerificationMessage() throws Exception {
        Method method = EmailService.class.getDeclaredMethod("buildVerificationHtml", String.class, String.class);
        method.setAccessible(true);

        String html = (String) method.invoke(null, "<script>alert(1)</script>", "123456");

        assertTrue(html.contains("Tabula"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertTrue(html.contains("123456"));
    }

    @Test
    void shouldParseDefaultPortWhenValueIsInvalid() throws Exception {
        Method method = EmailService.class.getDeclaredMethod("parsePort", String.class);
        method.setAccessible(true);

        Object port = method.invoke(null, "invalid-port");

        assertTrue(port instanceof Integer);
        assertTrue((Integer) port == 587);
    }

    @Test
    void shouldUseDefaultNameWhenRecipientNameIsBlank() throws Exception {
        Method method = EmailService.class.getDeclaredMethod("buildVerificationHtml", String.class, String.class);
        method.setAccessible(true);

        String html = (String) method.invoke(null, "   ", "123456");

        assertTrue(html.contains("jogador"));
    }

    @Test
    void shouldConstructWithDefaultConstructor() {
        EmailService service = new EmailService();
        assertTrue(service != null);
    }

    @Test
    void shouldTestGetEnvOrDefault() throws Exception {
        Method method = EmailService.class.getDeclaredMethod("getEnvOrDefault", String.class, String.class);
        method.setAccessible(true);
        String val = (String) method.invoke(null, "NON_EXISTENT_ENV_VAR_XYZ", "default_val");
        assertEquals("default_val", val);
    }

    @Test
    void shouldTestAuthenticatorAndPasswordAuthentication() throws Exception {
        EmailService service = new EmailService("smtp.example.com", 587, "  smtp_user  ", "  smtp_pass  ", "sender@example.com", "Tabula");
        
        Class<?> authenticatorClass = Class.forName("br.com.tabula.service.EmailService$1");
        java.lang.reflect.Constructor<?> constructor = authenticatorClass.getDeclaredConstructor(EmailService.class);
        constructor.setAccessible(true);
        
        Object authenticatorInstance = constructor.newInstance(service);
        
        Method method = authenticatorClass.getDeclaredMethod("getPasswordAuthentication");
        method.setAccessible(true);
        
        jakarta.mail.PasswordAuthentication passwordAuth = (jakarta.mail.PasswordAuthentication) method.invoke(authenticatorInstance);
        
        assertTrue(passwordAuth != null);
        assertEquals("smtp_user", passwordAuth.getUserName());
        assertEquals("smtp_pass", passwordAuth.getPassword());
    }
}
