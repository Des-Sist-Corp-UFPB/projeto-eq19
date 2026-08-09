package br.com.tabula.service;

public class AiDraftValidationException extends Exception {
    private final String reasonCode;
    private final String validationStage;

    public AiDraftValidationException(String message) {
        this(message, "validation_error", "input_validation", null);
    }

    public AiDraftValidationException(String message, Throwable cause) {
        this(message, "validation_error", "input_validation", cause);
    }

    public AiDraftValidationException(String message, String reasonCode, String validationStage) {
        this(message, reasonCode, validationStage, null);
    }

    public AiDraftValidationException(
            String message, String reasonCode, String validationStage, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
        this.validationStage = validationStage;
    }

    public String reasonCode() { return reasonCode; }
    public String validationStage() { return validationStage; }
}
