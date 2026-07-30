package br.com.tabula.dto;

import java.util.List;

public record AiEventDraftResponse(
        String gameId, String gameName, String date, String time, String location,
        int maxParticipants, String description, List<String> warnings) {}
