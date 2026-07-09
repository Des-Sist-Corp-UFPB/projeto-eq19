package br.com.tabula.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RelationalStateComparisonService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RelationalStateComparisonService() {
    }

    public static String compareStateJson(String legacyJson, String relationalJson) {
        ObjectNode report = MAPPER.createObjectNode();
        ObjectNode summary = report.putObject("summary");
        ArrayNode warnings = report.putArray("warnings");
        ArrayNode errors = report.putArray("errors");
        report.put("ok", true);

        JsonNode legacyRoot;
        try {
            legacyRoot = MAPPER.readTree(legacyJson);
        } catch (Exception e) {
            report.put("ok", false);
            errors.add("Invalid legacy JSON: " + e.getMessage());
            return report.toPrettyString();
        }

        JsonNode relationalRoot;
        try {
            relationalRoot = MAPPER.readTree(relationalJson);
        } catch (Exception e) {
            report.put("ok", false);
            errors.add("Invalid relational JSON: " + e.getMessage());
            return report.toPrettyString();
        }

        String[] sections = {"users", "boardGames", "sessions", "events", "logs"};

        for (String section : sections) {
            ObjectNode secSummary = summary.putObject(section);
            secSummary.put("legacyCount", 0);
            secSummary.put("relationalCount", 0);
            ArrayNode missingInRel = secSummary.putArray("missingInRelational");
            ArrayNode extraInRel = secSummary.putArray("extraInRelational");

            if (!legacyRoot.has(section)) {
                report.put("ok", false);
                errors.add("Missing top-level field in legacy JSON: " + section);
                continue;
            }
            JsonNode legacyArr = legacyRoot.get(section);
            if (!legacyArr.isArray()) {
                report.put("ok", false);
                errors.add("Field in legacy JSON is not an array: " + section);
                continue;
            }

            if (!relationalRoot.has(section)) {
                report.put("ok", false);
                errors.add("Missing top-level field in relational JSON: " + section);
                continue;
            }
            JsonNode relationalArr = relationalRoot.get(section);
            if (!relationalArr.isArray()) {
                report.put("ok", false);
                errors.add("Field in relational JSON is not an array: " + section);
                continue;
            }

            int legacyCount = legacyArr.size();
            int relationalCount = relationalArr.size();

            secSummary.put("legacyCount", legacyCount);
            secSummary.put("relationalCount", relationalCount);

            Map<String, JsonNode> legacyById = new HashMap<>();
            Set<String> legacyDupIds = new HashSet<>();
            for (JsonNode item : legacyArr) {
                if (item.has("id")) {
                    String id = item.get("id").asText();
                    if (legacyById.containsKey(id)) {
                        legacyDupIds.add(id);
                    } else {
                        legacyById.put(id, item);
                    }
                }
            }

            for (String dupId : legacyDupIds) {
                report.put("ok", false);
                errors.add("Duplicate ID detected in legacy " + section + ": " + dupId);
            }

            Map<String, JsonNode> relationalById = new HashMap<>();
            Set<String> relationalDupIds = new HashSet<>();
            for (JsonNode item : relationalArr) {
                if (item.has("id")) {
                    String id = item.get("id").asText();
                    if (relationalById.containsKey(id)) {
                        relationalDupIds.add(id);
                    } else {
                        relationalById.put(id, item);
                    }
                }
            }

            for (String dupId : relationalDupIds) {
                report.put("ok", false);
                errors.add("Duplicate ID detected in relational " + section + ": " + dupId);
            }

            for (String id : legacyById.keySet()) {
                if (!relationalById.containsKey(id)) {
                    missingInRel.add(id);
                    report.put("ok", false);
                    errors.add("ID " + id + " in section " + section + " present in legacy but missing in relational.");
                }
            }

            for (String id : relationalById.keySet()) {
                if (!legacyById.containsKey(id)) {
                    extraInRel.add(id);
                    warnings.add("ID " + id + " in section " + section + " present in relational but extra (missing in legacy).");
                }
            }

            for (Map.Entry<String, JsonNode> entry : legacyById.entrySet()) {
                String id = entry.getKey();
                JsonNode legItem = entry.getValue();
                JsonNode relItem = relationalById.get(id);
                if (relItem == null) {
                    continue;
                }

                if ("users".equals(section)) {
                    compareFields(id, section, legItem, relItem, new String[]{"name", "email", "role"}, warnings);
                } else if ("boardGames".equals(section)) {
                    compareFields(id, section, legItem, relItem, new String[]{"name", "category"}, warnings);
                } else if ("sessions".equals(section)) {
                    compareFields(id, section, legItem, relItem, new String[]{"gameId", "organizerId", "winnerId"}, warnings);
                } else if ("events".equals(section)) {
                    compareFields(id, section, legItem, relItem, new String[]{"gameId", "organizerId", "status"}, warnings);
                } else if ("logs".equals(section)) {
                    compareFields(id, section, legItem, relItem, new String[]{"userId", "action"}, warnings);
                }
            }
        }

        return report.toPrettyString();
    }

    private static void compareFields(String id, String section, JsonNode legItem, JsonNode relItem, String[] fields, ArrayNode warnings) {
        for (String field : fields) {
            String legVal = getFieldAsString(legItem, field);
            String relVal = getFieldAsString(relItem, field);
            if (!legVal.equalsIgnoreCase(relVal)) {
                warnings.add("Field mismatch in " + section + " ID " + id + " field '" + field + "': legacy is '" + legVal + "', relational is '" + relVal + "'");
            }
        }
    }

    private static String getFieldAsString(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText().trim();
    }
}
