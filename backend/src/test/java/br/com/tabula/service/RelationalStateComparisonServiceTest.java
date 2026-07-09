package br.com.tabula.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationalStateComparisonServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldReturnOkTrueForIdenticalEmptyStates() throws Exception {
        String emptyJson = """
                {
                  "users": [],
                  "boardGames": [],
                  "sessions": [],
                  "events": [],
                  "logs": []
                }
                """;

        String report = RelationalStateComparisonService.compareStateJson(emptyJson, emptyJson);
        assertNotNull(report);
        JsonNode root = MAPPER.readTree(report);
        assertTrue(root.get("ok").asBoolean());
        assertTrue(root.get("errors").isEmpty());
        assertTrue(root.get("warnings").isEmpty());
    }

    @Test
    void shouldReturnOkFalseForInvalidJson() throws Exception {
        String validJson = "{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";
        String invalidJson = "{invalid";

        String report1 = RelationalStateComparisonService.compareStateJson(invalidJson, validJson);
        JsonNode root1 = MAPPER.readTree(report1);
        assertFalse(root1.get("ok").asBoolean());
        assertFalse(root1.get("errors").isEmpty());

        String report2 = RelationalStateComparisonService.compareStateJson(validJson, invalidJson);
        JsonNode root2 = MAPPER.readTree(report2);
        assertFalse(root2.get("ok").asBoolean());
        assertFalse(root2.get("errors").isEmpty());
    }

    @Test
    void shouldReturnOkFalseForMissingTopLevelArray() throws Exception {
        String missingArrayJson = "{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[]}"; // no logs
        String validJson = "{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";

        String report = RelationalStateComparisonService.compareStateJson(validJson, missingArrayJson);
        JsonNode root = MAPPER.readTree(report);
        assertFalse(root.get("ok").asBoolean());
        assertFalse(root.get("errors").isEmpty());

        String report2 = RelationalStateComparisonService.compareStateJson(missingArrayJson, validJson);
        JsonNode root2 = MAPPER.readTree(report2);
        assertFalse(root2.get("ok").asBoolean());
        assertFalse(root2.get("errors").isEmpty());
    }

    @Test
    void shouldDetectCountMismatchAndDuplicateIds() throws Exception {
        String legacyJson = """
                {
                  "users": [{"id":"u1","name":"A","email":"a@b.com","role":"student"},{"id":"u1","name":"A","email":"a@b.com","role":"student"}],
                  "boardGames": [],
                  "sessions": [],
                  "events": [],
                  "logs": []
                }
                """;
        String relationalJson = "{\"users\":[{\"id\":\"u1\",\"name\":\"A\",\"email\":\"a@b.com\",\"role\":\"student\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";

        String report = RelationalStateComparisonService.compareStateJson(legacyJson, relationalJson);
        JsonNode root = MAPPER.readTree(report);
        assertFalse(root.get("ok").asBoolean()); // because of duplicate ID u1
        assertTrue(root.get("errors").toString().contains("Duplicate ID detected"));
    }

    @Test
    void shouldDetectMissingIdAsError() throws Exception {
        String legacyJson = "{\"users\":[{\"id\":\"u1\",\"name\":\"A\",\"email\":\"a@b.com\",\"role\":\"student\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";
        String relationalJson = "{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";

        String report = RelationalStateComparisonService.compareStateJson(legacyJson, relationalJson);
        JsonNode root = MAPPER.readTree(report);
        assertFalse(root.get("ok").asBoolean());
        assertTrue(root.get("errors").toString().contains("present in legacy but missing in relational"));
    }

    @Test
    void shouldDetectExtraIdAsWarningButOkTrue() throws Exception {
        String legacyJson = "{\"users\":[],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";
        String relationalJson = "{\"users\":[{\"id\":\"u1\",\"name\":\"A\",\"email\":\"a@b.com\",\"role\":\"student\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";

        String report = RelationalStateComparisonService.compareStateJson(legacyJson, relationalJson);
        JsonNode root = MAPPER.readTree(report);
        assertTrue(root.get("ok").asBoolean());
        assertTrue(root.get("warnings").toString().contains("present in relational but extra"));
    }

    @Test
    void shouldDetectFieldMismatchAsWarningButOkTrue() throws Exception {
        String legacyJson = "{\"users\":[{\"id\":\"u1\",\"name\":\"A\",\"email\":\"a@b.com\",\"role\":\"student\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";
        String relationalJson = "{\"users\":[{\"id\":\"u1\",\"name\":\"Different Name\",\"email\":\"a@b.com\",\"role\":\"student\"}],\"boardGames\":[],\"sessions\":[],\"events\":[],\"logs\":[]}";

        String report = RelationalStateComparisonService.compareStateJson(legacyJson, relationalJson);
        JsonNode root = MAPPER.readTree(report);
        assertTrue(root.get("ok").asBoolean());
        assertTrue(root.get("warnings").toString().contains("Field mismatch"));
    }
}
