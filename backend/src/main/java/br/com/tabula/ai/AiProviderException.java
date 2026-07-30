package br.com.tabula.ai;

public class AiProviderException extends Exception {
    public enum Category {
        NOT_CONFIGURED, TIMEOUT, CONNECTION, UNAUTHORIZED, RATE_LIMITED,
        SERVER_ERROR, INVALID_RESPONSE, CATALOG_UNAVAILABLE, INTERRUPTED, INTERNAL
    }

    private final Category category;
    private final int providerCalls;

    public AiProviderException(Category category) {
        this(category, null, category == Category.NOT_CONFIGURED || category == Category.CATALOG_UNAVAILABLE ? 0 : 1);
    }

    public AiProviderException(Category category, Throwable cause) {
        this(category, cause, category == Category.NOT_CONFIGURED || category == Category.CATALOG_UNAVAILABLE ? 0 : 1);
    }

    public AiProviderException(Category category, Throwable cause, int providerCalls) {
        super(category.name(), cause);
        this.category = category;
        this.providerCalls = providerCalls;
    }

    public Category category() { return category; }
    public int providerCalls() { return providerCalls; }
    public AiProviderException withProviderCalls(int calls) {
        return new AiProviderException(category, getCause(), calls);
    }
    public boolean transientFailure() {
        return category == Category.TIMEOUT || category == Category.CONNECTION
                || category == Category.RATE_LIMITED || category == Category.SERVER_ERROR;
    }
}
