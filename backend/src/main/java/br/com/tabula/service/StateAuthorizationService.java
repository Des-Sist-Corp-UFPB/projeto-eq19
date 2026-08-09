package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StateAuthorizationService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> STATE_SECTIONS =
            Set.of("users", "boardGames", "sessions", "events", "logs");
    private static final Set<String> OWN_PROFILE_FIELDS =
            Set.of("name", "email", "course", "avatar", "avatarUrl", "bio", "favoriteGames");
    private static final Set<String> USER_FIELDS = Set.of(
            "id", "name", "email", "passwordHash", "role", "course", "avatar", "avatarUrl",
            "winCount", "favoriteGames", "joinedAt", "bio");
    private static final Set<String> GAME_FIELDS = Set.of(
            "id", "name", "description", "coverUrl", "category", "minPlayers", "maxPlayers",
            "avgPlayTime", "complexity");
    private static final Set<String> SESSION_FIELDS = Set.of(
            "id", "gameId", "date", "location", "organizerId", "participantIds", "winnerId",
            "duration", "notes", "photos", "comments");
    private static final Set<String> EVENT_FIELDS = Set.of(
            "id", "gameId", "date", "time", "location", "maxParticipants", "participantIds",
            "waitingListIds", "description", "organizerId", "status");
    private static final Set<String> COMMENT_FIELDS = Set.of(
            "id", "userId", "userName", "userAvatar", "content", "createdAt");

    public AuthorizationDecision authorize(
            String currentPayload,
            String requestedPayload,
            AuthenticatedPrincipal principal) {
        final JsonNode current;
        try {
            current = MAPPER.readTree(currentPayload);
        } catch (Exception ex) {
            return AuthorizationDecision.invalid("invalid_payload", "current");
        }
        final JsonNode requested;
        try {
            requested = MAPPER.readTree(requestedPayload);
        } catch (Exception ex) {
            return AuthorizationDecision.invalid("invalid_payload", "requested");
        }
        JsonNode canonicalCurrent = canonicalizeCurrent(current);
        ValidationResult currentValidation = validateState(canonicalCurrent);
        if (!currentValidation.valid()) {
            return AuthorizationDecision.invalid(currentValidation, "current");
        }
        ValidationResult requestedValidation = validateState(requested);
        if (!requestedValidation.valid()) {
            return AuthorizationDecision.invalid(requestedValidation, "requested");
        }
        if (!Objects.equals(canonicalCurrent.get("logs"), requested.get("logs"))
                || requested.has("audit_logs") || requested.has("auditLogs")) {
            return denied(AuditAction.STATE_UPDATE_REJECTED, "AUDIT_LOG", "official_audit_data");
        }
        if (hasUnknownTopLevelField(canonicalCurrent) || hasUnknownTopLevelField(requested)) {
            return denied(AuditAction.STATE_UPDATE_REJECTED, "APP_STATE", "unsupported_section");
        }
        if (principal.isAdmin()) {
            return AuthorizationDecision.permit();
        }

        AuthorizationDecision decision = authorizeUsers(canonicalCurrent, requested, principal.getExternalId());
        if (!decision.allowed()) return decision;
        if (!Objects.equals(canonicalCurrent.get("boardGames"), requested.get("boardGames"))) {
            return denied(AuditAction.STATE_UPDATE_REJECTED, "BOARD_GAME", "admin_required");
        }
        decision = authorizeSessions(canonicalCurrent, requested, principal.getExternalId());
        if (!decision.allowed()) return decision;
        return authorizeEvents(canonicalCurrent, requested, principal.getExternalId());
    }

    private static JsonNode canonicalizeCurrent(JsonNode current) {
        if (current == null || !current.isObject()) return current;
        ObjectNode canonical = current.deepCopy();
        canonicalizeEntityArray(canonical.get("users"), USER_FIELDS, null);
        canonicalizeEntityArray(canonical.get("boardGames"), GAME_FIELDS, null);
        canonicalizeEntityArray(canonical.get("events"), EVENT_FIELDS, null);
        canonicalizeEntityArray(canonical.get("sessions"), SESSION_FIELDS, "comments");
        return canonical;
    }

    private static void canonicalizeEntityArray(
            JsonNode candidate, Set<String> allowedFields, String nestedCommentsField) {
        if (!(candidate instanceof ArrayNode array)) return;
        for (JsonNode item : array) {
            if (!(item instanceof ObjectNode entity)) continue;
            if (nestedCommentsField != null) {
                canonicalizeEntityArray(entity.get(nestedCommentsField), COMMENT_FIELDS, null);
            }
            entity.retain(allowedFields);
        }
    }

    private static AuthorizationDecision authorizeUsers(JsonNode current, JsonNode requested, String actorId) {
        Map<String, JsonNode> before = byId(current.get("users"));
        Map<String, JsonNode> after = byId(requested.get("users"));
        if (before == null || after == null) return AuthorizationDecision.invalid("invalid_users", "requested");
        if (!before.keySet().equals(after.keySet())) {
            return denied(AuditAction.STATE_UPDATE_REJECTED, "USER", "admin_required");
        }
        for (String id : before.keySet()) {
            JsonNode oldUser = before.get(id);
            JsonNode newUser = after.get(id);
            if (Objects.equals(oldUser, newUser)) continue;
            if (!id.equals(actorId)) {
                if (!onlyValidDerivedUserFieldsChanged(oldUser, newUser, requested)) {
                    return denied(AuditAction.STATE_UPDATE_REJECTED, "USER", "resource_owner_mismatch");
                }
                continue;
            }
            if (!Objects.equals(oldUser.get("role"), newUser.get("role"))) {
                return denied(AuditAction.ROLE_CHANGE_REJECTED, "USER", "role_change");
            }
            Set<String> ownAndDerivedFields = new HashSet<>(OWN_PROFILE_FIELDS);
            ownAndDerivedFields.add("winCount");
            if (!onlyFieldsChanged(oldUser, newUser, ownAndDerivedFields)
                    || !validWinCount(newUser, requested)) {
                return denied(AuditAction.STATE_UPDATE_REJECTED, "USER", "protected_profile_field");
            }
        }
        return AuthorizationDecision.permit();
    }

    private static boolean onlyValidDerivedUserFieldsChanged(
            JsonNode oldUser, JsonNode newUser, JsonNode requestedState) {
        if (!onlyFieldsChanged(oldUser, newUser, Set.of("winCount", "favoriteGames"))) return false;
        if (!validWinCount(newUser, requestedState)) return false;
        String userId = newUser.path("id").asText();
        Set<String> playedGames = new HashSet<>();
        for (JsonNode session : requestedState.path("sessions")) {
            if (containsText(session.path("participantIds"), userId)) {
                playedGames.add(session.path("gameId").asText());
            }
        }
        Set<String> previousFavorites = textSet(oldUser.path("favoriteGames"));
        Set<String> nextFavorites = textSet(newUser.path("favoriteGames"));
        Set<String> permittedFavorites = new HashSet<>(previousFavorites);
        permittedFavorites.addAll(playedGames);
        return permittedFavorites.containsAll(nextFavorites)
                && nextFavorites.containsAll(previousFavorites);
    }

    private static boolean validWinCount(JsonNode user, JsonNode requestedState) {
        String userId = user.path("id").asText();
        int expectedWins = 0;
        for (JsonNode session : requestedState.path("sessions")) {
            if (userId.equals(session.path("winnerId").asText(null))) expectedWins++;
        }
        return user.path("winCount").asInt() == expectedWins;
    }

    private static AuthorizationDecision authorizeSessions(
            JsonNode current, JsonNode requested, String actorId) {
        Map<String, JsonNode> before = byId(current.get("sessions"));
        Map<String, JsonNode> after = byId(requested.get("sessions"));
        if (before == null || after == null) return AuthorizationDecision.invalid("invalid_sessions", "requested");

        for (String id : before.keySet()) {
            if (!after.containsKey(id)) {
                if (!actorId.equals(before.get(id).path("organizerId").asText())) {
                    return denied(AuditAction.STATE_UPDATE_REJECTED, "SESSION", "resource_owner_mismatch");
                }
                continue;
            }
            JsonNode oldSession = before.get(id);
            JsonNode newSession = after.get(id);
            if (Objects.equals(oldSession, newSession)) continue;
            if (!Objects.equals(oldSession.get("organizerId"), newSession.get("organizerId"))) {
                return denied(AuditAction.STATE_UPDATE_REJECTED, "SESSION", "organizer_identity");
            }
            if (actorId.equals(oldSession.path("organizerId").asText())) {
                if (!commentsAuthorized(oldSession.path("comments"), newSession.path("comments"),
                        actorId, requested)) {
                    return denied(AuditAction.STATE_UPDATE_REJECTED, "COMMENT", "comment_author_mismatch");
                }
            } else if (!onlyFieldsChanged(oldSession, newSession, Set.of("comments"))
                    || !commentsAuthorized(oldSession.path("comments"), newSession.path("comments"),
                    actorId, requested)) {
                return denied(AuditAction.STATE_UPDATE_REJECTED, "SESSION", "resource_owner_mismatch");
            }
        }
        for (String id : after.keySet()) {
            if (!before.containsKey(id)) {
                JsonNode session = after.get(id);
                if (!actorId.equals(session.path("organizerId").asText())
                        || !commentsBelongTo(session.path("comments"), actorId, requested)) {
                    return denied(AuditAction.STATE_UPDATE_REJECTED, "SESSION", "organizer_identity");
                }
            }
        }
        return AuthorizationDecision.permit();
    }

    private static AuthorizationDecision authorizeEvents(
            JsonNode current, JsonNode requested, String actorId) {
        Map<String, JsonNode> before = byId(current.get("events"));
        Map<String, JsonNode> after = byId(requested.get("events"));
        if (before == null || after == null) return AuthorizationDecision.invalid("invalid_events", "requested");

        for (String id : before.keySet()) {
            JsonNode oldEvent = before.get(id);
            JsonNode newEvent = after.get(id);
            if (newEvent == null) {
                if (!actorId.equals(oldEvent.path("organizerId").asText())) {
                    return denied(AuditAction.EVENT_UPDATE_REJECTED, "EVENT", "resource_owner_mismatch");
                }
                continue;
            }
            if (Objects.equals(oldEvent, newEvent)) continue;
            if (!Objects.equals(oldEvent.get("organizerId"), newEvent.get("organizerId"))) {
                return denied(AuditAction.EVENT_UPDATE_REJECTED, "EVENT", "organizer_identity");
            }
            boolean membershipChanged = !Objects.equals(
                    oldEvent.get("participantIds"), newEvent.get("participantIds"))
                    || !Objects.equals(oldEvent.get("waitingListIds"), newEvent.get("waitingListIds"));
            if (actorId.equals(oldEvent.path("organizerId").asText())) {
                if (membershipChanged && !ownMembershipChange(oldEvent, newEvent, actorId)) {
                    return denied(AuditAction.EVENT_UPDATE_REJECTED, "EVENT", "participant_identity");
                }
                continue;
            }
            if (!onlyFieldsChanged(oldEvent, newEvent, Set.of("participantIds", "waitingListIds"))
                    || !ownMembershipChange(oldEvent, newEvent, actorId)) {
                return denied(AuditAction.EVENT_UPDATE_REJECTED, "EVENT", "resource_owner_mismatch");
            }
        }
        for (String id : after.keySet()) {
            if (!before.containsKey(id)) {
                JsonNode event = after.get(id);
                if (!actorId.equals(event.path("organizerId").asText())
                        || !textSet(event.path("participantIds")).equals(Set.of(actorId))
                        || !event.path("waitingListIds").isEmpty()) {
                    return denied(AuditAction.EVENT_UPDATE_REJECTED, "EVENT", "organizer_identity");
                }
            }
        }
        return AuthorizationDecision.permit();
    }

    private static boolean ownMembershipChange(JsonNode before, JsonNode after, String actorId) {
        Set<String> oldParticipants = textSet(before.path("participantIds"));
        Set<String> newParticipants = textSet(after.path("participantIds"));
        Set<String> oldWaiting = textSet(before.path("waitingListIds"));
        Set<String> newWaiting = textSet(after.path("waitingListIds"));

        Set<String> changedPeople = new HashSet<>();
        symmetricDifference(oldParticipants, newParticipants, changedPeople);
        symmetricDifference(oldWaiting, newWaiting, changedPeople);
        changedPeople.remove(actorId);
        if (changedPeople.isEmpty()) return true;

        // Leaving a full event legitimately promotes the first waiting user.
        if (oldParticipants.contains(actorId) && !newParticipants.contains(actorId)
                && before.path("waitingListIds").isArray() && before.path("waitingListIds").size() > 0) {
            String promoted = before.path("waitingListIds").get(0).asText();
            return changedPeople.equals(Set.of(promoted))
                    && newParticipants.contains(promoted) && !newWaiting.contains(promoted);
        }
        return false;
    }

    private static boolean commentsAuthorized(
            JsonNode before, JsonNode after, String actorId, JsonNode requestedState) {
        Map<String, JsonNode> oldComments = byId(before);
        Map<String, JsonNode> newComments = byId(after);
        if (oldComments == null || newComments == null) return false;
        Set<String> ids = new HashSet<>(oldComments.keySet());
        ids.addAll(newComments.keySet());
        for (String id : ids) {
            JsonNode oldComment = oldComments.get(id);
            JsonNode newComment = newComments.get(id);
            if (Objects.equals(oldComment, newComment)) continue;
            JsonNode ownerSource = oldComment != null ? oldComment : newComment;
            if (!actorId.equals(ownerSource.path("userId").asText())) return false;
            if (oldComment != null && newComment != null
                    && !Objects.equals(oldComment.get("userId"), newComment.get("userId"))) return false;
            if (newComment != null && !commentIdentityMatches(newComment, actorId, requestedState)) return false;
        }
        return true;
    }

    private static boolean commentsBelongTo(JsonNode comments, String actorId, JsonNode requestedState) {
        if (!comments.isArray()) return false;
        for (JsonNode comment : comments) {
            if (!actorId.equals(comment.path("userId").asText())
                    || !commentIdentityMatches(comment, actorId, requestedState)) return false;
        }
        return true;
    }

    private static boolean commentIdentityMatches(
            JsonNode comment, String actorId, JsonNode requestedState) {
        Map<String, JsonNode> users = byId(requestedState.path("users"));
        JsonNode actor = users == null ? null : users.get(actorId);
        if (actor == null) return false;
        return Objects.equals(comment.path("userName").asText(), actor.path("name").asText())
                && Objects.equals(comment.path("userAvatar").asText(), actor.path("avatar").asText());
    }

    private static boolean hasUnknownTopLevelField(JsonNode requested) {
        Iterator<String> fields = requested.fieldNames();
        while (fields.hasNext()) if (!STATE_SECTIONS.contains(fields.next())) return true;
        return false;
    }

    private static ValidationResult validateState(JsonNode state) {
        if (state == null || !state.isObject()) {
            return ValidationResult.invalid("invalid_state_object", null, null, null,
                    "expected JSON object");
        }
        for (String section : Set.of("users", "boardGames", "sessions", "events")) {
            JsonNode value = state.get(section);
            if (value == null) {
                return ValidationResult.invalid("missing_section", section, null, null,
                        "required section is missing");
            }
            if (!value.isArray()) {
                return ValidationResult.invalid("expected_entity_array", section, null, null,
                        "expected array of entities");
            }
        }
        if (state.has("logs") && !state.path("logs").isArray()) {
            return ValidationResult.invalid("expected_entity_array", "logs", null, null,
                    "expected array of audit records");
        }

        ValidationResult result = validateEntityArray(state.get("users"), "users", USER_FIELDS);
        if (!result.valid()) return result;
        result = validateEntityArray(state.get("boardGames"), "boardGames", GAME_FIELDS);
        if (!result.valid()) return result;
        result = validateEntityArray(state.get("sessions"), "sessions", SESSION_FIELDS);
        if (!result.valid()) return result;
        result = validateEntityArray(state.get("events"), "events", EVENT_FIELDS);
        if (!result.valid()) return result;

        for (JsonNode user : state.path("users")) {
            result = validateTextArray(user.get("favoriteGames"), true, "users",
                    user.path("id").asText(null), "favoriteGames");
            if (!result.valid()) return result;
        }
        for (JsonNode session : state.path("sessions")) {
            String id = session.path("id").asText(null);
            result = validateTextArray(session.get("participantIds"), true,
                    "sessions", id, "participantIds");
            if (!result.valid()) return result;
            result = validateTextArray(session.get("photos"), false,
                    "sessions", id, "photos");
            if (!result.valid()) return result;
            result = validateEntityArray(session.get("comments"), "sessions", COMMENT_FIELDS, id, "comments");
            if (!result.valid()) return result;
        }
        for (JsonNode event : state.path("events")) {
            String id = event.path("id").asText(null);
            result = validateTextArray(event.get("participantIds"), true,
                    "events", id, "participantIds");
            if (!result.valid()) return result;
            result = validateTextArray(event.get("waitingListIds"), true,
                    "events", id, "waitingListIds");
            if (!result.valid()) return result;
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateEntityArray(
            JsonNode array, String section, Set<String> allowedFields) {
        return validateEntityArray(array, section, allowedFields, null, null);
    }

    private static ValidationResult validateEntityArray(
            JsonNode array, String section, Set<String> allowedFields,
            String parentResourceId, String parentField) {
        if (array == null || !array.isArray()) {
            return ValidationResult.invalid("expected_entity_array", section,
                    parentResourceId, parentField, "expected array of entities");
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode entity : array) {
            if (!entity.isObject()) {
                return ValidationResult.invalid("invalid_entity", section,
                        parentResourceId, parentField, "expected object entity");
            }
            String id = entity.path("id").asText("");
            if (id.isBlank()) {
                return ValidationResult.invalid("missing_id", section,
                        parentResourceId, parentField == null ? "id" : parentField, "entity id is required");
            }
            if (!ids.add(id)) {
                return ValidationResult.invalid("duplicate_id", section,
                        parentResourceId == null ? id : parentResourceId,
                        parentField == null ? "id" : parentField, "entity id must be unique");
            }
            Iterator<String> fields = entity.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                if (!allowedFields.contains(field)) {
                    return ValidationResult.invalid("unknown_field", section,
                            parentResourceId == null ? id : parentResourceId,
                            parentField == null ? field : parentField,
                            "field is not supported");
                }
            }
        }
        return ValidationResult.ok();
    }

    private static ValidationResult validateTextArray(
            JsonNode array, boolean unique, String section, String resourceId, String field) {
        if (array == null || !array.isArray()) {
            return ValidationResult.invalid("expected_text_array", section, resourceId, field,
                    "expected array of strings");
        }
        Set<String> values = new HashSet<>();
        for (JsonNode item : array) {
            if (!item.isTextual()) {
                return ValidationResult.invalid("expected_text_array", section, resourceId, field,
                        "expected array of strings");
            }
            if (item.asText().isBlank()) {
                return ValidationResult.invalid("blank_value", section, resourceId, field,
                        "blank values are not allowed");
            }
            if (unique && !values.add(item.asText())) {
                return ValidationResult.invalid("duplicate_values", section, resourceId, field,
                        "array values must be unique");
            }
        }
        return ValidationResult.ok();
    }

    private static Map<String, JsonNode> byId(JsonNode array) {
        if (array == null || !array.isArray()) return null;
        Map<String, JsonNode> values = new HashMap<>();
        for (JsonNode value : array) {
            if (!value.isObject()) return null;
            String id = value.path("id").asText("");
            if (id.isBlank() || values.put(id, value) != null) return null;
        }
        return values;
    }

    private static boolean onlyFieldsChanged(JsonNode before, JsonNode after, Set<String> allowed) {
        Set<String> fields = new HashSet<>();
        Iterator<String> beforeNames = before.fieldNames();
        Iterator<String> afterNames = after.fieldNames();
        beforeNames.forEachRemaining(fields::add);
        afterNames.forEachRemaining(fields::add);
        for (String field : fields) {
            if (!allowed.contains(field) && !Objects.equals(before.get(field), after.get(field))) return false;
        }
        return true;
    }

    private static boolean containsText(JsonNode array, String value) {
        if (!array.isArray()) return false;
        for (JsonNode item : array) if (value.equals(item.asText())) return true;
        return false;
    }

    private static Set<String> textSet(JsonNode array) {
        Set<String> values = new HashSet<>();
        if (array.isArray()) for (JsonNode item : array) values.add(item.asText());
        return values;
    }

    private static void symmetricDifference(Set<String> first, Set<String> second, Set<String> target) {
        for (String value : first) if (!second.contains(value)) target.add(value);
        for (String value : second) if (!first.contains(value)) target.add(value);
    }

    private static AuthorizationDecision denied(
            AuditAction action, String resourceType, String reason) {
        return new AuthorizationDecision(false, false, action, resourceType, reason,
                null, null, null, null, null, null);
    }

    public record AuthorizationDecision(
            boolean allowed,
            boolean invalidPayload,
            AuditAction auditAction,
            String resourceType,
            String reason,
            String reasonCode,
            String section,
            String resourceId,
            String field,
            String detail,
            String validationSource) {
        public static AuthorizationDecision permit() {
            return new AuthorizationDecision(true, false, null, null, null,
                    null, null, null, null, null, null);
        }

        public static AuthorizationDecision invalid(String reason, String validationSource) {
            return new AuthorizationDecision(false, true, AuditAction.STATE_UPDATE_REJECTED,
                    "APP_STATE", reason, "invalid_json", null, null, null, "invalid JSON payload",
                    validationSource);
        }

        public static AuthorizationDecision invalid(ValidationResult validation, String validationSource) {
            return new AuthorizationDecision(false, true, AuditAction.STATE_UPDATE_REJECTED,
                    "APP_STATE", "invalid_payload", validation.reasonCode(), validation.section(),
                    validation.resourceId(), validation.field(), validation.detail(), validationSource);
        }
    }

    public record ValidationResult(
            boolean valid,
            String reasonCode,
            String section,
            String resourceId,
            String field,
            String detail) {
        static ValidationResult ok() {
            return new ValidationResult(true, null, null, null, null, null);
        }

        static ValidationResult invalid(
                String reasonCode, String section, String resourceId, String field, String detail) {
            return new ValidationResult(false, reasonCode, section, resourceId, field, detail);
        }
    }
}
