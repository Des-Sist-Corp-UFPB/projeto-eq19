package br.com.tabula.dto;

public class VerifyEmailCodeRequest {
    private String email;
    private String code;

    public VerifyEmailCodeRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}