package br.com.ziipsan.platform.accesscontrol;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Portas da ED-06. A loja é recortada pelo tenant da instância do serviço
 * (RLS continua só do tenant, como na P3.30): método nenhum recebe tenant de
 * outro — quem constrói o serviço fixa o tenant, e recurso de outro tenant
 * simplesmente não aparece (404 na camada de serviço).
 */
public final class AccessControlPorts {

    private AccessControlPorts() {
    }

    public interface AccessControlStore {
        // Perfis compostos
        List<CompositeProfile> listCompositeProfiles();

        Optional<CompositeProfile> findCompositeProfile(UUID profileId);

        void insertCompositeProfile(CompositeProfile profile);

        void assignCompositeProfile(UUID userId, UUID profileId, UUID assignedBy, Instant at);

        List<CompositeProfile> compositeProfilesOfUser(UUID userId);

        Set<String> factoryProfilesOfUser(UUID userId);

        // Restrição negativa
        Optional<EffectivePermissions.NegativeRestriction> restrictionOfUser(UUID userId);

        void upsertRestriction(EffectivePermissions.NegativeRestriction restriction);

        // Delegações
        List<EffectivePermissions.Delegation> delegationsOfDelegate(UUID delegateId);

        void insertDelegation(EffectivePermissions.Delegation delegation);

        void revokeDelegation(UUID delegationId, Instant at);

        // Política e aceites NIST
        TenantSecurityPolicy securityPolicy();

        List<NistAcceptance> nistAcceptances();

        void insertNistAcceptance(NistAcceptance acceptance);

        TenantSsoConfig ssoConfig();

        // Tentativas e sessões (LoginGuard)
        LoginGuard.AttemptState attemptStateOf(UUID userId);

        void saveAttemptState(UUID userId, LoginGuard.AttemptState state);

        Optional<LoginGuard.ActiveSession> activeSessionOf(UUID userId);

        /** Abre a sessão e ENCERRA qualquer outra do usuário (login único). */
        void openSession(LoginGuard.ActiveSession session);

        /** Remove o ESPELHO da sessão (revogação não pode deixá-la listada como ativa). */
        void removeSession(String sessionId);

        List<LoginGuard.ActiveSession> activeSessions();

        // Recertificação
        void insertRecertificationCampaign(RecertificationService.Campaign campaign);

        Optional<RecertificationService.Campaign> findCampaign(UUID campaignId);

        void insertRecertificationDecision(RecertificationService.Decision decision);

        List<RecertificationService.Decision> decisionsOfCampaign(UUID campaignId);
    }

    /** Trilha de auditoria do tenant (P1.7): leitura por usuário e integridade. */
    public interface AuditTrailPort {
        /** Eventos da trilha de UM usuário, em ordem cronológica (TR-128, SG-062). */
        List<AuditTrailService.AuditEventView> eventsOfUser(UUID userId, Instant from, Instant to);

        /** Verdadeiro se a prova de integridade (raiz diária/Merkle da P1.7) confere no período. */
        boolean integrityVerifies(UUID userId, Instant from, Instant to);
    }

    /** Sessões ativas no Keycloak (revogação e listagem para o ADMIN). */
    public interface SessionAdminPort {
        void revokeSession(String sessionId);
    }
}
