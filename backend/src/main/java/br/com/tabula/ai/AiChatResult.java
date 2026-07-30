package br.com.tabula.ai;

public record AiChatResult(String content, AiUsage usage, int providerCalls) {
    public AiChatResult(String content, AiUsage usage) {
        this(content, usage, 1);
    }
}
