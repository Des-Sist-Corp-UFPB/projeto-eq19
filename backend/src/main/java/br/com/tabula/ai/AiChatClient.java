package br.com.tabula.ai;

public interface AiChatClient {
    String chat(String systemPrompt, String userPrompt) throws AiProviderException;
    String model();
}
