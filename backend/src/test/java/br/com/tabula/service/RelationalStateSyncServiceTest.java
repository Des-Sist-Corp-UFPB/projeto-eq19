package br.com.tabula.service;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelationalStateSyncServiceTest {

    @Test
    void shouldHandleNullOrEmptyPayload() throws Exception {
        HikariDataSource ds = mock(HikariDataSource.class);
        
        RelationalStateSyncService.syncFromStateJson(ds, null);
        RelationalStateSyncService.syncFromStateJson(ds, "");
        RelationalStateSyncService.syncFromStateJson(ds, "   ");
        
        verify(ds, never()).getConnection();
    }

    @Test
    void shouldHandleEmptyJsonAndMissingArrays() throws Exception {
        HikariDataSource ds = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenAnswer(invocation -> {
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);
            when(rs.next()).thenReturn(false);
            return stmt;
        });

        assertDoesNotThrow(() -> RelationalStateSyncService.syncFromStateJson(ds, "{}"));

        verify(conn).setAutoCommit(false);
        verify(conn).commit();
        verify(conn, never()).rollback();
    }

    @Test
    void shouldSyncUsersWithDifferentScenarios() throws Exception {
        HikariDataSource ds = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);

        when(ds.getConnection()).thenReturn(conn);

        final int[] extIdQueryCount = {0};
        final int[] emailQueryCount = {0};
        final int[] cacheUsersQueryCount = {0};

        when(conn.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);

            if (sql.contains("SELECT id, email FROM usuarios WHERE external_id")) {
                // Called 5 times in sequence: u_1, u_2, u_3, u_4, u_5
                when(rs.next()).thenAnswer(inv -> {
                    extIdQueryCount[0]++;
                    return extIdQueryCount[0] <= 2; // true for u_1, u_2
                });
                when(rs.getLong("id")).thenAnswer(inv -> {
                    return extIdQueryCount[0] == 1 ? 101L : 102L;
                });
                when(rs.getString("email")).thenAnswer(inv -> {
                    return extIdQueryCount[0] == 1 ? "user1@example.com" : "user2_old@example.com";
                });
            } else if (sql.contains("SELECT 1 FROM usuarios WHERE lower(email) = lower(?) AND id <> ?")) {
                // Called for u_2 to check email conflict
                when(rs.next()).thenReturn(true);
            } else if (sql.contains("SELECT id FROM usuarios WHERE lower(email) = lower(?)")) {
                // Called for u_3, u_4, u_5
                when(rs.next()).thenAnswer(inv -> {
                    emailQueryCount[0]++;
                    return emailQueryCount[0] == 1; // true for u_3 only
                });
                when(rs.getLong("id")).thenReturn(103L);
            } else if (sql.contains("SELECT id, external_id FROM usuarios WHERE external_id IS NOT NULL")) {
                // Caching users
                when(rs.next()).thenAnswer(inv -> {
                    cacheUsersQueryCount[0]++;
                    return cacheUsersQueryCount[0] <= 5;
                });
                when(rs.getString("external_id")).thenAnswer(inv -> "u_" + cacheUsersQueryCount[0]);
                when(rs.getLong("id")).thenAnswer(inv -> 100L + cacheUsersQueryCount[0]);
            } else {
                when(rs.next()).thenReturn(false);
            }

            return stmt;
        });

        String payload = """
                {
                  "users": [
                    { "id": "u_1", "name": "User One", "email": "user1@example.com", "role": "student", "passwordHash": "h1" },
                    { "id": "u_2", "name": "User Two", "email": "conflict@example.com", "role": "student", "passwordHash": "h2" },
                    { "id": "u_3", "name": "User Three", "email": "user3@example.com", "role": "student" },
                    { "id": "u_4", "name": "User Four", "email": "user4@example.com", "role": "student" },
                    { "id": "u_5", "name": "Admin User", "email": "admin@example.com", "role": "admin" }
                  ]
                }
                """;

        assertDoesNotThrow(() -> RelationalStateSyncService.syncFromStateJson(ds, payload));
        verify(conn).commit();
    }

    @Test
    void shouldSyncBoardGamesWithConflicts() throws Exception {
        HikariDataSource ds = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);

        when(ds.getConnection()).thenReturn(conn);

        final int[] extGameQueryCount = {0};
        final int[] nameGameQueryCount = {0};
        final int[] cacheGamesQueryCount = {0};

        when(conn.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);

            if (sql.contains("SELECT id FROM jogos WHERE external_id")) {
                // Called 3 times for g1, g2, g3
                when(rs.next()).thenAnswer(inv -> {
                    extGameQueryCount[0]++;
                    return extGameQueryCount[0] == 1; // true for g1
                });
                when(rs.getLong("id")).thenReturn(501L);
            } else if (sql.contains("SELECT id FROM jogos WHERE nome")) {
                // Called for g2 and g3
                when(rs.next()).thenAnswer(inv -> {
                    nameGameQueryCount[0]++;
                    return nameGameQueryCount[0] == 1; // true for g2
                });
                when(rs.getLong("id")).thenReturn(502L);
            } else if (sql.contains("SELECT id, external_id FROM jogos WHERE external_id IS NOT NULL")) {
                // Cache games
                when(rs.next()).thenAnswer(inv -> {
                    cacheGamesQueryCount[0]++;
                    return cacheGamesQueryCount[0] <= 3;
                });
                when(rs.getString("external_id")).thenAnswer(inv -> "g" + cacheGamesQueryCount[0]);
                when(rs.getLong("id")).thenAnswer(inv -> 500L + cacheGamesQueryCount[0]);
            } else {
                when(rs.next()).thenReturn(false);
            }

            return stmt;
        });

        String payload = """
                {
                  "boardGames": [
                    { "id": "g1", "name": "Game One", "description": "Desc 1", "coverUrl": "url1", "category": "cat1", "minPlayers": 1, "maxPlayers": 4, "avgPlayTime": 30, "complexity": 1.5 },
                    { "id": "g2", "name": "Game Two", "description": "Desc 2", "coverUrl": "url2", "category": "cat2", "minPlayers": 2, "maxPlayers": 5, "avgPlayTime": 60, "complexity": 3.0 },
                    { "id": "g3", "name": "Game Three", "description": "Desc 3", "coverUrl": "url3", "category": "cat3" }
                  ]
                }
                """;

        assertDoesNotThrow(() -> RelationalStateSyncService.syncFromStateJson(ds, payload));
        verify(conn).commit();
    }

    @Test
    void shouldSyncFullDatabaseState() throws Exception {
        HikariDataSource ds = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);

        when(ds.getConnection()).thenReturn(conn);

        final int[] eventSessionIdGen = {0};

        when(conn.prepareStatement(anyString(), anyInt())).thenAnswer(invocation -> {
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet gkRs = mock(ResultSet.class);
            when(stmt.getGeneratedKeys()).thenReturn(gkRs);
            when(gkRs.next()).thenReturn(true);
            when(gkRs.getLong(1)).thenAnswer(inv -> {
                eventSessionIdGen[0]++;
                return eventSessionIdGen[0] == 1 ? 301L : 401L;
            });
            return stmt;
        });

        when(conn.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            PreparedStatement stmt = mock(PreparedStatement.class);
            ResultSet rs = mock(ResultSet.class);
            when(stmt.executeQuery()).thenReturn(rs);

            if (sql.contains("SELECT id, email FROM usuarios WHERE external_id")) {
                when(rs.next()).thenReturn(false);
            } else if (sql.contains("SELECT id FROM usuarios WHERE lower(email)")) {
                when(rs.next()).thenReturn(false);
            } else if (sql.contains("SELECT id, external_id FROM usuarios WHERE external_id IS NOT NULL")) {
                when(rs.next()).thenReturn(true, false);
                when(rs.getString("external_id")).thenReturn("u_1");
                when(rs.getLong("id")).thenReturn(101L);
            } else if (sql.contains("SELECT id FROM jogos WHERE external_id")) {
                when(rs.next()).thenReturn(false);
            } else if (sql.contains("SELECT id FROM jogos WHERE nome")) {
                when(rs.next()).thenReturn(false);
            } else if (sql.contains("SELECT id, external_id FROM jogos WHERE external_id IS NOT NULL")) {
                when(rs.next()).thenReturn(true, false);
                when(rs.getString("external_id")).thenReturn("g_1");
                when(rs.getLong("id")).thenReturn(201L);
            } else {
                when(rs.next()).thenReturn(false);
            }

            return stmt;
        });

        String payload = """
                {
                  "users": [
                    { "id": "u_1", "name": "User One", "email": "user1@example.com", "role": "student", "favoriteGames": ["g_1"] }
                  ],
                  "boardGames": [
                    { "id": "g_1", "name": "Game One", "description": "Desc 1", "coverUrl": "url1", "category": "cat1" }
                  ],
                  "events": [
                    {
                      "id": "e_1",
                      "gameId": "g_1",
                      "date": "2026-07-09",
                      "time": "18:00",
                      "location": "Loc 1",
                      "maxParticipants": 5,
                      "status": "active",
                      "organizerId": "u_1",
                      "participantIds": ["u_1"],
                      "waitingListIds": []
                    }
                  ],
                  "sessions": [
                    {
                      "id": "s_1",
                      "gameId": "g_1",
                      "date": "2026-07-09T19:49:37Z",
                      "location": "Loc 2",
                      "organizerId": "u_1",
                      "winnerId": "u_1",
                      "duration": 45,
                      "notes": "Notes 1",
                      "participantIds": ["u_1"],
                      "photos": ["photo1_url"],
                      "comments": [
                        {
                          "id": "c_1",
                          "userId": "u_1",
                          "userName": "User One",
                          "userAvatar": "avatar",
                          "content": "Comment content",
                          "createdAt": "2026-07-09T20:00:00Z"
                        }
                      ]
                    }
                  ],
                  "logs": [
                    {
                      "id": "l_1",
                      "userId": "u_1",
                      "userName": "User One",
                      "action": "Sync Action",
                      "timestamp": "2026-07-09T20:10:00Z"
                    }
                  ]
                }
                """;

        assertDoesNotThrow(() -> RelationalStateSyncService.syncFromStateJson(ds, payload, true));
        verify(conn).commit();
    }

    @Test
    void shouldRollbackTransactionOnFailure() throws Exception {
        HikariDataSource ds = mock(HikariDataSource.class);
        Connection conn = mock(Connection.class);

        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenAnswer(invocation -> {
            PreparedStatement stmt = mock(PreparedStatement.class);
            doThrow(new SQLException("database fail")).when(stmt).executeUpdate();
            return stmt;
        });

        Exception ex = assertThrows(SQLException.class, () -> 
            RelationalStateSyncService.syncFromStateJson(ds, "{\"users\": []}")
        );
        assertTrue(ex.getMessage().contains("database fail"));
        verify(conn).rollback();
    }

    @Test
    void shouldParseTimestampsSafely() {
        assertNotNull(RelationalStateSyncService.parseTimestamp("2026-07-09T19:49:37Z"));
        assertNotNull(RelationalStateSyncService.parseTimestamp("2026-07-09T19:49:37.000Z"));
        assertNotNull(RelationalStateSyncService.parseTimestamp("2026-07-09T19:49:37"));
        assertNotNull(RelationalStateSyncService.parseTimestamp("2026-07-09"));
        
        Timestamp now = new Timestamp(System.currentTimeMillis());
        Timestamp nullParsed = RelationalStateSyncService.parseTimestamp(null);
        Timestamp emptyParsed = RelationalStateSyncService.parseTimestamp("");
        Timestamp invalidParsed = RelationalStateSyncService.parseTimestamp("invalid-date");
        
        assertTrue(Math.abs(nullParsed.getTime() - now.getTime()) < 5000);
        assertTrue(Math.abs(emptyParsed.getTime() - now.getTime()) < 5000);
        assertTrue(Math.abs(invalidParsed.getTime() - now.getTime()) < 5000);
    }
    
    @Test
    void shouldMockPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<RelationalStateSyncService> constructor = RelationalStateSyncService.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        RelationalStateSyncService instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
