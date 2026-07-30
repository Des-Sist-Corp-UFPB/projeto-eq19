package br.com.tabula.service;

import br.com.tabula.ai.AiChatClient;
import br.com.tabula.ai.AiProviderException;
import br.com.tabula.dto.AiEventDraftResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiEventDraftService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AiChatClient client;
    private final HikariDataSource dataSource;
    private final ZoneId timeZone;
    private final Clock clock;

    public record Game(String id, String name, String category, int minPlayers, int maxPlayers,
                       int avgPlayTime, double complexity) {}

    public AiEventDraftService(AiChatClient client, HikariDataSource dataSource, ZoneId timeZone) {
        this(client, dataSource, timeZone, Clock.system(timeZone));
    }

    AiEventDraftService(AiChatClient client, HikariDataSource dataSource, ZoneId timeZone, Clock clock) {
        this.client = client; this.dataSource = dataSource; this.timeZone = timeZone; this.clock = clock;
    }

    public AiEventDraftResponse generate(String prompt) throws AiProviderException, AiDraftValidationException {
        String cleanPrompt = validatePrompt(prompt);
        List<Game> games = loadGames();
        String raw = client.chat(systemPrompt(games), cleanPrompt);
        return validateResponse(raw, games);
    }

    public static String validatePrompt(String prompt) throws AiDraftValidationException {
        if (prompt == null) throw new AiDraftValidationException("prompt é obrigatório.");
        String clean = prompt.trim();
        if (clean.length() < 5) throw new AiDraftValidationException("prompt deve ter ao menos 5 caracteres.");
        if (clean.length() > 1000) throw new AiDraftValidationException("prompt deve ter no máximo 1000 caracteres.");
        return clean;
    }

    private List<Game> loadGames() throws AiProviderException {
        String sql = "SELECT external_id, nome, categoria, min_players, max_players, avg_play_time, complexity FROM jogos WHERE external_id IS NOT NULL ORDER BY nome";
        List<Game> games = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) games.add(new Game(rs.getString("external_id"), rs.getString("nome"),
                    rs.getString("categoria"), rs.getInt("min_players"), rs.getInt("max_players"),
                    rs.getInt("avg_play_time"), rs.getDouble("complexity")));
            return games;
        } catch (Exception ex) {
            throw new AiProviderException("GAME_CATALOG_UNAVAILABLE", ex);
        }
    }

    private String systemPrompt(List<Game> games) throws AiProviderException {
        try {
            return """
                    Você cria apenas rascunhos de encontros do Tabula. Hoje é %s no fuso %s.
                    Interprete datas relativas usando a próxima ocorrência (por exemplo, "sábado").
                    Responda SOMENTE um objeto JSON, sem markdown ou bloco de código, com:
                    gameId, gameName, date (YYYY-MM-DD), time (HH:mm), location, maxParticipants,
                    description curta em português brasileiro e warnings (array).
                    Nunca invente gameId. Escolha somente um jogo do catálogo abaixo. Sinalize ambiguidades em warnings.
                    Catálogo de jogos (únicos campos enviados ao modelo):
                    %s
                    """.formatted(LocalDate.now(clock), timeZone, MAPPER.writeValueAsString(games));
        } catch (Exception ex) { throw new AiProviderException("PROMPT_BUILD_FAILURE", ex); }
    }

    private AiEventDraftResponse validateResponse(String raw, List<Game> games) throws AiDraftValidationException {
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (node == null || !node.isObject()) throw invalid("JSON inválido.");
            String gameId = requiredText(node, "gameId", 80);
            Map<String, Game> byId = new LinkedHashMap<>();
            games.forEach(game -> byId.put(game.id(), game));
            Game game = byId.get(gameId);
            if (game == null) throw invalid("Jogo fora do catálogo.");
            String gameName = requiredText(node, "gameName", 200);
            if (!game.name().equals(gameName)) throw invalid("Nome do jogo não corresponde ao catálogo.");
            LocalDate date;
            try {
                String dateText = requiredText(node, "date", 10);
                if (!dateText.matches("\\d{4}-\\d{2}-\\d{2}")) throw invalid("Data inválida.");
                date = LocalDate.parse(dateText);
            } catch (DateTimeParseException ex) { throw invalid("Data inválida."); }
            if (date.isBefore(LocalDate.now(clock))) throw invalid("Data no passado.");
            String time = requiredText(node, "time", 5);
            if (!time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) throw invalid("Horário inválido.");
            String location = requiredText(node, "location", 200);
            int max = node.path("maxParticipants").asInt(-1);
            if (max < 2 || max > 100) throw invalid("Quantidade de participantes inválida.");
            String description = requiredText(node, "description", 500);
            JsonNode warningsNode = node.get("warnings");
            if (warningsNode == null || !warningsNode.isArray() || warningsNode.size() > 10)
                throw invalid("Warnings inválidos.");
            List<String> warnings = new ArrayList<>();
            for (JsonNode warning : warningsNode) {
                if (!warning.isTextual() || warning.asText().isBlank() || warning.asText().length() > 200)
                    throw invalid("Warning inválido.");
                warnings.add(warning.asText().trim());
            }
            return new AiEventDraftResponse(gameId, gameName, date.toString(), time, location, max, description, warnings);
        } catch (AiDraftValidationException ex) { throw ex; }
        catch (Exception ex) { throw new AiDraftValidationException("Resposta da IA não pôde ser validada.", ex); }
    }

    private static String requiredText(JsonNode node, String field, int max) throws AiDraftValidationException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().trim().isEmpty() || value.asText().trim().length() > max)
            throw invalid("Campo " + field + " inválido.");
        return value.asText().trim();
    }
    private static AiDraftValidationException invalid(String message) { return new AiDraftValidationException(message); }
}
