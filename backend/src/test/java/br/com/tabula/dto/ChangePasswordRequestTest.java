package br.com.tabula.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangePasswordRequestTest {

    @Test
    void shouldSetAndGetAllFields() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setEmail("bob@example.com");
        request.setCurrentPassword("oldPass");
        request.setNewPassword("newPass");

        assertEquals("bob@example.com", request.getEmail());
        assertEquals("oldPass", request.getCurrentPassword());
        assertEquals("newPass", request.getNewPassword());
    }
}
