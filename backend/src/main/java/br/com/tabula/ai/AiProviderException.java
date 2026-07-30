package br.com.tabula.ai;

public class AiProviderException extends Exception {
    public enum Category {
        NOT_CONFIGURED, TIMEOUT, CONNECTION, UNAUTHORIZED, RATE_LIMITED,
        SERVER_ERROR, INVALID_RESPONSE, CATALOG_UNAVAILABLE, INTERRUPTED, INTERNAL
    }

    private final Category category;

    public AiProviderException(Category category) {
        super(category.name());
        this.category = category;
    }

    public AiProviderException(Category category, Throwable cause) {
        super(category.name(), cause);
        this.category = category;
    }

    public Category category() { return category; }
    public boolean transientFailure() {
        return category == Category.TIMEOUT || category == Category.CONNECTION
                || category == Category.RATE_LIMITED || category == Category.SERVER_ERROR;
    }
}
