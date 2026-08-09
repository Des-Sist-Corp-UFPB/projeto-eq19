package br.com.tabula.service;

import br.com.tabula.ai.AiChatClient;
import br.com.tabula.ai.AiChatResult;
import br.com.tabula.ai.AiProviderException;
import br.com.tabula.ai.AiUsage;
import br.com.tabula.dto.AiEventDraftResponse;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class AiEventDraftServiceTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZONE);

    @Test void validatesPromptBeforeCallingProvider() throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        AiEventDraftService service = service(client, validJson());
        for (String prompt : new String[]{null, "", "   ", "abcd", "x".repeat(1001)}) {
            assertThrows(AiDraftValidationException.class, () -> service.generate(prompt));
        }
        verifyNoInteractions(client);
    }

    @Test void returnsValidatedDraftAndUsesOnlyCompactCatalog() throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), eq("Mesa sábado"))).thenReturn(validJson());
        AiEventDraftResponse draft = service(client, validJson()).generate("  Mesa sábado  ").draft();
        assertEquals("g2", draft.gameId());
        assertEquals(4, draft.maxParticipants());
        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(systemPrompt.capture(), eq("Mesa sábado"));
        assertAll(
                () -> assertTrue(systemPrompt.getValue().contains("Não exija verbos")),
                () -> assertTrue(systemPrompt.getValue().contains("use exatamente maxPlayers")),
                () -> assertTrue(systemPrompt.getValue().contains("Hoje=2026-07-30")),
                () -> assertTrue(systemPrompt.getValue().contains("fuso=America/Sao_Paulo")),
                () -> assertTrue(systemPrompt.getValue().contains("ocorrência futura")),
                () -> assertTrue(systemPrompt.getValue().contains("não pode aparecer em missingFields")),
                () -> assertTrue(systemPrompt.getValue().contains("somente status e draft")),
                () -> assertTrue(systemPrompt.getValue().contains("sem nulls extras")));
    }

    @Test void acceptsOneJsonObjectInsideMarkdownOrSurroundingText() throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString())).thenReturn("```json\n" + validJson() + "\n```");
        assertEquals("g2", service(client, validJson()).generate("Mesa sábado").draft().gameId());
    }

    @Test void rejectsAmbiguousOrMalformedJson() throws Exception {
        for (String response : new String[]{"não é json", "{} {}", "{\"gameId\":"}) {
            assertInvalid(response);
        }
    }

    @Test void exposesSafeSpecificReasonsForInvalidModelResponses() throws Exception {
        assertReason("", "empty_model_response", "response_parse");
        assertReason("not json", "malformed_json", "response_parse");
        assertReason("{\"status\":\"other\"}", "unknown_response_type", "response_contract");
        assertReason("{\"status\":\"draft\"}", "missing_draft", "response_contract");
        assertReason(validJson().replace("\"description\":\"Mesa de Magic.\",", ""),
                "missing_required_field", "draft_validation");
        assertReason(validJson().replace("\"g2\"", "\"inventado\""),
                "game_not_in_catalog", "draft_validation");
        assertReason(validJson().replace("\"18:00\"", "\"25:99\""),
                "invalid_time", "draft_validation");
    }

    @Test void rejectsUnknownGameAndMismatchedName() throws Exception {
        assertInvalid(validJson().replace("\"g2\"", "\"inventado\""));
        assertInvalid(validJson().replace("Magic: The Gathering", "Xadrez"));
    }

    @Test void classifiesSupportedIncompleteAndOutOfScopeRequestsInOneProviderCall() throws Exception {
        assertStatus("Xadrez amanhã 19h biblioteca", validXadrezJson(), "draft");
        assertStatus("Magic sábado às 18h no bloco C", validJson(), "draft");
        assertStatus("Pokémon sexta às 15h na biblioteca", validPokemonJson(), "draft");
        String missingMany = """
                {"status":"needs_clarification","reasonCode":"missing_required_information",
                "missingFields":["date","time","location"],"message":"Informe data, horário e local.",
                "partialDraft":{"gameId":"g2","gameName":"Magic: The Gathering","maxParticipants":6,
                "description":"Evento de Magic: The Gathering.","warnings":[]}}
                """;
        String missingLocation = """
                {"status":"needs_clarification","reasonCode":"missing_required_information",
                "missingFields":["location"],"message":"Onde será o evento?",
                "partialDraft":{"gameId":"g3","gameName":"Pokémon","date":"2026-08-01","time":"15:00",
                "maxParticipants":4,"description":"Evento de Pokémon.","warnings":[]}}
                """;
        var pokemon = serviceResponse("Pokémon sexta às 3 da tarde", missingLocation);
        assertEquals("needs_clarification", pokemon.status());
        assertEquals(List.of("location"), pokemon.missingFields());
        assertEquals("g3", pokemon.partialDraft().gameId());
        assertEquals("2026-08-01", pokemon.partialDraft().date());
        assertEquals("15:00", pokemon.partialDraft().time());
        assertNull(pokemon.partialDraft().location());
        assertStatus("Quero criar um evento de Magic", missingMany, "needs_clarification");
        String unsupported = """
                {"status":"unsupported","reasonCode":"not_event_creation_request"}
                """;
        assertStatus("Tem evento de Pokémon sexta?", unsupported, "unsupported");
        assertStatus("Como jogar Xadrez?", unsupported, "unsupported");
        assertStatus("Qual jogo você recomenda?", unsupported, "unsupported");
    }

    @Test void weekdayClarificationUsesOnlyBackendTodayAndDoesNotMarkDateAsMissing() throws Exception {
        Clock sunday = Clock.fixed(Instant.parse("2026-08-09T12:00:00Z"), ZONE);
        String fridayWithoutLocation = """
                {"status":"needs_clarification","reasonCode":"missing_required_information",
                "missingFields":["location"],"message":"Informe o local.",
                "partialDraft":{"gameId":"g3","gameName":"Pokémon","date":"2026-08-14","time":"15:00",
                "maxParticipants":4,"description":"Evento de Pokémon.","warnings":[]}}
                """;
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString())).thenReturn(fridayWithoutLocation);

        var response = service(client, fridayWithoutLocation, sunday)
                .generate("Pokémon sexta às 3 da tarde");

        assertEquals(List.of("location"), response.missingFields());
        assertEquals("2026-08-14", response.partialDraft().date());
        assertEquals("15:00", response.partialDraft().time());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(client).chat(prompt.capture(), anyString());
        assertTrue(prompt.getValue().contains("Hoje=2026-08-09"));

        assertStatus("Xadrez segunda 19h biblioteca",
                validXadrezJson().replace("2026-08-01", "2026-08-10"), "draft");
        assertStatus("Magic sábado 18h bloco C",
                validJson().replace("2026-08-01", "2026-08-15"), "draft");
    }

    @Test void rejectsUnknownFieldsInDraftAndInvalidClassificationShape() throws Exception {
        assertInvalid(validJson().replace("\"warnings\":[]", "\"warnings\":[],\"extra\":true"));
        assertInvalid("{\"status\":\"unsupported\",\"reasonCode\":\"not_event_creation_request\",\"draft\":null}");
    }

    @Test void rejectsUnknownOrContradictoryPartialDraftFields() throws Exception {
        String valid = """
                {"status":"needs_clarification","reasonCode":"missing_required_information",
                "missingFields":["location"],"message":"Informe o local.",
                "partialDraft":{"gameId":"g3","gameName":"Pokémon","date":"2026-08-01","time":"15:00"}}
                """;
        assertReason(valid.replace("\"time\":\"15:00\"", "\"time\":\"15:00\",\"extra\":true"),
                "invalid_response_schema", "partial_draft_validation");
        assertReason(valid.replace("\"time\":\"15:00\"", "\"time\":\"15:00\",\"location\":\"Biblioteca\""),
                "inconsistent_partial_draft", "partial_draft_validation");
        assertReason(valid.replace("\"g3\"", "\"inventado\""),
                "game_not_in_catalog", "partial_draft_validation");
        assertReason(valid.replace("\"message\":\"Informe o local.\"",
                        "\"message\":\"Informe o local.\",\"extra\":true"),
                "invalid_response_schema", "response_contract");
    }

    @Test void outOfScopeRefinementReturnsNoDraftAndDoesNotMutateCurrentDraft() throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        String unsupported = "{\"status\":\"unsupported\",\"reasonCode\":\"not_event_creation_request\"}";
        when(client.chatWithUsage(anyString(), anyString()))
                .thenReturn(new AiChatResult(unsupported, AiUsage.empty(), 1));
        AiEventDraftResponse current = new AiEventDraftResponse("g2", "Magic: The Gathering",
                "2026-08-01", "18:00", "Biblioteca", 4, "Mesa de Magic.", List.of());

        AiEventDraftService.GenerationResult result = service(client, unsupported)
                .refineWithUsage("Qual o horário do SBT hoje?", current);

        assertEquals("unsupported", result.response().status());
        assertNull(result.response().draft());
        assertEquals("Biblioteca", current.location());
        verify(client, times(1)).chatWithUsage(anyString(), anyString());
    }

    @Test void rejectsPastDateAndInvalidTime() throws Exception {
        assertInvalid(validJson().replace("2026-08-01", "2026-07-29"));
        assertInvalid(validJson().replace("18:00", "25:99"));
    }

    @Test void rejectsParticipantCountsOutsideGlobalAndGameLimits() throws Exception {
        assertInvalid(validJson().replace("\"maxParticipants\":4", "\"maxParticipants\":1"));
        assertInvalid(validJson().replace("\"maxParticipants\":4", "\"maxParticipants\":7"));
    }

    @Test void rejectsExcessiveDescriptionAndWarnings() throws Exception {
        assertInvalid(validJson().replace("Mesa de Magic.", "x".repeat(501)));
        String warnings = java.util.stream.IntStream.range(0, 11).mapToObj(i -> "\"w" + i + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        assertInvalid(validJson().replace("\"warnings\":[]", "\"warnings\":[" + warnings + "]"));
    }

    @Test void propagatesSafeProviderFailure() throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString()))
                .thenThrow(new AiProviderException(AiProviderException.Category.TIMEOUT));
        AiProviderException error = assertThrows(AiProviderException.class,
                () -> service(client, validJson()).generate("Mesa sábado"));
        assertEquals(AiProviderException.Category.TIMEOUT, error.category());
    }

    @Test void refinementCapturesUsageAndProviderCallCount() throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chatWithUsage(anyString(), anyString())).thenReturn(
                new AiChatResult(validJson(), new AiUsage(90, 25, 115), 2));
        AiEventDraftResponse current = new AiEventDraftResponse("g2", "Magic: The Gathering",
                "2026-08-01", "18:00", "Biblioteca", 4, "Mesa de Magic.", List.of());

        AiEventDraftService.GenerationResult result =
                service(client, validJson()).refineWithUsage("Troque o horário", current);

        assertEquals(90, result.usage().promptTokens());
        assertEquals(25, result.usage().completionTokens());
        assertEquals(115, result.usage().totalTokens());
        assertEquals(2, result.providerCalls());
    }

    @Test void limitsCandidatesAndFiltersByName() {
        List<AiEventDraftService.Game> catalog = java.util.stream.IntStream.range(0, 30)
                .mapToObj(index -> new AiEventDraftService.Game("g" + index,
                        index == 22 ? "Magic Arena" : "Jogo " + index,
                        index % 2 == 0 ? "Cartas" : "Estratégia", 2, 6, 30 + index, 2.0))
                .toList();
        List<AiEventDraftService.Game> candidates =
                AiEventDraftService.selectCandidates("Quero jogar Magic", catalog, 15);
        assertEquals(15, candidates.size());
        assertEquals("g22", candidates.get(0).id());
    }

    @Test void filtersByPlayerCountAndUsesDeterministicFallback() {
        List<AiEventDraftService.Game> catalog = List.of(
                new AiEventDraftService.Game("g3", "Zulu", "Estratégia", 2, 4, 60, 3),
                new AiEventDraftService.Game("g1", "Alpha", "Cartas", 5, 8, 30, 2),
                new AiEventDraftService.Game("g2", "Beta", "Cartas", 2, 3, 45, 2));
        assertEquals("g1", AiEventDraftService.selectCandidates("mesa para 6 pessoas", catalog, 2).get(0).id());
        List<String> first = AiEventDraftService.selectCandidates("vamos jogar", catalog, 3)
                .stream().map(AiEventDraftService.Game::id).toList();
        List<String> second = AiEventDraftService.selectCandidates("vamos jogar", catalog, 3)
                .stream().map(AiEventDraftService.Game::id).toList();
        assertEquals(List.of("g1", "g2", "g3"), first);
        assertEquals(first, second);
    }

    private static void assertInvalid(String response) throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString())).thenReturn(response);
        assertThrows(AiDraftValidationException.class, () -> service(client, response).generate("Mesa sábado"));
    }

    private static void assertReason(String response, String reasonCode, String validationStage) throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString())).thenReturn(response);
        AiDraftValidationException exception = assertThrows(AiDraftValidationException.class,
                () -> service(client, response).generate("Mesa sábado"));
        assertEquals(reasonCode, exception.reasonCode());
        assertEquals(validationStage, exception.validationStage());
    }

    private static void assertStatus(String prompt, String modelResponse, String expectedStatus) throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString())).thenReturn(modelResponse);
        assertEquals(expectedStatus, service(client, modelResponse).generate(prompt).status());
        verify(client, times(1)).chat(anyString(), anyString());
    }

    private static br.com.tabula.dto.AiEventAssistantResponse serviceResponse(
            String prompt, String modelResponse) throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString())).thenReturn(modelResponse);
        var response = service(client, modelResponse).generate(prompt);
        verify(client, times(1)).chat(anyString(), anyString());
        return response;
    }

    private static AiEventDraftService service(AiChatClient client, String ignored) throws Exception {
        return service(client, ignored, CLOCK);
    }

    private static AiEventDraftService service(AiChatClient client, String ignored, Clock clock) throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("FROM jogos"))).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, true, true, false);
        when(result.getString("external_id")).thenReturn("g2", "g1", "g3");
        when(result.getString("nome")).thenReturn("Magic: The Gathering", "Xadrez", "Pokémon");
        when(result.getString("categoria")).thenReturn("Cartas", "Estratégia", "Cartas");
        when(result.getInt("min_players")).thenReturn(2, 2, 2);
        when(result.getInt("max_players")).thenReturn(6, 2, 4);
        when(result.getInt("avg_play_time")).thenReturn(60, 45, 40);
        when(result.getDouble("complexity")).thenReturn(3.2, 2.5, 2.0);
        return new AiEventDraftService(client, dataSource, ZONE, clock);
    }

    private static String validJson() {
        return """
                {"status":"draft","draft":{"gameId":"g2","gameName":"Magic: The Gathering","date":"2026-08-01",
                "time":"18:00","location":"Biblioteca","maxParticipants":4,
                "description":"Mesa de Magic.","warnings":[]}}
                """;
    }

    private static String validXadrezJson() {
        return """
                {"status":"draft","draft":{"gameId":"g1","gameName":"Xadrez","date":"2026-08-01",
                "time":"19:00","location":"Biblioteca","maxParticipants":2,
                "description":"Partida de Xadrez.","warnings":[]}}
                """;
    }

    private static String validPokemonJson() {
        return """
                {"status":"draft","draft":{"gameId":"g3","gameName":"Pokémon","date":"2026-08-01",
                "time":"15:00","location":"Biblioteca","maxParticipants":4,
                "description":"Evento de Pokémon.","warnings":[]}}
                """;
    }
}
