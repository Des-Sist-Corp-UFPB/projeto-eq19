package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateAuthorizationServiceTest {
    private final StateAuthorizationService service = new StateAuthorizationService();
    private final AuthenticatedPrincipal user = new AuthenticatedPrincipal(1, "u1", "USER");
    private final AuthenticatedPrincipal admin = new AuthenticatedPrincipal(9, "admin", "ADMIN");

    @Test
    void allowsUserToEditOwnProfileAndFavorites() {
        String next = baseState()
                .replace("\"name\":\"Ana\"", "\"name\":\"Ana Silva\"")
                .replace("\"favoriteGames\":[]", "\"favoriteGames\":[\"g1\"]");

        assertTrue(service.authorize(baseState(), next, user).allowed());
    }

    @Test
    void rejectsEditingAnotherUsersProfile() {
        String next = baseState().replace("\"name\":\"Bruno\"", "\"name\":\"Bruno Silva\"");

        var decision = service.authorize(baseState(), next, user);

        assertDenied(decision, AuditAction.STATE_UPDATE_REJECTED, "USER");
    }

    @Test
    void rejectsSelfPromotion() {
        String next = baseState().replace("\"role\":\"student\"", "\"role\":\"admin\"");

        var decision = service.authorize(baseState(), next, user);

        assertDenied(decision, AuditAction.ROLE_CHANGE_REJECTED, "USER");
    }

    @Test
    void rejectsChangingAnotherOrganizersEvent() {
        String next = baseState().replace("\"location\":\"Sala B\"", "\"location\":\"Sala C\"");

        var decision = service.authorize(baseState(), next, user);

        assertDenied(decision, AuditAction.EVENT_UPDATE_REJECTED, "EVENT");
    }

    @Test
    void rejectsCancellingAnotherOrganizersEvent() {
        String next = baseState().replace(
                "\"organizerId\":\"u2\",\"location\":\"Sala B\",\"participantIds\":[\"u2\"],\"waitingListIds\":[],\"status\":\"active\"",
                "\"organizerId\":\"u2\",\"location\":\"Sala B\",\"participantIds\":[\"u2\"],\"waitingListIds\":[],\"status\":\"cancelled\"");

        assertDenied(service.authorize(baseState(), next, user),
                AuditAction.EVENT_UPDATE_REJECTED, "EVENT");
    }

    @Test
    void allowsOrganizerToEditOrCancelOwnEvent() {
        String next = baseState()
                .replace("\"location\":\"Sala A\"", "\"location\":\"Sala Nova\"")
                .replaceFirst("\"status\":\"active\"", "\"status\":\"cancelled\"");

        assertTrue(service.authorize(baseState(), next, user).allowed());
    }

    @Test
    void allowsUserToJoinAndLeaveOnlyInOwnName() {
        String joined = baseState().replace(
                "\"participantIds\":[\"u2\"],\"waitingListIds\":[]",
                "\"participantIds\":[\"u2\",\"u1\"],\"waitingListIds\":[]");
        assertTrue(service.authorize(baseState(), joined, user).allowed());
        assertTrue(service.authorize(joined, baseState(), user).allowed());
    }

    @Test
    void allowsExpectedWaitingListPromotionWhenUserLeaves() {
        String before = baseState().replace(
                "\"participantIds\":[\"u2\"],\"waitingListIds\":[]",
                "\"participantIds\":[\"u2\",\"u1\"],\"waitingListIds\":[\"u3\"]");
        String after = before.replace(
                "\"participantIds\":[\"u2\",\"u1\"],\"waitingListIds\":[\"u3\"]",
                "\"participantIds\":[\"u2\",\"u3\"],\"waitingListIds\":[]");

        assertTrue(service.authorize(before, after, user).allowed());
    }

    @Test
    void rejectsChangingAnotherUsersParticipation() {
        String next = baseState().replace(
                "\"participantIds\":[\"u2\"],\"waitingListIds\":[]",
                "\"participantIds\":[\"u2\",\"u3\"],\"waitingListIds\":[]");

        assertDenied(service.authorize(baseState(), next, user),
                AuditAction.EVENT_UPDATE_REJECTED, "EVENT");
    }

    @Test
    void rejectsEditingOrRemovingAnotherUsersComment() {
        String edited = baseState().replace("\"content\":\"Original\"", "\"content\":\"Alterado\"");
        String removed = baseState().replace(comment(), "");

        assertDenied(service.authorize(baseState(), edited, user),
                AuditAction.STATE_UPDATE_REJECTED, "SESSION");
        assertDenied(service.authorize(baseState(), removed, user),
                AuditAction.STATE_UPDATE_REJECTED, "SESSION");
    }

    @Test
    void allowsCreatingEditingAndRemovingOwnComment() {
        String ownComment = """
                {"id":"c2","userId":"u1","userName":"Ana","userAvatar":"A","content":"Meu comentário","createdAt":"2026-07-30T12:00:00Z"}""";
        String added = baseState().replace(comment(), comment() + "," + ownComment);
        String edited = added.replace("Meu comentário", "Meu comentário editado");

        assertTrue(service.authorize(baseState(), added, user).allowed());
        assertTrue(service.authorize(added, edited, user).allowed());
        assertTrue(service.authorize(edited, baseState(), user).allowed());
    }

    @Test
    void rejectsSpoofedCommentIdentityAndProtectedSections() {
        String spoofedComment = baseState().replace(comment(), comment() + """
                ,{"id":"c2","userId":"u1","userName":"Bruno","userAvatar":"B","content":"Falso","createdAt":"2026-07-30T12:00:00Z"}""");
        String changedGame = baseState().replace("\"name\":\"Xadrez\"", "\"name\":\"Xadrez adulterado\"");
        String changedSession = baseState().replace(
                "\"organizerId\":\"u2\",\"participantIds\":[\"u2\"]",
                "\"organizerId\":\"u2\",\"participantIds\":[\"u2\",\"u1\"]");

        assertFalse(service.authorize(baseState(), spoofedComment, user).allowed());
        assertFalse(service.authorize(baseState(), changedGame, user).allowed());
        assertFalse(service.authorize(baseState(), changedSession, user).allowed());
    }

    @Test
    void rejectsCreatingResourcesAsAnotherOrganizer() {
        String newEvent = """
                ,{"id":"e3","gameId":"g1","organizerId":"u2","location":"Sala C","participantIds":["u2"],"waitingListIds":[],"status":"active"}""";
        String next = baseState().replace(
                "\"status\":\"active\"}\n  ],\n  \"logs\"",
                "\"status\":\"active\"}" + newEvent + "\n  ],\n  \"logs\"");

        assertDenied(service.authorize(baseState(), next, user),
                AuditAction.EVENT_UPDATE_REJECTED, "EVENT");
    }

    @Test
    void rejectsOfficialLogInsertion() {
        String next = baseState().replace("\"logs\":[]", "\"logs\":[{\"id\":\"forged\"}]");

        var decision = service.authorize(baseState(), next, user);

        assertDenied(decision, AuditAction.STATE_UPDATE_REJECTED, "AUDIT_LOG");
    }

    @Test
    void preservesAdministrativeOperationsButNeverAcceptsClientAuditLogs() {
        String adminChange = baseState()
                .replace("\"role\":\"student\"", "\"role\":\"admin\"")
                .replace("\"name\":\"Bruno\"", "\"name\":\"Bruno Admin\"");
        assertTrue(service.authorize(baseState(), adminChange, admin).allowed());

        String forgedLog = adminChange.replace("\"logs\":[]", "\"logs\":[{\"id\":\"forged\"}]");
        assertFalse(service.authorize(baseState(), forgedLog, admin).allowed());
    }

    @Test
    void rejectsMalformedOrIncompleteState() {
        var malformed = service.authorize(baseState(), "{", user);
        var incomplete = service.authorize(baseState(), "{\"users\":[]}", user);

        assertTrue(malformed.invalidPayload());
        assertTrue(incomplete.invalidPayload());
    }

    @Test
    void rejectsInvalidIdsArraysAndUnknownFieldsEvenForAdmin() {
        String duplicateUser = baseState().replace("\"id\":\"u2\"", "\"id\":\"u1\"");
        String missingParticipantArray = baseState().replace("\"participantIds\":[\"u2\"]", "\"participantIds\":null");
        String duplicateParticipant = baseState().replace(
                "\"participantIds\":[\"u2\"]", "\"participantIds\":[\"u2\",\"u2\"]");
        String unknownField = baseState().replace(
                "\"name\":\"Xadrez\"", "\"name\":\"Xadrez\",\"ownerId\":\"u1\"");
        String unknownSection = baseState().replace(
                "\"logs\":[]", "\"logs\":[],\"permissions\":[]");

        assertTrue(service.authorize(baseState(), duplicateUser, admin).invalidPayload());
        assertTrue(service.authorize(baseState(), missingParticipantArray, admin).invalidPayload());
        assertTrue(service.authorize(baseState(), duplicateParticipant, admin).invalidPayload());
        assertTrue(service.authorize(baseState(), unknownField, admin).invalidPayload());
        assertFalse(service.authorize(baseState(), unknownSection, admin).allowed());
    }

    @Test
    void reportsSafeStructuredValidationDiagnostics() {
        assertValidation(
                baseState().replace("\"name\":\"Xadrez\"", "\"name\":\"Xadrez\",\"tags\":[\"strategy\"]"),
                "unknown_field", "boardGames", "g1", "tags");
        assertValidation(
                baseState().replace("\"id\":\"u2\"", "\"id\":\"u1\""),
                "duplicate_id", "users", "u1", "id");
        assertValidation(
                baseState().replaceFirst("\"favoriteGames\":\\[\\]", "\"favoriteGames\":null"),
                "expected_text_array", "users", "u1", "favoriteGames");
        assertValidation(
                baseState().replace("\"participantIds\":[\"u2\"]", "\"participantIds\":[\"u2\",\"u2\"]"),
                "duplicate_values", "sessions", "s1", "participantIds");
        assertValidation(
                baseState().replace("\"photos\":[],\"comments\"", "\"photos\":null,\"comments\""),
                "expected_text_array", "sessions", "s1", "photos");
        assertValidation(
                baseState().replace("\"comments\":[" + comment() + "]", "\"comments\":null"),
                "expected_entity_array", "sessions", "s1", "comments");
        assertValidation(
                baseState().replaceFirst(",\"waitingListIds\":\\[\\]", ""),
                "expected_text_array", "events", "e1", "waitingListIds");
    }

    @Test
    void distinguishesMissingSectionsAndInvalidEntityArrays() {
        assertValidation(
                baseState().replace("\"boardGames\":[{\"id\":\"g1\",\"name\":\"Xadrez\"}],", ""),
                "missing_section", "boardGames", null, null);
        assertValidation(
                baseState().replace("\"boardGames\":[{\"id\":\"g1\",\"name\":\"Xadrez\"}]", "\"boardGames\":{}"),
                "expected_entity_array", "boardGames", null, null);
    }

    @Test
    void rejectsOrganizerChangingThirdPartyParticipationOrAddingParticipantsOnCreation() {
        String organizerChangesThirdParty = baseState().replace(
                "\"participantIds\":[\"u1\"],\"waitingListIds\":[]",
                "\"participantIds\":[\"u1\",\"u2\"],\"waitingListIds\":[]");
        String newEvent = """
                ,{"id":"e3","gameId":"g1","organizerId":"u1","location":"Sala C","participantIds":["u1","u2"],"waitingListIds":[],"status":"active"}""";
        String createsWithThirdParty = baseState().replace(
                "\"status\":\"active\"}\n  ],\n  \"logs\"",
                "\"status\":\"active\"}" + newEvent + "\n  ],\n  \"logs\"");

        assertDenied(service.authorize(baseState(), organizerChangesThirdParty, user),
                AuditAction.EVENT_UPDATE_REJECTED, "EVENT");
        assertDenied(service.authorize(baseState(), createsWithThirdParty, user),
                AuditAction.EVENT_UPDATE_REJECTED, "EVENT");
    }

    private static void assertDenied(
            StateAuthorizationService.AuthorizationDecision decision,
            AuditAction action,
            String resourceType) {
        assertFalse(decision.allowed());
        assertFalse(decision.invalidPayload());
        assertEquals(action, decision.auditAction());
        assertEquals(resourceType, decision.resourceType());
    }

    private void assertValidation(
            String payload, String reasonCode, String section, String resourceId, String field) {
        var decision = service.authorize(baseState(), payload, admin);
        assertTrue(decision.invalidPayload(), decision.toString());
        assertEquals("invalid_payload", decision.reason());
        assertEquals(reasonCode, decision.reasonCode());
        assertEquals(section, decision.section());
        assertEquals(resourceId, decision.resourceId());
        assertEquals(field, decision.field());
    }

    private static String baseState() {
        return """
                {
                  "users":[
                    {"id":"u1","name":"Ana","email":"ana@example.com","role":"student","course":"SI","avatar":"A","winCount":0,"favoriteGames":[],"joinedAt":"2026-01-01","bio":""},
                    {"id":"u2","name":"Bruno","email":"bruno@example.com","role":"student","course":"SI","avatar":"B","winCount":0,"favoriteGames":[],"joinedAt":"2026-01-01","bio":""}
                  ],
                  "boardGames":[{"id":"g1","name":"Xadrez"}],
                  "sessions":[
                    {"id":"s1","gameId":"g1","organizerId":"u2","participantIds":["u2"],"winnerId":null,"photos":[],"comments":[%s]}
                  ],
                  "events":[
                    {"id":"e1","gameId":"g1","organizerId":"u1","location":"Sala A","participantIds":["u1"],"waitingListIds":[],"status":"active"},
                    {"id":"e2","gameId":"g1","organizerId":"u2","location":"Sala B","participantIds":["u2"],"waitingListIds":[],"status":"active"}
                  ],
                  "logs":[]
                }
                """.formatted(comment());
    }

    private static String comment() {
        return """
                {"id":"c1","userId":"u2","userName":"Bruno","userAvatar":"B","content":"Original","createdAt":"2026-07-30T10:00:00Z"}""";
    }
}
