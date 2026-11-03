package br.com.ziipsan.platform.console;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.document.DocumentNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Console da plataforma v0 (P1.6, core V9): perfis de tenant (fonte única,
 * DAD-32), planos e cotas, flags, jornada de implantação, suspensão manual,
 * exportação e eliminação, tudo auditado em {@code platform.audit_event}.
 *
 * <p>Limites honestos desta classe:
 * <ul>
 *   <li>MFA: o filtro {@code MfaGateFilter} já recusou (403
 *       {@code mfa-required}) qualquer chamada de plataforma sem segundo
 *       fator no token antes de chegar aqui. O step-up é a confirmação
 *       explícita ({@code stepUpConfirmed}) da chamada sensível, registrada
 *       na auditoria; não existe protocolo de token de step-up separado.</li>
 *   <li>Backoffice só leitura: este serviço expõe o predicado
 *       ({@link #backofficeReadOnly}); o backoffice em si aplica o bloqueio
 *       de escrita quando o predicado é verdadeiro. Portal, Pix, retornos e
 *       2ª via não consultam o predicado e seguem funcionando — é por isso
 *       que a suspensão não os interrompe.</li>
 *   <li>Sem impersonação: acesso assistido é P3.17 (core V10_3) e não existe
 *       aqui, nem como atalho escondido.</li>
 *   <li>{@code plan} e {@code settings} de {@code public.tenant_catalog}
 *       continuam existindo (ver o cabeçalho da V9): só o provisionamento
 *       os escreve e nenhuma regra os lê — a remoção das colunas é
 *       pendência coordenada com a frente do provisionamento, porque ela
 *       toca o INSERT do provisionamento e todos os testes que o usam.</li>
 * </ul>
 * <p>Auditoria: toda mutação insere uma linha em {@code platform.audit_event}
 * na mesma transação. A tabela é append-only para a aplicação (GRANT SELECT
 * + INSERT da V9): UPDATE/DELETE falham no banco, e um teste de integração
 * prova isso com a role da aplicação.
 */
@Service
public class PlatformConsoleService {

    private static final Set<String> LEGAL_NATURES = Set.of("PRIVATE", "AUTARCHY", "PUBLIC_COMPANY", "SEM");
    private static final Set<String> TAX_REGIMES =
            Set.of("SIMPLES_NACIONAL", "LUCRO_PRESUMIDO", "LUCRO_REAL", "EXEMPT");

    /** Perfil do tenant, fonte única (DAD-32). CNPJ em CHAR(14) sem máscara. */
    public record TenantProfileInput(String legalName, String tradeName, String cnpj, String legalNature,
                                     String addressStreet, String addressNumber, String addressDistrict,
                                     String addressCity, String addressState, String addressPostalCode,
                                     String municipalityIbge, String stateRegistration, String taxRegime,
                                     String dpoName, String dpoPublicContact) {
    }

    public record TenantProfile(UUID tenantId, String legalName, String tradeName, String cnpj,
                                String legalNature, String addressCity, String addressState,
                                String municipalityIbge, String stateRegistration, String taxRegime,
                                String dpoName, String dpoPublicContact) {
    }

    public record PlanView(UUID id, String code, String name, String quotasJson, boolean active) {
    }

    public record SubscriptionView(UUID id, UUID tenantId, UUID planId, String planCode, String status,
                                   OffsetDateTime startedAt, OffsetDateTime endsAt) {
    }

    public record OnboardingStepView(String key, String title, String status,
                                     OffsetDateTime completedAt, UUID completedBy) {
    }

    public record OnboardingPanel(List<OnboardingStepView> steps, int done, int total, int percent) {
    }

    public record LifecycleView(UUID tenantId, String status, String suspensionReason,
                                OffsetDateTime suspensionNoticeAt, OffsetDateTime suspensionEffectiveAt,
                                String suspensionJustification, UUID suspendedBy,
                                OffsetDateTime deletionRequestedAt, UUID deletionRequestedBy,
                                String deletionJustification, OffsetDateTime deletionGraceUntil,
                                String deletionHoldReason, OffsetDateTime deletionHoldAt,
                                String deletionHoldReference, String deletionCertificateHash,
                                OffsetDateTime deletionCertificateIssuedAt) {
    }

    public record AuditView(UUID id, UUID tenantId, UUID actorId, String actorRole,
                            String action, String details, OffsetDateTime createdAt) {
    }

    public record ExportView(UUID id, UUID tenantId, OffsetDateTime requestedAt,
                             String contentHash, String content) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public PlatformConsoleService(DataSource dataSource) {
        this(new JdbcTemplate(dataSource), Clock.systemUTC());
    }

    PlatformConsoleService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Perfil do tenant (fonte única)
    // ------------------------------------------------------------------

    /** Cria ou atualiza o perfil. Não toca em {@code tenant_catalog}: o catálogo continua sendo só o cadastro de provisionamento. */
    @Transactional
    public TenantProfile upsertProfile(UUID tenantId, TenantProfileInput in, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        if (in == null || in.legalName() == null || in.legalName().isBlank()) {
            throw validation("A razão social é obrigatória.");
        }
        String cnpj = in.cnpj() == null ? "" : DocumentNumber.normalize(in.cnpj());
        if (!DocumentNumber.isValidCnpj(cnpj)) {
            throw validation("O CNPJ precisa ser válido (14 posições, com dígitos verificadores).");
        }
        if (in.legalNature() == null || !LEGAL_NATURES.contains(in.legalNature())) {
            throw validation("A natureza jurídica precisa ser um dos valores do domínio.");
        }
        if (in.municipalityIbge() != null && !in.municipalityIbge().matches("^[0-9]{7}$")) {
            throw validation("O código IBGE do município precisa ter 7 dígitos.");
        }
        if (in.taxRegime() != null && !TAX_REGIMES.contains(in.taxRegime())) {
            throw validation("O regime tributário precisa ser um dos valores do domínio.");
        }
        if (in.addressState() != null && !in.addressState().matches("^[A-Z]{2}$")) {
            throw validation("A UF precisa ter 2 letras maiúsculas.");
        }
        jdbc.update("""
                INSERT INTO platform.tenant_profile (tenant_id, legal_name, trade_name, cnpj, legal_nature,
                    address_street, address_number, address_district, address_city, address_state,
                    address_postal_code, municipality_ibge, state_registration, tax_regime,
                    dpo_name, dpo_public_contact, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (tenant_id) DO UPDATE SET
                    legal_name = EXCLUDED.legal_name, trade_name = EXCLUDED.trade_name,
                    cnpj = EXCLUDED.cnpj, legal_nature = EXCLUDED.legal_nature,
                    address_street = EXCLUDED.address_street, address_number = EXCLUDED.address_number,
                    address_district = EXCLUDED.address_district, address_city = EXCLUDED.address_city,
                    address_state = EXCLUDED.address_state, address_postal_code = EXCLUDED.address_postal_code,
                    municipality_ibge = EXCLUDED.municipality_ibge,
                    state_registration = EXCLUDED.state_registration, tax_regime = EXCLUDED.tax_regime,
                    dpo_name = EXCLUDED.dpo_name, dpo_public_contact = EXCLUDED.dpo_public_contact,
                    updated_at = NOW()
                """,
                tenantId, in.legalName(), in.tradeName(), cnpj, in.legalNature(),
                in.addressStreet(), in.addressNumber(), in.addressDistrict(), in.addressCity(),
                in.addressState(), in.addressPostalCode(), in.municipalityIbge(),
                in.stateRegistration(), in.taxRegime(), in.dpoName(), in.dpoPublicContact());
        audit(tenantId, actorId, actorRole, "PROFILE_UPSERTED", Map.of("legal_nature", in.legalNature()));
        return profile(tenantId);
    }

    public TenantProfile profile(UUID tenantId) {
        List<TenantProfile> rows = jdbc.query(
                "SELECT tenant_id, legal_name, trade_name, cnpj, legal_nature, address_city, address_state,"
                        + " municipality_ibge, state_registration, tax_regime, dpo_name, dpo_public_contact"
                        + " FROM platform.tenant_profile WHERE tenant_id = ?",
                (rs, i) -> new TenantProfile(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12)),
                tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O tenant não tem perfil no console.");
        }
        return rows.get(0);
    }

    public boolean profileExists(UUID tenantId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM platform.tenant_profile WHERE tenant_id = ?", Integer.class, tenantId);
        return n != null && n > 0;
    }

    // ------------------------------------------------------------------
    // Planos, cotas e flags
    // ------------------------------------------------------------------

    @Transactional
    public PlanView createPlan(String code, String name, String quotasJson, UUID actorId, String actorRole) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw validation("O código e o nome do plano são obrigatórios.");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.plan (id, code, name, quotas) VALUES (?, ?, ?, ?::jsonb)",
                id, code, name, quotasJson == null ? "{}" : quotasJson);
        audit(null, actorId, actorRole, "PLAN_CREATED", Map.of("plan_code", code));
        return new PlanView(id, code, name, quotasJson == null ? "{}" : quotasJson, true);
    }

    /**
     * Assina um plano. O índice parcial da V9 permite um único ACTIVE por
     * tenant, por isso a assinatura anterior é encerrada (CANCELLED) antes
     * da nova — trocar de plano nunca deixa dois ativos nem apaga o
     * histórico da assinatura anterior.
     */
    @Transactional
    public SubscriptionView subscribe(UUID tenantId, UUID planId, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        List<PlanView> plans = jdbc.query("SELECT id, code, name, quotas::text, active FROM platform.plan WHERE id = ?",
                (rs, i) -> new PlanView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5)), planId);
        if (plans.isEmpty()) {
            throw new ApiException.NotFound("O plano não existe.");
        }
        jdbc.update("UPDATE platform.tenant_subscription SET status = 'CANCELLED', ends_at = NOW()"
                + " WHERE tenant_id = ? AND status = 'ACTIVE'", tenantId);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO platform.tenant_subscription (id, tenant_id, plan_id, status)"
                + " VALUES (?, ?, ?, 'ACTIVE')", id, tenantId, planId);
        audit(tenantId, actorId, actorRole, "SUBSCRIPTION_CHANGED", Map.of("plan_code", plans.get(0).code()));
        return subscription(tenantId);
    }

    public SubscriptionView subscription(UUID tenantId) {
        List<SubscriptionView> rows = jdbc.query(
                "SELECT s.id, s.tenant_id, s.plan_id, p.code, s.status, s.started_at, s.ends_at"
                        + " FROM platform.tenant_subscription s JOIN platform.plan p ON p.id = s.plan_id"
                        + " WHERE s.tenant_id = ? AND s.status = 'ACTIVE'",
                (rs, i) -> new SubscriptionView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                        toOffset(rs.getTimestamp(6)), toOffset(rs.getTimestamp(7))),
                tenantId);
        if (rows.isEmpty()) {
            throw new ApiException.NotFound("O tenant não tem assinatura ativa.");
        }
        return rows.get(0);
    }

    @Transactional
    public void createFlag(String flagKey, String description, boolean defaultEnabled,
                           UUID actorId, String actorRole) {
        if (flagKey == null || flagKey.isBlank() || description == null || description.isBlank()) {
            throw validation("A chave e a descrição da flag são obrigatórias.");
        }
        jdbc.update("INSERT INTO platform.feature_flag (flag_key, description, default_enabled)"
                + " VALUES (?, ?, ?) ON CONFLICT (flag_key) DO UPDATE SET description = EXCLUDED.description,"
                + " default_enabled = EXCLUDED.default_enabled, updated_at = NOW()",
                flagKey, description, defaultEnabled);
        audit(null, actorId, actorRole, "FLAG_CREATED", Map.of("flag_key", flagKey));
    }

    @Transactional
    public void setTenantFlag(UUID tenantId, String flagKey, boolean enabled, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        Integer n = jdbc.queryForObject("SELECT count(*) FROM platform.feature_flag WHERE flag_key = ?",
                Integer.class, flagKey);
        if (n == null || n == 0) {
            throw new ApiException.NotFound("A flag não existe no catálogo de flags.");
        }
        jdbc.update("INSERT INTO platform.tenant_feature_flag (tenant_id, flag_key, enabled, set_by)"
                + " VALUES (?, ?, ?, ?) ON CONFLICT (tenant_id, flag_key) DO UPDATE SET"
                + " enabled = EXCLUDED.enabled, set_by = EXCLUDED.set_by, set_at = NOW()",
                tenantId, flagKey, enabled, actorId);
        audit(tenantId, actorId, actorRole, "TENANT_FLAG_SET",
                Map.of("flag_key", flagKey, "enabled", Boolean.toString(enabled)));
    }

    /** Flag efetiva do tenant: override por tenant, senão o padrão da flag, senão desligada. */
    public boolean tenantFlag(UUID tenantId, String flagKey) {
        List<Boolean> rows = jdbc.query(
                "SELECT enabled FROM platform.tenant_feature_flag WHERE tenant_id = ? AND flag_key = ?",
                (rs, i) -> rs.getBoolean(1), tenantId, flagKey);
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        List<Boolean> defaults = jdbc.query(
                "SELECT default_enabled FROM platform.feature_flag WHERE flag_key = ?",
                (rs, i) -> rs.getBoolean(1), flagKey);
        return !defaults.isEmpty() && defaults.get(0);
    }

    // ------------------------------------------------------------------
    // Jornada de implantação
    // ------------------------------------------------------------------

    /** Semeia o checklist do manifesto; passos já existentes não são recriados nem resetados. */
    @Transactional
    public int seedOnboarding(UUID tenantId, UUID actorId, String actorRole) {
        requireTenant(tenantId);
        int inserted = 0;
        for (OnboardingChecklist.Step step : OnboardingChecklist.STEPS) {
            inserted += jdbc.update("INSERT INTO platform.onboarding_step (tenant_id, step_key, title)"
                    + " VALUES (?, ?, ?) ON CONFLICT (tenant_id, step_key) DO NOTHING",
                    tenantId, step.key(), step.title());
        }
        audit(tenantId, actorId, actorRole, "ONBOARDING_SEEDED", Map.of("inserted", Integer.toString(inserted)));
        return inserted;
    }

    @Transactional
    public void completeStep(UUID tenantId, String stepKey, UUID actorId, String actorRole) {
        int updated = jdbc.update("UPDATE platform.onboarding_step SET status = 'DONE', completed_at = NOW(),"
                + " completed_by = ?, updated_at = NOW()"
                + " WHERE tenant_id = ? AND step_key = ? AND status = 'PENDING'",
                actorId, tenantId, stepKey);
        if (updated == 0) {
            Integer exists = jdbc.queryForObject(
                    "SELECT count(*) FROM platform.onboarding_step WHERE tenant_id = ? AND step_key = ?",
                    Integer.class, tenantId, stepKey);
            if (exists == null || exists == 0) {
                throw new ApiException.NotFound("O passo de implantação não existe para este tenant.");
            }
            throw conflict("O passo de implantação já está concluído.");
        }
        audit(tenantId, actorId, actorRole, "ONBOARDING_STEP_COMPLETED", Map.of("step_key", stepKey));
    }

    public OnboardingPanel onboardingPanel(UUID tenantId) {
        List<OnboardingStepView> steps = jdbc.query(
                "SELECT step_key, title, status, completed_at, completed_by FROM platform.onboarding_step"
                        + " WHERE tenant_id = ? ORDER BY created_at, step_key",
                (rs, i) -> new OnboardingStepView(rs.getString(1), rs.getString(2), rs.getString(3),
                        toOffset(rs.getTimestamp(4)), rs.getObject(5, UUID.class)),
                tenantId);
        int done = (int) steps.stream().filter(s -> "DONE".equals(s.status())).count();
        return new OnboardingPanel(steps, done, steps.size(),
                PlatformConsoleRules.onboardingPercent(done, steps.size()));
    }

    // ------------------------------------------------------------------
    // Suspensão manual (aviso prévio, MFA + step-up + justificativa)
    // ------------------------------------------------------------------

    /**
     * Aviso prévio de suspensão. A MFA já foi exigida pelo filtro antes da
     * rota; aqui exigem-se o step-up confirmado na chamada, a justificativa
     * e um início de vigência futuro — o aviso corre com o backoffice ainda
     * operando, e só {@link #activateSuspension} torna a suspensão vigente.
     */
    @Transactional
    public LifecycleView requestSuspensionNotice(UUID tenantId, String role, UUID actorId,
                                                 String reason, String justification,
                                                 boolean stepUpConfirmed, OffsetDateTime effectiveAt) {
        if (!PlatformConsoleRules.canSuspend(role, reason)) {
            throw new ApiException.Forbidden(
                    "Suspensão recusada: PLATFORM_FINANCE só suspende por inadimplência contratual.");
        }
        if (!stepUpConfirmed) {
            throw new ApiException.Forbidden("Suspensão recusada: confirme o step-up antes de suspender.");
        }
        if (justification == null || justification.isBlank()) {
            throw validation("A suspensão exige justificativa.");
        }
        if (effectiveAt == null || !effectiveAt.toInstant().isAfter(clock.instant())) {
            throw validation("A suspensão exige uma data de vigência futura (aviso prévio).");
        }
        LifecycleView current = lifecycleOrNull(tenantId);
        if (current != null && "DELETED".equals(current.status())) {
            throw conflict("Tenant eliminado não pode ser suspenso.");
        }
        jdbc.update("INSERT INTO platform.tenant_lifecycle (tenant_id, status, suspension_reason,"
                + " suspension_notice_at, suspension_effective_at, suspension_justification, suspended_by)"
                + " VALUES (?, 'SUSPENSION_NOTICE', ?, NOW(), ?, ?, ?)"
                + " ON CONFLICT (tenant_id) DO UPDATE SET status = 'SUSPENSION_NOTICE',"
                + " suspension_reason = EXCLUDED.suspension_reason,"
                + " suspension_notice_at = EXCLUDED.suspension_notice_at,"
                + " suspension_effective_at = EXCLUDED.suspension_effective_at,"
                + " suspension_justification = EXCLUDED.suspension_justification,"
                + " suspended_by = EXCLUDED.suspended_by, updated_at = NOW()",
                tenantId, reason, Timestamp.from(effectiveAt.toInstant()), justification, actorId);
        audit(tenantId, actorId, role, "SUSPENSION_NOTICE_REQUESTED",
                Map.of("reason", reason, "effective_at", effectiveAt.toString()));
        return lifecycle(tenantId);
    }

    /** Torna vigente uma suspensão cujo aviso prévio já venceu. Antes da vigência, recusa. */
    @Transactional
    public LifecycleView activateSuspension(UUID tenantId, String role, UUID actorId) {
        LifecycleView current = lifecycle(tenantId);
        if (!"SUSPENSION_NOTICE".equals(current.status())) {
            throw conflict("Não há aviso prévio de suspensão para ativar.");
        }
        if (current.suspensionEffectiveAt() == null
                || clock.instant().isBefore(current.suspensionEffectiveAt().toInstant())) {
            throw conflict("O aviso prévio ainda não venceu; a suspensão não pode vigorar.");
        }
        if (!PlatformConsoleRules.canSuspend(role, current.suspensionReason())) {
            throw new ApiException.Forbidden("Suspensão recusada para este papel e motivo.");
        }
        jdbc.update("UPDATE platform.tenant_lifecycle SET status = 'SUSPENDED', updated_at = NOW()"
                + " WHERE tenant_id = ?", tenantId);
        audit(tenantId, actorId, role, "SUSPENSION_ACTIVATED", Map.of());
        return lifecycle(tenantId);
    }

    /**
     * Predicado do backoffice: só leitura enquanto SUSPENDED. Portal, Pix,
     * retornos e 2ª via não consultam este predicado — por isso a suspensão
     * não os interrompe (plano v3.0, P1.6, com teste).
     */
    public boolean backofficeReadOnly(UUID tenantId) {
        LifecycleView current = lifecycleOrNull(tenantId);
        return current != null && "SUSPENDED".equals(current.status());
    }

    // ------------------------------------------------------------------
    // Exportação e eliminação (carência de 30 dias, certificado)
    // ------------------------------------------------------------------

    /** Exportação: só PLATFORM_OWNER. O conteúdo sai na resposta; no banco ficam só o pedido e o hash. */
    @Transactional
    public ExportView exportTenant(UUID tenantId, UUID actorId, String actorRole) {
        if (!PlatformConsoleRules.canDelete(actorRole)) {
            throw new ApiException.Forbidden("A exportação de dados do tenant só pode ser pedida por PLATFORM_OWNER.");
        }
        requireTenant(tenantId);
        String content = buildExportContent(tenantId);
        String hash = PlatformConsoleRules.sha256Hex(content);
        UUID id = UUID.randomUUID();
        OffsetDateTime requestedAt = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("INSERT INTO platform.tenant_export (id, tenant_id, requested_by, format, status, content_hash)"
                + " VALUES (?, ?, ?, 'JSON', 'READY', ?)", id, tenantId, actorId, hash);
        audit(tenantId, actorId, actorRole, "TENANT_EXPORTED", Map.of("content_hash", hash));
        return new ExportView(id, tenantId, requestedAt, hash, content);
    }

    /** Pedido de eliminação: só PLATFORM_OWNER, com justificativa; abre a carência de 30 dias. */
    @Transactional
    public LifecycleView requestDeletion(UUID tenantId, String role, UUID actorId, String justification) {
        if (!PlatformConsoleRules.canDelete(role)) {
            throw new ApiException.Forbidden("A eliminação de dados do tenant só pode ser pedida por PLATFORM_OWNER.");
        }
        if (justification == null || justification.isBlank()) {
            throw validation("A eliminação exige justificativa.");
        }
        LifecycleView current = lifecycleOrNull(tenantId);
        if (current != null && "DELETED".equals(current.status())) {
            throw conflict("Tenant já eliminado.");
        }
        Instant now = clock.instant();
        Instant graceUntil = PlatformConsoleRules.graceUntil(now);
        jdbc.update("INSERT INTO platform.tenant_lifecycle (tenant_id, status, deletion_requested_at,"
                + " deletion_requested_by, deletion_justification, deletion_grace_until,"
                + " deletion_hold_reason, deletion_hold_at, deletion_hold_reference)"
                + " VALUES (?, 'DELETION_GRACE', ?, ?, ?, ?, NULL, NULL, NULL)"
                + " ON CONFLICT (tenant_id) DO UPDATE SET status = 'DELETION_GRACE',"
                + " deletion_requested_at = EXCLUDED.deletion_requested_at,"
                + " deletion_requested_by = EXCLUDED.deletion_requested_by,"
                + " deletion_justification = EXCLUDED.deletion_justification,"
                + " deletion_grace_until = EXCLUDED.deletion_grace_until,"
                + " deletion_hold_reason = NULL, deletion_hold_at = NULL, deletion_hold_reference = NULL,"
                + " updated_at = NOW()",
                tenantId, Timestamp.from(now), actorId, justification, Timestamp.from(graceUntil));
        audit(tenantId, actorId, role, "DELETION_REQUESTED",
                Map.of("grace_until", graceUntil.toString()));
        return lifecycle(tenantId);
    }

    /** Contestação ou ordem judicial durante a carência: suspende a eliminação (fail-safe: OWNER ou OPS). */
    @Transactional
    public LifecycleView registerDeletionHold(UUID tenantId, String role, UUID actorId,
                                              String holdReason, String reference) {
        if (!PlatformConsoleRules.canHoldDeletion(role)) {
            throw new ApiException.Forbidden("O registro de hold de eliminação exige PLATFORM_OWNER ou PLATFORM_OPS.");
        }
        if (!"CONTESTATION".equals(holdReason) && !"COURT_ORDER".equals(holdReason)) {
            throw validation("O hold de eliminação precisa ser CONTESTATION ou COURT_ORDER.");
        }
        LifecycleView current = lifecycle(tenantId);
        if (!"DELETION_GRACE".equals(current.status())) {
            throw conflict("Só há hold durante a carência de eliminação.");
        }
        jdbc.update("UPDATE platform.tenant_lifecycle SET status = 'DELETION_HELD',"
                + " deletion_hold_reason = ?, deletion_hold_at = NOW(), deletion_hold_reference = ?,"
                + " updated_at = NOW() WHERE tenant_id = ?",
                holdReason, reference, tenantId);
        audit(tenantId, actorId, role, "DELETION_HELD",
                Map.of("hold_reason", holdReason));
        return lifecycle(tenantId);
    }

    /** Libera o hold: a carência retoma de onde parou (nunca zerada, nunca estendida). */
    @Transactional
    public LifecycleView releaseDeletionHold(UUID tenantId, String role, UUID actorId) {
        if (!PlatformConsoleRules.canHoldDeletion(role)) {
            throw new ApiException.Forbidden("A liberação do hold de eliminação exige PLATFORM_OWNER ou PLATFORM_OPS.");
        }
        LifecycleView current = lifecycle(tenantId);
        if (!"DELETION_HELD".equals(current.status())) {
            throw conflict("Não há hold de eliminação para liberar.");
        }
        Instant resumed = PlatformConsoleRules.graceUntilAfterHoldRelease(
                current.deletionGraceUntil().toInstant(), current.deletionHoldAt().toInstant(), clock.instant());
        jdbc.update("UPDATE platform.tenant_lifecycle SET status = 'DELETION_GRACE',"
                + " deletion_grace_until = ?, deletion_hold_reason = NULL, deletion_hold_at = NULL,"
                + " deletion_hold_reference = NULL, updated_at = NOW() WHERE tenant_id = ?",
                Timestamp.from(resumed), tenantId);
        audit(tenantId, actorId, role, "DELETION_HOLD_RELEASED",
                Map.of("grace_until", resumed.toString()));
        return lifecycle(tenantId);
    }

    /** Completa a eliminação: só OWNER, só em DELETION_GRACE, só com a carência vencida. Emite o certificado (hash). */
    @Transactional
    public LifecycleView completeDeletion(UUID tenantId, String role, UUID actorId) {
        if (!PlatformConsoleRules.canDelete(role)) {
            throw new ApiException.Forbidden("A eliminação de dados do tenant só pode ser concluída por PLATFORM_OWNER.");
        }
        LifecycleView current = lifecycle(tenantId);
        if (!PlatformConsoleRules.deletionMayComplete(current.status(), clock.instant(),
                current.deletionGraceUntil() == null ? null : current.deletionGraceUntil().toInstant())) {
            throw conflict(
                    "A eliminação só se completa em carência vencida, sem hold vigente.");
        }
        String certificate = PlatformConsoleRules.deletionCertificateHash(
                tenantId.toString(), current.deletionRequestedAt().toInstant());
        jdbc.update("UPDATE platform.tenant_lifecycle SET status = 'DELETED',"
                + " deletion_certificate_hash = ?, deletion_certificate_issued_at = NOW(), updated_at = NOW()"
                + " WHERE tenant_id = ?", certificate, tenantId);
        audit(tenantId, actorId, role, "DELETION_COMPLETED",
                Map.of("certificate_hash", certificate));
        return lifecycle(tenantId);
    }

    public LifecycleView lifecycle(UUID tenantId) {
        LifecycleView row = lifecycleOrNull(tenantId);
        if (row == null) {
            throw new ApiException.NotFound("O tenant não tem ciclo de vida no console.");
        }
        return row;
    }

    public LifecycleView lifecycleOrNull(UUID tenantId) {
        List<LifecycleView> rows = jdbc.query(
                "SELECT tenant_id, status, suspension_reason, suspension_notice_at, suspension_effective_at,"
                        + " suspension_justification, suspended_by, deletion_requested_at, deletion_requested_by,"
                        + " deletion_justification, deletion_grace_until, deletion_hold_reason, deletion_hold_at,"
                        + " deletion_hold_reference, deletion_certificate_hash, deletion_certificate_issued_at"
                        + " FROM platform.tenant_lifecycle WHERE tenant_id = ?",
                (rs, i) -> new LifecycleView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        toOffset(rs.getTimestamp(4)), toOffset(rs.getTimestamp(5)), rs.getString(6),
                        rs.getObject(7, UUID.class), toOffset(rs.getTimestamp(8)), rs.getObject(9, UUID.class),
                        rs.getString(10), toOffset(rs.getTimestamp(11)), rs.getString(12),
                        toOffset(rs.getTimestamp(13)), rs.getString(14), rs.getString(15),
                        toOffset(rs.getTimestamp(16))),
                tenantId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<AuditView> auditEvents(UUID tenantId, int limit) {
        return jdbc.query(
                "SELECT id, tenant_id, actor_id, actor_role, action, details::text, created_at"
                        + " FROM platform.audit_event WHERE tenant_id = ? ORDER BY created_at DESC, id DESC LIMIT ?",
                (rs, i) -> new AuditView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6),
                        toOffset(rs.getTimestamp(7))),
                tenantId, Math.max(1, limit));
    }

    // ------------------------------------------------------------------
    // Apoio
    // ------------------------------------------------------------------

    private void requireTenant(UUID tenantId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM public.tenant_catalog WHERE id = ?",
                Integer.class, tenantId);
        if (n == null || n == 0) {
            throw new ApiException.NotFound("O tenant não existe no catálogo.");
        }
    }

    private void audit(UUID tenantId, UUID actorId, String actorRole, String action, Map<String, String> details) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : details.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(esc(e.getKey())).append("\":\"").append(esc(e.getValue())).append("\"");
        }
        json.append("}");
        jdbc.update("INSERT INTO platform.audit_event (tenant_id, actor_id, actor_role, action, details)"
                + " VALUES (?, ?, ?, ?, ?::jsonb)", tenantId, actorId, actorRole, action, json.toString());
    }

    /** Conteúdo da exportação: perfil, assinatura, flags do tenant, ciclo de vida e checklist. Sem dado de outro tenant. */
    private String buildExportContent(UUID tenantId) {
        List<String> parts = new ArrayList<>();
        parts.add("\"tenant_id\":\"" + tenantId + "\"");
        try {
            TenantProfile p = profile(tenantId);
            parts.add("\"profile\":{\"legal_name\":\"" + esc(p.legalName()) + "\",\"trade_name\":\""
                    + esc(p.tradeName()) + "\",\"cnpj\":\"" + esc(p.cnpj()) + "\",\"legal_nature\":\""
                    + esc(p.legalNature()) + "\",\"municipality_ibge\":\"" + esc(p.municipalityIbge())
                    + "\",\"dpo_name\":\"" + esc(p.dpoName()) + "\",\"dpo_public_contact\":\""
                    + esc(p.dpoPublicContact()) + "\"}");
        } catch (ApiException.NotFound e) {
            parts.add("\"profile\":null");
        }
        try {
            SubscriptionView s = subscription(tenantId);
            parts.add("\"subscription\":{\"plan_code\":\"" + esc(s.planCode()) + "\",\"status\":\""
                    + esc(s.status()) + "\"}");
        } catch (ApiException.NotFound e) {
            parts.add("\"subscription\":null");
        }
        List<String> flags = jdbc.query(
                "SELECT flag_key || '=' || enabled::text FROM platform.tenant_feature_flag WHERE tenant_id = ?",
                (rs, i) -> rs.getString(1), tenantId);
        parts.add("\"tenant_flags\":" + jsonStringArray(flags));
        LifecycleView life = lifecycleOrNull(tenantId);
        parts.add("\"lifecycle_status\":" + (life == null ? "null" : "\"" + life.status() + "\""));
        OnboardingPanel panel = onboardingPanel(tenantId);
        parts.add("\"onboarding\":{\"done\":" + panel.done() + ",\"total\":" + panel.total()
                + ",\"percent\":" + panel.percent() + "}");
        return "{" + String.join(",", parts) + "}";
    }

    private static String jsonStringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(esc(values.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static ApiException.Validation validation(String message) {
        return new ApiException.Validation(message, List.of(), List.of());
    }

    private static ApiException.Conflict conflict(String message) {
        return new ApiException.Conflict("state-conflict", message);
    }
}
