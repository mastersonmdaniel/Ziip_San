package br.com.ziipsan.platform.portal;

import java.util.Set;

/**
 * Defesa contra automacao do portal (PLT-22, P3.3), compativel com a
 * WCAG 2.2, criterio 3.3.8 (autenticacao acessivel): o desafio e NAO
 * cognitivo (prova de trabalho ou desafio invisivel) — quebra-cabeca de
 * imagem e recusado por construcao ({@link ChallengeKind} nao tem valor
 * cognitivo). Gerenciador de senha e colar sao sempre permitidos: nenhuma
 * regra daqui autoriza bloquea-los.
 *
 * <p>Regras: deteccao de preenchimento de credenciais (um IP com muitos
 * documentos; um documento com muitos IPs), senha conferida contra lista
 * de vazadas no cadastro (porta {@link BreachedPasswordPort}), limite de
 * criacao de conta por e-mail (de {@code portal.account_throttle}) e modo
 * por {@code portal.bot_defense} (classe d). A telemetria vai para a aba
 * Seguranca da Central pelo sinal {@link StuffingSignals#telemetryEvent()}.
 * A regra gerenciada de bot NA BORDA e da LC.2 (pendencia declarada).
 */
public final class BotDefensePolicy {

    public enum ChallengeKind { NONE, PROOF_OF_WORK, INVISIBLE }

    /** Limiares de deteccao de credential stuffing (por janela do chamador). */
    public record StuffingThresholds(int maxDistinctDocumentsPerIp, int maxDistinctIpsPerDocument) {
        public StuffingThresholds {
            if (maxDistinctDocumentsPerIp < 1 || maxDistinctIpsPerDocument < 1) {
                throw new IllegalArgumentException("limiares de stuffing devem ser >= 1");
            }
        }
    }

    public record StuffingSignals(boolean manyDocumentsFromOneIp, boolean manyIpsForOneDocument) {
        public boolean any() { return manyDocumentsFromOneIp || manyIpsForOneDocument; }
        /** Evento de telemetria para a Central (sem documento, sem IP em claro no payload alem do necessario a deteccao). */
        public String telemetryEvent() {
            return any() ? "PORTAL_CREDENTIAL_STUFFING_SUSPECT" : "PORTAL_LOGIN_TELEMETRY";
        }
    }

    /** Porta da lista de senhas vazadas (adaptador = integracao; ausencia de adaptador = cadastro recusado). */
    public interface BreachedPasswordPort {
        boolean isBreached(String password);
    }

    private BotDefensePolicy() { }

    public static StuffingSignals detectStuffing(int distinctDocumentsFromIp, int distinctIpsForDocument,
                                                 StuffingThresholds thresholds) {
        if (thresholds == null) {
            throw new IllegalArgumentException("limiares sao obrigatorios");
        }
        return new StuffingSignals(
                distinctDocumentsFromIp > thresholds.maxDistinctDocumentsPerIp(),
                distinctIpsForDocument > thresholds.maxDistinctIpsPerDocument());
    }

    /** Desafio exigido conforme o modo e os sinais: OFF nunca desafia; PASSIVE so registra; CHALLENGE desafia sob sinal. */
    public static ChallengeKind requiredChallenge(PortalParameters.BotDefenseMode mode, StuffingSignals signals) {
        if (mode == null) {
            throw new IllegalArgumentException("portal.bot_defense e obrigatorio");
        }
        return switch (mode) {
            case OFF -> ChallengeKind.NONE;
            case PASSIVE -> ChallengeKind.NONE;
            case CHALLENGE -> signals != null && signals.any() ? ChallengeKind.PROOF_OF_WORK : ChallengeKind.INVISIBLE;
        };
    }

    /** Tipos de desafio permitidos: somente nao cognitivos (WCAG 2.2, 3.3.8). */
    public static final Set<ChallengeKind> ALLOWED_KINDS =
            Set.of(ChallengeKind.NONE, ChallengeKind.PROOF_OF_WORK, ChallengeKind.INVISIBLE);

    /**
     * Senha nova do portal: comprimento minimo, sem regra de composicao e
     * sem rotacao forcada (alinhamento NIST SP 800-63B declarado no
     * ADR-0040; a nota de alinhamento completa e da ED-06), e conferencia
     * obrigatoria contra a lista de senhas vazadas.
     */
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 128;

    public static void validateNewPassword(String password, BreachedPasswordPort breachedPort) {
        if (breachedPort == null) {
            throw new IllegalStateException("sem verificador de senhas vazadas: cadastro recusado (falha fechada)");
        }
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalArgumentException("senha deve ter ao menos " + PASSWORD_MIN_LENGTH + " caracteres");
        }
        if (password.length() > PASSWORD_MAX_LENGTH) {
            throw new IllegalArgumentException("senha deve ter no maximo " + PASSWORD_MAX_LENGTH + " caracteres");
        }
        if (breachedPort.isBreached(password)) {
            throw new IllegalArgumentException("senha presente em lista de senhas vazadas: escolha outra");
        }
    }

    /** Criacao de conta por e-mail dentro do limite de portal.account_throttle? */
    public static boolean accountCreationAllowed(int existingAccountsForEmail,
                                                 PortalParameters.AccountThrottle throttle) {
        if (throttle == null) {
            throw new IllegalArgumentException("portal.account_throttle e obrigatorio");
        }
        return existingAccountsForEmail < throttle.maxAccountsPerEmail();
    }
}
