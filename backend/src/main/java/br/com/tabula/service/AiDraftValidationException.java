package br.com.tabula.service;

public class AiDraftValidationException extends Exception {
    public AiDraftValidationException(String message) { super(message); }
    public AiDraftValidationException(String message, Throwable cause) { super(message, cause); }
}
