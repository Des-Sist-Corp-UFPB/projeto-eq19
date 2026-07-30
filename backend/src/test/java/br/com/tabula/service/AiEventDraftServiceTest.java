package br.com.tabula.service;

import br.com.tabula.ai.AiChatClient;
import br.com.tabula.ai.AiProviderException;
import br.com.tabula.dto.AiEventDraftResponse;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

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
        AiEventDraftResponse draft = service(client, validJson()).generate("  Mesa sábado  ");
        assertEquals("g2", draft.gameId());
        assertEquals(4, draft.maxParticipants());
        verify(client).chat(contains("Catálogo de jogos"), eq("Mesa sábado"));
    }

    @Test void acceptsOneJsonObjectInsideMarkdownOrSurroundingText() throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString())).thenReturn("```json\n" + validJson() + "\n```");
        assertEquals("g2", service(client, validJson()).generate("Mesa sábado").gameId());
    }

    @Test void rejectsAmbiguousOrMalformedJson() throws Exception {
        for (String response : new String[]{"não é json", "{} {}", "{\"gameId\":"}) {
            assertInvalid(response);
        }
    }

    @Test void rejectsUnknownGameAndMismatchedName() throws Exception {
        assertInvalid(validJson().replace("\"g2\"", "\"inventado\""));
        assertInvalid(validJson().replace("Magic: The Gathering", "Xadrez"));
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

    private static void assertInvalid(String response) throws Exception {
        AiChatClient client = mock(AiChatClient.class);
        when(client.chat(anyString(), anyString())).thenReturn(response);
        assertThrows(AiDraftValidationException.class, () -> service(client, response).generate("Mesa sábado"));
    }

    private static AiEventDraftService service(AiChatClient client, String ignored) throws Exception {
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("FROM jogos"))).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true, false);
        when(result.getString("external_id")).thenReturn("g2");
        when(result.getString("nome")).thenReturn("Magic: The Gathering");
        when(result.getString("categoria")).thenReturn("Cartas");
        when(result.getInt("min_players")).thenReturn(2);
        when(result.getInt("max_players")).thenReturn(6);
        when(result.getInt("avg_play_time")).thenReturn(60);
        when(result.getDouble("complexity")).thenReturn(3.2);
        return new AiEventDraftService(client, dataSource, ZONE, CLOCK);
    }

    private static String validJson() {
        return """
                {"gameId":"g2","gameName":"Magic: The Gathering","date":"2026-08-01",
                "time":"18:00","location":"Biblioteca","maxParticipants":4,
                "description":"Mesa de Magic.","warnings":[]}
                """;
    }
}
