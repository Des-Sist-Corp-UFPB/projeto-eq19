package br.com.tabula.ai;

public record AiUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    public static AiUsage empty() { return new AiUsage(null, null, null); }
}
