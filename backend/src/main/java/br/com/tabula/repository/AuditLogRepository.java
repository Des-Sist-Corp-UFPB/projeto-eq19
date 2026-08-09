package br.com.tabula.repository;

import br.com.tabula.dto.AuditLogFilter;
import br.com.tabula.model.AuditLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AuditLogRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HikariDataSource dataSource;

    public AuditLogRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(Connection connection, AuditLog log) throws SQLException {
        String sql = """
                INSERT INTO audit_logs (
                    usuario_id, ator_id_externo, acao, tipo_recurso, recurso_id,
                    detalhes, endereco_ip, user_agent, sucesso, trace_id
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::inet, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableLong(statement, 1, log.getUserDatabaseId());
            statement.setString(2, log.getActorExternalId());
            statement.setString(3, log.getAction());
            statement.setString(4, log.getResourceType());
            statement.setString(5, log.getResourceId());
            statement.setString(6, log.getDetails().toString());
            statement.setString(7, log.getIpAddress());
            statement.setString(8, log.getUserAgent());
            statement.setBoolean(9, log.isSuccess());
            statement.setString(10, log.getTraceId());
            statement.executeUpdate();
        }
    }

    public List<AuditLog> findPage(AuditLogFilter filter) throws SQLException {
        FilterSql filterSql = buildFilter(filter);
        String sql = """
                SELECT al.id, al.usuario_id, al.ator_id_externo, al.acao, al.tipo_recurso, al.recurso_id,
                       al.detalhes::text AS detalhes, host(al.endereco_ip) AS endereco_ip,
                       al.user_agent, al.sucesso, al.trace_id, al.criado_em,
                       u.nome AS actor_name, u.email AS actor_email
                FROM audit_logs al
                LEFT JOIN usuarios u ON u.id = al.usuario_id
                """ + filterSql.whereClause()
                + " ORDER BY criado_em DESC, id DESC LIMIT ? OFFSET ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = bind(statement, filterSql.parameters(), 1);
            statement.setInt(index++, filter.getPageSize());
            statement.setInt(index, filter.getOffset());

            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuditLog> logs = new ArrayList<>();
                while (resultSet.next()) {
                    logs.add(map(resultSet));
                }
                return logs;
            }
        }
    }

    public long count(AuditLogFilter filter) throws SQLException {
        FilterSql filterSql = buildFilter(filter);
        String sql = "SELECT COUNT(*) FROM audit_logs al" + filterSql.whereClause();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, filterSql.parameters(), 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    private static FilterSql buildFilter(AuditLogFilter filter) {
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        addFilter(clauses, parameters, "acao = ?", filter.getAction());
        addFilter(clauses, parameters, "ator_id_externo = ?", filter.getUserId());
        addFilter(clauses, parameters, "tipo_recurso = ?", filter.getResourceType());
        addFilter(clauses, parameters, "recurso_id = ?", filter.getResourceId());
        addFilter(clauses, parameters, "sucesso = ?", filter.getSuccess());
        addFilter(clauses, parameters, "criado_em >= ?", filter.getStartDate());
        addFilter(clauses, parameters, "criado_em <= ?", filter.getEndDate());
        return new FilterSql(
                clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses),
                parameters
        );
    }

    private static void addFilter(List<String> clauses, List<Object> parameters, String clause, Object value) {
        if (value != null) {
            clauses.add(clause);
            parameters.add(value);
        }
    }

    private static int bind(PreparedStatement statement, List<Object> parameters, int startIndex) throws SQLException {
        int index = startIndex;
        for (Object parameter : parameters) {
            if (parameter instanceof Instant instant) {
                statement.setTimestamp(index++, Timestamp.from(instant));
            } else if (parameter instanceof Boolean bool) {
                statement.setBoolean(index++, bool);
            } else {
                statement.setString(index++, parameter.toString());
            }
        }
        return index;
    }

    private static AuditLog map(ResultSet resultSet) throws SQLException {
        long databaseUserId = resultSet.getLong("usuario_id");
        Long nullableDatabaseUserId = resultSet.wasNull() ? null : databaseUserId;
        Timestamp createdAt = resultSet.getTimestamp("criado_em");
        String actorName = resultSet.getString("actor_name");
        String actorEmail = resultSet.getString("actor_email");
        try {
            JsonNode details = MAPPER.readTree(resultSet.getString("detalhes"));
            return new AuditLog(
                    resultSet.getLong("id"),
                    nullableDatabaseUserId,
                    resultSet.getString("ator_id_externo"),
                    resultSet.getString("acao"),
                    resultSet.getString("tipo_recurso"),
                    resultSet.getString("recurso_id"),
                    details,
                    resultSet.getString("endereco_ip"),
                    resultSet.getString("user_agent"),
                    resultSet.getBoolean("sucesso"),
                    resultSet.getString("trace_id"),
                    createdAt.toInstant(),
                    actorName,
                    actorEmail
            );
        } catch (Exception ex) {
            throw new SQLException("Invalid audit log data returned by database", ex);
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private record FilterSql(String whereClause, List<Object> parameters) {
    }
}
