package br.com.tabula.dto;

public record AiEventRefinementRequest(String instruction, AiEventDraftResponse currentDraft) {}
