package br.com.tabula.ai;

public interface AiChatClient {
    String chat(String systemPrompt, String userPrompt) throws AiProviderException;
    default AiChatResult chatWithUsage(String systemPrompt, String userPrompt) throws AiProviderException {
        return new AiChatResult(chat(systemPrompt, userPrompt), AiUsage.empty());
    }
    String model();
}
