package br.com.tabula.service;

import br.com.tabula.model.AuditAction;
import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.ProfileRepository;
import br.com.tabula.repository.ProfileRepository.ProfileData;
import com.zaxxer.hikari.HikariDataSource;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProfileService {
    private final HikariDataSource dataSource; private final ProfileRepository repository; private final AuditLogService audit;
    public ProfileService(HikariDataSource dataSource, ProfileRepository repository, AuditLogService audit) {
        this.dataSource=dataSource; this.repository=repository; this.audit=audit;
    }

    public ProfileData get(AuthenticatedPrincipal actor) throws SQLException, ProfileException {
        try (Connection connection=dataSource.getConnection()) {
            return repository.findByUserId(connection, actor.getDatabaseId(), false)
                    .orElseThrow(() -> ProfileException.notFound("profile_not_found"));
        }
    }

    public ProfileData update(AuthenticatedPrincipal actor, ProfileInput input, RequestMetadata metadata)
            throws SQLException, ProfileException {
        ProfileInput clean=validate(input);
        try (Connection connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ProfileData before=repository.findByUserId(connection, actor.getDatabaseId(), true)
                        .orElseThrow(() -> ProfileException.notFound("profile_not_found"));
                List<String> changed=new ArrayList<>();
                if (!before.name().equals(clean.name())) changed.add("name");
                if (!java.util.Objects.equals(before.course(), clean.course())) changed.add("course");
                if (!java.util.Objects.equals(before.bio(), clean.bio())) changed.add("bio");
                if (!java.util.Objects.equals(before.avatarUrl(), clean.avatarUrl())) changed.add("avatarUrl");
                ProfileData result=repository.update(connection, actor.getDatabaseId(), clean.name(), clean.course(), clean.bio(), clean.avatarUrl());
                audit.record(connection, actor, AuditAction.PROFILE_UPDATED, "PROFILE", actor.getExternalId(), true,
                        metadata.ipAddress(), metadata.userAgent(), Map.of("changedFields", changed));
                connection.commit(); return result;
            } catch (Exception ex) {
                connection.rollback();
                if (ex instanceof ProfileException pe) throw pe;
                if (ex instanceof SQLException se) throw se;
                throw new SQLException("Profile transaction failed", ex);
            }
        }
    }

    public void auditRejected(AuthenticatedPrincipal actor, String reason, RequestMetadata metadata) {
        audit.recordBestEffort(actor, AuditAction.PROFILE_OPERATION_REJECTED, "PROFILE", actor.getExternalId(), false,
                metadata.ipAddress(), metadata.userAgent(), Map.of("reasonCode", reason));
    }

    private static ProfileInput validate(ProfileInput input) throws ProfileException {
        if (input==null || input.name()==null || input.name().trim().isEmpty() || input.name().trim().length()>150)
            throw ProfileException.invalid("invalid_name");
        String course=clean(input.course()), bio=clean(input.bio()), avatar=clean(input.avatarUrl());
        if (course!=null && course.length()>180) throw ProfileException.invalid("invalid_course");
        if (bio!=null && bio.length()>2000) throw ProfileException.invalid("invalid_bio");
        if (avatar!=null && (avatar.length()>1_000_000 || !validAvatar(avatar)))
            throw ProfileException.invalid("invalid_avatar_url");
        return new ProfileInput(input.name().trim(), course, bio, avatar);
    }

    private static boolean validAvatar(String value) {
        if (value.startsWith("data:image/")) return true;
        try { URI uri=URI.create(value); return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()); }
        catch (IllegalArgumentException ex) { return false; }
    }
    private static String clean(String value) { return value==null || value.trim().isEmpty() ? null : value.trim(); }
    public record ProfileInput(String name,String course,String bio,String avatarUrl) {}
    public record RequestMetadata(String ipAddress,String userAgent) {}
    public static final class ProfileException extends Exception {
        public enum Kind { NOT_FOUND, INVALID } private final Kind kind; private final String reason;
        private ProfileException(Kind kind,String reason){this.kind=kind;this.reason=reason;}
        public Kind kind(){return kind;} public String reason(){return reason;}
        public static ProfileException notFound(String reason){return new ProfileException(Kind.NOT_FOUND,reason);}
        public static ProfileException invalid(String reason){return new ProfileException(Kind.INVALID,reason);}
    }
}
