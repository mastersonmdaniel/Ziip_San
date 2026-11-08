package br.com.ziipsan.platform.accesscontrol;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Regras PURAS da política de senha por tenant (ED-06, ADR-0040, alinhada ao
 * NIST SP 800-63B-4). Sem banco e sem relógio da máquina: hashes do histórico,
 * lista de senhas vazadas e aceites chegam por parâmetro/porta de quem chama
 * (o autenticador do Keycloak e o serviço de troca de senha).
 *
 * <p>O que o NIST manda e esta regra aplica:
 * <ul>
 *   <li>comparação com lista de senhas VAZADAS — senha presente na lista é
 *       recusada ({@code password-breached}); a lista em si é dado externo
 *       (porta {@link BreachedPasswordPort}), nenhum verbete é inventado aqui;</li>
 *   <li>expiração periódica DESLIGADA por padrão; quando o tenant a liga
 *       (exigência de edital), ela só vale com aceite registrado vigente —
 *       sem aceite, {@link #effectivePolicy} a remove;</li>
 *   <li>regra de COMPOSIÇÃO idem: só com aceite; sem aceite, maiúscula/dígito/
 *       símbolo NÃO são exigidos;</li>
 *   <li>mínimo de 15 caracteres para fator único (portal sem MFA) — ver
 *       {@link TenantSecurityPolicy#effectiveMinLength(boolean)};</li>
 *   <li>histórico: a nova senha não pode repetir nenhuma das últimas N
 *       (comparação por HASH, via porta — senha em claro nunca é persistida
 *       nem comparada fora do verificador do Keycloak).</li>
 * </ul>
 * A mensagem de recusa NÃO diz qual regra falhou além do necessário ao
 * usuário que está trocando a PRÓPRIA senha; no LOGIN, a resposta é sempre
 * indistinta (ver {@link LoginGuard}).
 */
public final class PasswordPolicyRule {

    /** Porta da lista de senhas vazadas (fonte externa ao produto). */
    @FunctionalInterface
    public interface BreachedPasswordPort {
        boolean isBreached(String candidate);
    }

    /** Porta de comparação com o histórico, por hash (o verificador detém o segredo). */
    @FunctionalInterface
    public interface PasswordHistoryPort {
        boolean matchesAnyPrevious(String candidate, int historyCount);
    }

    /** Resultado da validação: válida, ou o código estável da recusa. */
    public record Verdict(boolean valid, String code, String message) {
        static Verdict ok() {
            return new Verdict(true, "ok", "Senha aceita pela política do tenant.");
        }

        static Verdict reject(String code, String message) {
            return new Verdict(false, code, message);
        }
    }

    private PasswordPolicyRule() {
    }

    /**
     * Política EFETIVA do tenant {@code tenantId} (correção D7): aceites de
     * OUTRO tenant são ignorados aqui dentro — a regra pura não depende mais
     * de a lista chegar recortada pela loja.
     */
    public static TenantSecurityPolicy effectivePolicy(TenantSecurityPolicy configured, UUID tenantId,
                                                       List<NistAcceptance> acceptances, Instant now) {
        Objects.requireNonNull(tenantId, "tenant é obrigatório");
        List<NistAcceptance> own = acceptances == null ? List.of()
                : acceptances.stream().filter(a -> tenantId.equals(a.tenantId())).toList();
        return effectivePolicy(configured, own, now);
    }

    /**
     * Política EFETIVA: opções contrárias ao NIST sem aceite vigente do ADMIN
     * do tenant são REMOVIDAS (expiração desligada, composição desligada) —
     * a configuração continua gravada, mas inerte, até o aceite existir.
     * CONTRATO: {@code acceptances} precisa vir RECORTADO pelo tenant da
     * política (a loja JDBC recorta por {@code tenant_id}); para lista mista,
     * use a sobrecarga com {@code tenantId}, que filtra aqui dentro.
     */
    public static TenantSecurityPolicy effectivePolicy(TenantSecurityPolicy configured,
                                                       List<NistAcceptance> acceptances, Instant now) {
        Objects.requireNonNull(configured, "política é obrigatória");
        Objects.requireNonNull(now, "instante é obrigatório");
        List<NistAcceptance> active = acceptances == null ? List.of() : acceptances;
        boolean expiryAccepted = active.stream().anyMatch(a ->
                NistAcceptance.OPTION_PERIODIC_EXPIRY.equals(a.optionKey()) && a.activeAt(now));
        boolean compositionAccepted = active.stream().anyMatch(a ->
                NistAcceptance.OPTION_COMPOSITION.equals(a.optionKey()) && a.activeAt(now));
        return new TenantSecurityPolicy(
                configured.passwordMinLength(),
                expiryAccepted ? configured.passwordMaxAgeDays() : null,
                configured.passwordHistory(),
                configured.lockoutAttempts(),
                configured.userInactivityDays(),
                compositionAccepted && configured.compositionRequired(),
                configured.passkeyEnabled());
    }

    /**
     * Valida uma NOVA senha contra a política EFETIVA do tenant.
     *
     * <p>PENDÊNCIA DE FIAÇÃO NOMEADA (correção D4): nesta entrega este
     * método só é chamado pelos testes. O fluxo de TROCA de senha no
     * Keycloak é outra SPI (RequiredAction/provider próprio), distinta do
     * autenticador de login — ligar esta regra a ele é trabalho do adaptador
     * SPI (pendência 1 do relatório, desdobrada). Até lá, tamanho/histórico/
     * vazadas NÃO estão aplicados em produção; só bloqueio, expiração e
     * inatividade (no login) estão.
     */
    public static Verdict validateNewPassword(String candidate, TenantSecurityPolicy effective,
                                              boolean mfaEnrolled, BreachedPasswordPort breached,
                                              PasswordHistoryPort history) {
        Objects.requireNonNull(effective, "política efetiva é obrigatória");
        Objects.requireNonNull(breached, "porta de senhas vazadas é obrigatória");
        Objects.requireNonNull(history, "porta de histórico é obrigatória");
        if (candidate == null || candidate.length() < effective.effectiveMinLength(mfaEnrolled)) {
            return Verdict.reject("password-too-short",
                    "A senha precisa ter ao menos " + effective.effectiveMinLength(mfaEnrolled) + " caracteres.");
        }
        if (breached.isBreached(candidate)) {
            return Verdict.reject("password-breached",
                    "Esta senha aparece em listas de senhas vazadas; escolha outra.");
        }
        if (effective.passwordHistory() > 0 && history.matchesAnyPrevious(candidate, effective.passwordHistory())) {
            return Verdict.reject("password-in-history",
                    "A senha não pode repetir nenhuma das últimas " + effective.passwordHistory() + " senhas.");
        }
        if (effective.compositionRequired() && !hasComposition(candidate)) {
            return Verdict.reject("password-composition",
                    "A política deste tenant exige maiúscula, minúscula, dígito e símbolo.");
        }
        return Verdict.ok();
    }

    private static boolean hasComposition(String candidate) {
        boolean upper = false, lower = false, digit = false, symbol = false;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else symbol = true;
        }
        return upper && lower && digit && symbol;
    }

    /** Senha EXPIRADA na política efetiva? Sem expiração configurada (padrão), nunca. */
    public static boolean isExpired(TenantSecurityPolicy effective, Instant passwordChangedAt, Instant now) {
        Objects.requireNonNull(effective, "política efetiva é obrigatória");
        if (!effective.periodicExpiryEnabled() || passwordChangedAt == null) {
            return false;
        }
        return Duration.between(passwordChangedAt, now).toDays() >= effective.passwordMaxAgeDays();
    }

    /** Conta INATIVA além do limite do tenant? Sem limite configurado, nunca. */
    public static boolean isInactiveBeyondLimit(TenantSecurityPolicy effective, Instant lastLoginAt, Instant now) {
        Objects.requireNonNull(effective, "política efetiva é obrigatória");
        if (effective.userInactivityDays() == null || lastLoginAt == null) {
            return false;
        }
        return Duration.between(lastLoginAt, now).toDays() >= effective.userInactivityDays();
    }

    /** Chaves de parâmetro desta política (para o catálogo e para o console). */
    public static final Set<String> PARAMETER_KEYS = Set.of(
            "sec.password_min_length", "sec.password_max_age_days", "sec.password_history",
            "sec.lockout_attempts", "sec.user_inactivity_days", "sec.password_composition_required",
            "sec.sso_mode", "sec.sso_enforced", "sec.scim_enabled", "portal.passkey_enabled");
}
