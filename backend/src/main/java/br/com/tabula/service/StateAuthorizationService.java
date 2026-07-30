package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        final JsonNode requested;
        try {
            current = MAPPER.readTree(currentPayload);
            requested = MAPPER.readTree(requestedPayload);
        } catch (Exception ex) {
            return AuthorizationDecision.invalid("invalid_payload");
        }
        if (!isStateObject(current) || !isStateObject(requested)
                || !hasValidStructure(current) || !hasValidStructure(requested)) {
            return AuthorizationDecision.invalid("invalid_payload");
        }
        if (!Objects.equals(current.get("logs"), requested.get("logs"))
                || requested.has("audit_logs") || requested.has("auditLogs")) {
            return denied(AuditAction.STATE_UPDATE_REJECTED, "AUDIT_LOG", "official_audit_data");
        }
        if (hasUnknownTopLevelField(current) || hasUnknownTopLevelField(requested)) {
            return denied(AuditAction.STATE_UPDATE_REJECTED, "APP_STATE", "unsupported_section");
        }
        if (principal.isAdmin()) {
            return AuthorizationDecision.permit();
        }

        AuthorizationDecision decision = authorizeUsers(current, requested, principal.getExternalId());
        if (!decision.allowed()) return decision;
        if (!Objects.equals(current.get("boardGames"), requested.get("boardGames"))) {
            return denied(AuditAction.STATE_UPDATE_REJECTED, "BOARD_GAME", "admin_required");
        }
        decision = authorizeSessions(current, requested, principal.getExternalId());
        if (!decision.allowed()) return decision;
        return authorizeEvents(current, requested, principal.getExternalId());
    }

    private static AuthorizationDecision authorizeUsers(JsonNode current, JsonNode requested, String actorId) {
        Map<String, JsonNode> before = byId(current.get("users"));
        Map<String, JsonNode> after = byId(requested.get("users"));
        if (before == null || after == null) return AuthorizationDecision.invalid("invalid_users");
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
        if (before == null || after == null) return AuthorizationDecision.invalid("invalid_sessions");

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
        if (before == null || after == null) return AuthorizationDecision.invalid("invalid_events");

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

    private static boolean isStateObject(JsonNode node) {
        if (node == null || !node.isObject()) return false;
        for (String section : Set.of("users", "boardGames", "sessions", "events")) {
            if (!node.path(section).isArray()) return false;
        }
        return !node.has("logs") || node.path("logs").isArray();
    }

    private static boolean hasValidStructure(JsonNode state) {
        if (!validEntityArray(state.get("users"), USER_FIELDS)
                || !validEntityArray(state.get("boardGames"), GAME_FIELDS)
                || !validEntityArray(state.get("sessions"), SESSION_FIELDS)
                || !validEntityArray(state.get("events"), EVENT_FIELDS)) return false;

        for (JsonNode user : state.path("users")) {
            if (!isUniqueTextArray(user.get("favoriteGames"))) return false;
        }
        for (JsonNode session : state.path("sessions")) {
            if (!isUniqueTextArray(session.get("participantIds"))
                    || !isTextArray(session.get("photos"))
                    || !validEntityArray(session.get("comments"), COMMENT_FIELDS)) return false;
        }
        for (JsonNode event : state.path("events")) {
            if (!isUniqueTextArray(event.get("participantIds"))
                    || !isUniqueTextArray(event.get("waitingListIds"))) return false;
        }
        return true;
    }

    private static boolean validEntityArray(JsonNode array, Set<String> allowedFields) {
        if (byId(array) == null) return false;
        for (JsonNode entity : array) {
            Iterator<String> fields = entity.fieldNames();
            while (fields.hasNext()) if (!allowedFields.contains(fields.next())) return false;
        }
        return true;
    }

    private static boolean isUniqueTextArray(JsonNode array) {
        if (!isTextArray(array)) return false;
        Set<String> values = new HashSet<>();
        for (JsonNode item : array) {
            if (item.asText().isBlank() || !values.add(item.asText())) return false;
        }
        return true;
    }

    private static boolean isTextArray(JsonNode array) {
        if (array == null || !array.isArray()) return false;
        for (JsonNode item : array) if (!item.isTextual()) return false;
        return true;
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
        return new AuthorizationDecision(false, false, action, resourceType, reason);
    }

    public record AuthorizationDecision(
            boolean allowed,
            boolean invalidPayload,
            AuditAction auditAction,
            String resourceType,
            String reason) {
        public static AuthorizationDecision permit() {
            return new AuthorizationDecision(true, false, null, null, null);
        }

        public static AuthorizationDecision invalid(String reason) {
            return new AuthorizationDecision(false, true, AuditAction.STATE_UPDATE_REJECTED,
                    "APP_STATE", reason);
        }
    }
}
