package br.com.tabula.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiEventPartialDraftResponse(
        String gameId,
        String gameName,
        String date,
        String time,
        String location,
        Integer maxParticipants,
        String description,
        List<String> warnings) {}
