package br.com.ziipsan.core.restore;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Portao puro da restauracao seletiva de um tenant (P3.33; PLT-03).
 * Sem banco e sem relogio da maquina: {@code now} vem de quem chama
 * (nos testes, de um {@code Clock} fixo), como em {@code AccessPolicy}
 * (P3.17) e {@code TenantExitCalculator} (P3.16).
 *
 * <p>O aceite exige, cumulativamente:
 * <ul>
 *   <li>execucao SO por {@code PLATFORM_OWNER} (papeis da P0.43) com
 *       step-up: a reautenticacao com MFA precisa ser DO MOMENTO da
 *       execucao ({@code mfaStepUpAt == now}) — um MFA de uma requisicao
 *       anterior nao executa restauracao, mesma leitura do acesso de
 *       emergencia da P3.17;</li>
 *   <li>aprovacao do tenant nos moldes do consentimento do ADR-0020
 *       ({@link RestoreConsent}): presente, deste tenant, vigente, nao
 *       revogada, com o instante-alvo e o modo (substituicao/mesclagem)
 *       iguais aos da execucao pedida;</li>
 *   <li>aplicacao EM JANELA: a substituicao/mesclagem so ocorre dentro
 *       da janela declarada para a execucao ({@code windowStart}/
 *       {@code windowEnd}, combinada com o tenant na aprovacao — tipicamente
 *       a janela de manutencao do RNF-12). A janela e DADO da execucao,
 *       nao constante desta classe: nenhum horario e inventado aqui.</li>
 * </ul>
 *
 * <p>A {@link Decision} negada carrega o codigo estavel do motivo, que e
 * o mesmo codigo gravado na trilha ({@link RestoreAudit}): regra e
 * auditoria nunca divergem no motivo.
 */
public final class TenantRestorePolicy {

    /** Papeis de plataforma da P0.43. */
    public static final Set<String> PLATFORM_ROLES = Set.of(
            "PLATFORM_OWNER", "PLATFORM_OPS", "PLATFORM_SUPPORT", "PLATFORM_FINANCE");

    /** Papel unico que executa a restauracao (aceite da P3.33). */
    public static final String ROLE_PLATFORM_OWNER = "PLATFORM_OWNER";

    /** Decisao do portao: permitida, ou negada com o codigo estavel do motivo. */
    public record Decision(boolean allowed, String code, String message) {

        static Decision allow() {
            return new Decision(true, "allowed", "Restauracao permitida.");
        }

        static Decision deny(String code, String message) {
            return new Decision(false, code, message);
        }
    }

    private TenantRestorePolicy() {
    }

    /**
     * Avalia a APLICACAO de uma restauracao (a etapa que escreve sobre o
     * estado atual). A extracao e a comparacao NAO passam por este
     * portao: sao somente leitura e podem ocorrer fora da janela — a
     * janela restringe a aplicacao, como diz o RB-07 §2. {@code role}
     * fora do dominio da P0.43 e dado invalido e o metodo lanca
     * {@link IllegalArgumentException} — papel desconhecido nunca vira
     * "algum papel" por omissao.
     */
    public static Decision evaluateExecution(String role, Instant mfaStepUpAt,
                                             RestoreConsent consent, UUID tenantId,
                                             RestoreConsent.RestoreMode requestedMode,
                                             Instant requestedTarget,
                                             Instant windowStart, Instant windowEnd,
                                             Instant now) {
        if (role == null || !PLATFORM_ROLES.contains(role)) {
            throw new IllegalArgumentException(
                    "role fora do dominio da P0.43 (PLATFORM_OWNER, PLATFORM_OPS, "
                            + "PLATFORM_SUPPORT, PLATFORM_FINANCE): " + role);
        }
        Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
        Objects.requireNonNull(requestedMode, "requestedMode e obrigatorio");
        Objects.requireNonNull(requestedTarget, "requestedTarget e obrigatorio");
        Objects.requireNonNull(windowStart, "windowStart e obrigatorio");
        Objects.requireNonNull(windowEnd, "windowEnd e obrigatorio");
        Objects.requireNonNull(now, "now e obrigatorio");
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("windowEnd precisa ser posterior a windowStart");
        }
        if (!ROLE_PLATFORM_OWNER.equals(role)) {
            return Decision.deny("platform-owner-required",
                    "Restauracao seletiva so e executada por PLATFORM_OWNER.");
        }
        if (mfaStepUpAt == null) {
            return Decision.deny("mfa-required",
                    "Restauracao seletiva exige step-up (reautenticacao com MFA).");
        }
        if (!mfaStepUpAt.equals(now)) {
            return Decision.deny("mfa-not-current",
                    "O step-up precisa ser do momento da execucao, nao de uma requisicao anterior.");
        }
        Decision consentDecision = evaluateConsent(consent, tenantId, requestedMode, requestedTarget, now);
        if (!consentDecision.allowed()) {
            return consentDecision;
        }
        if (now.isBefore(windowStart) || !now.isBefore(windowEnd)) {
            return Decision.deny("outside-window",
                    "A aplicacao da restauracao so ocorre dentro da janela declarada com o tenant.");
        }
        return Decision.allow();
    }

    private static Decision evaluateConsent(RestoreConsent consent, UUID tenantId,
                                            RestoreConsent.RestoreMode requestedMode,
                                            Instant requestedTarget, Instant now) {
        if (consent == null) {
            return Decision.deny("consent-required",
                    "Sem aprovacao do tenant (moldes do ADR-0020), nao ha restauracao.");
        }
        if (!consent.tenantId().equals(tenantId)) {
            return Decision.deny("consent-tenant-mismatch",
                    "A aprovacao e de outro tenant.");
        }
        if (consent.isRevokedAt(now)) {
            return Decision.deny("consent-revoked",
                    "A aprovacao do tenant foi revogada.");
        }
        if (now.isBefore(consent.grantedAt())) {
            return Decision.deny("consent-not-yet-valid",
                    "A aprovacao do tenant ainda nao entrou em vigencia.");
        }
        if (!now.isBefore(consent.expiresAt())) {
            return Decision.deny("consent-expired",
                    "A aprovacao do tenant expirou.");
        }
        if (!consent.targetInstant().equals(requestedTarget)) {
            return Decision.deny("consent-target-mismatch",
                    "O instante-alvo pedido nao e o instante-alvo consentido pelo tenant.");
        }
        if (consent.mode() != requestedMode) {
            return Decision.deny("consent-mode-mismatch",
                    "O modo pedido (substituicao/mesclagem) nao e o modo consentido pelo tenant.");
        }
        return Decision.allow();
    }
}
