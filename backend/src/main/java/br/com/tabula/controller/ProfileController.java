package br.com.tabula.controller;

import br.com.tabula.model.AuthenticatedPrincipal;
import br.com.tabula.repository.ProfileRepository;
import br.com.tabula.repository.ProfileRepository.ProfileData;
import br.com.tabula.service.AuditLogService;
import br.com.tabula.service.AuthenticatedUserService;
import br.com.tabula.service.ProfileService;
import br.com.tabula.service.ProfileService.ProfileException;
import br.com.tabula.service.ProfileService.ProfileInput;
import br.com.tabula.service.ProfileService.RequestMetadata;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public final class ProfileController {
    private ProfileController() {}
    public static void register(Javalin app, HikariDataSource dataSource) {
        AuthenticatedUserService auth=new AuthenticatedUserService(dataSource);
        ProfileService service=new ProfileService(dataSource,new ProfileRepository(),new AuditLogService(dataSource));
        app.get("/profile",ctx->authenticated(ctx,auth,service,principal->ctx.json(ProfileResponse.from(service.get(principal)))));
        app.put("/profile",ctx->authenticated(ctx,auth,service,principal->{
            ProfileRequest request;
            try { request=ctx.bodyAsClass(ProfileRequest.class); }
            catch (Exception ex) { throw ProfileException.invalid("invalid_payload"); }
            ctx.json(ProfileResponse.from(service.update(principal,request.toInput(),metadata(ctx))));
        }));
    }
    private static void authenticated(Context ctx,AuthenticatedUserService auth,ProfileService service,Handler handler)throws Exception{
        Optional<AuthenticatedPrincipal> principal=auth.resolve(ctx.header("Authorization"));
        if(principal.isEmpty()){ctx.status(401).json(Map.of("error","Sessão inválida ou expirada."));return;}
        try{handler.handle(principal.get());}
        catch(ProfileException ex){
            service.auditRejected(principal.get(),ex.reason(),metadata(ctx));
            ctx.status(ex.kind()==ProfileException.Kind.NOT_FOUND?404:422)
                    .json(Map.of("error",ex.kind()==ProfileException.Kind.NOT_FOUND?"Perfil não encontrado.":"Dados de perfil inválidos."));
        }
    }
    private static RequestMetadata metadata(Context ctx){return new RequestMetadata(ctx.ip(),ctx.userAgent());}
    private record ProfileRequest(String name,String course,String bio,String avatarUrl){
        ProfileInput toInput(){return new ProfileInput(name,course,bio,avatarUrl);}
    }
    private record ProfileResponse(String id,String name,String course,String bio,String avatarUrl,String joinedAt){
        static ProfileResponse from(ProfileData value){return new ProfileResponse(value.externalId(),value.name(),
                value.course()==null?"":value.course(),value.bio()==null?"":value.bio(),value.avatarUrl(),
                value.joinedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));}
    }
    @FunctionalInterface private interface Handler{void handle(AuthenticatedPrincipal principal)throws Exception;}
}
