package br.com.tabula.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegisterRequestTest {

    @Test
    void shouldSetAndGetAllFields() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("secret123");

        assertEquals("Alice", request.getName());
        assertEquals("alice@example.com", request.getEmail());
        assertEquals("secret123", request.getPassword());
    }
}
