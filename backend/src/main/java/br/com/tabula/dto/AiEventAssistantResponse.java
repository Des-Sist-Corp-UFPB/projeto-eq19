package br.com.tabula.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiEventAssistantResponse(
        String status,
        AiEventDraftResponse draft,
        String reasonCode,
        List<String> missingFields,
        String message) {

    public static AiEventAssistantResponse draft(AiEventDraftResponse draft) {
        return new AiEventAssistantResponse("draft", draft, null, null, null);
    }

    public static AiEventAssistantResponse needsClarification(
            String reasonCode, List<String> missingFields, String message) {
        return new AiEventAssistantResponse(
                "needs_clarification", null, reasonCode, List.copyOf(missingFields), message);
    }

    public static AiEventAssistantResponse unsupported(String reasonCode) {
        return new AiEventAssistantResponse("unsupported", null, reasonCode, null, null);
    }
}
