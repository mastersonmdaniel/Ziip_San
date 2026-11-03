package br.com.ziipsan.platform.console;

import br.com.ziipsan.platform.security.PlatformRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;

/**
 * Regras puras do console da plataforma (P1.6), sem banco, para que a
 * matriz de autorização, a carência e o gate sejam testáveis sem
 * PostgreSQL. O banco continua sendo a trava final (CHECKs da V9 e
 * GRANTs), e o serviço aplica estas mesmas regras antes de escrever.
 */
public final class PlatformConsoleRules {

    /** Carência de eliminação: 30 dias a partir do pedido (plano v3.0, P1.6). */
    public static final Duration DELETION_GRACE = Duration.ofDays(30);

    /** Motivos de suspensão do domínio da V9 (coluna suspension_reason). */
    public static final Set<String> SUSPENSION_REASONS = Set.of(
            "CONTRACT_DEFAULT", "REGULATORY_ORDER", "FRAUD_PREVENTION", "OTHER");

    /** O único motivo pelo qual PLATFORM_FINANCE pode suspender. */
    public static final String FINANCE_SUSPENSION_REASON = "CONTRACT_DEFAULT";

    private PlatformConsoleRules() {
    }

    /**
     * Quem pode suspender, e com qual motivo (plano v3.0, P1.6, com teste):
     * PLATFORM_OWNER suspende por qualquer motivo; PLATFORM_FINANCE só por
     * inadimplência contratual; qualquer outro papel, nunca. A exigência de
     * MFA vive no filtro ({@code MfaGateFilter}, amr no token) e o step-up
     * é confirmado na chamada do serviço — nenhum dos dois é decidido aqui.
     */
    public static boolean canSuspend(String role, String reason) {
        if (PlatformRole.OWNER.equals(role)) {
            return reason != null && SUSPENSION_REASONS.contains(reason);
        }
        if (PlatformRole.FINANCE.equals(role)) {
            return FINANCE_SUSPENSION_REASON.equals(reason);
        }
        return false;
    }

    /** Eliminação só por PLATFORM_OWNER (plano v3.0, P1.6, com teste). */
    public static boolean canDelete(String role) {
        return PlatformRole.OWNER.equals(role);
    }

    /**
     * Registrar ou liberar um hold de eliminação só atrasa a eliminação
     * (direção fail-safe): OWNER e OPS podem, porque o hold nunca apaga
     * dado — ele só impede o apagamento. A eliminação em si continua
     * restrita a OWNER ({@link #canDelete}).
     */
    public static boolean canHoldDeletion(String role) {
        return PlatformRole.OWNER.equals(role) || PlatformRole.OPS.equals(role);
    }

    /** Fim da carência de 30 dias a partir do pedido de eliminação. */
    public static Instant graceUntil(Instant requestedAt) {
        if (requestedAt == null) {
            throw new IllegalArgumentException("O pedido de eliminação é obrigatório");
        }
        return requestedAt.plus(DELETION_GRACE);
    }

    /**
     * A eliminação só se completa com o tenant em DELETION_GRACE, a carência
     * vencida e nenhum hold vigente. Contestação ou ordem judicial levam a
     * DELETION_HELD, e o estado HELD nunca completa, mesmo com a carência
     * vencida — é preciso liberar o hold primeiro.
     */
    public static boolean deletionMayComplete(String status, Instant now, Instant graceUntil) {
        return "DELETION_GRACE".equals(status)
                && graceUntil != null && now != null && !now.isBefore(graceUntil);
    }

    /**
     * Carência restante quando um hold é liberado: o relógio retoma de onde
     * parou (carência menos o tempo já corrido até o hold), nunca zerado e
     * nunca estendido. Se nada restava, a eliminação fica elegível na hora.
     */
    public static Instant graceUntilAfterHoldRelease(Instant graceUntil, Instant holdAt, Instant releasedAt) {
        if (graceUntil == null || holdAt == null || releasedAt == null) {
            throw new IllegalArgumentException("Datas do hold são obrigatórias");
        }
        Duration remaining = Duration.between(holdAt, graceUntil);
        if (remaining.isNegative() || remaining.isZero()) {
            return releasedAt;
        }
        return releasedAt.plus(remaining);
    }

    /** Porcentagem concluída do checklist (0–100, inteiro, sem arredondar para cima). */
    public static int onboardingPercent(int done, int total) {
        if (total <= 0) {
            return 0;
        }
        if (done <= 0) {
            return 0;
        }
        if (done >= total) {
            return 100;
        }
        return (done * 100) / total;
    }

    /**
     * Gate automático de pronto para produção (P1.6): o checklist precisa
     * estar 100% concluído, o perfil do tenant precisa existir (fonte
     * única, DAD-32) e nenhuma versão de parâmetro aprovada pode estar sem
     * aceite formal — "produção bloqueada sem parâmetros aceitos". O gate
     * não exige que o tenant tenha valor para toda definição do catálogo:
     * definição sem versão usa o padrão de fábrica do catálogo (P0.19), e
     * é o padrão, não a ausência de versão, que vale para a operação.
     */
    public static boolean productionReady(int stepsDone, int stepsTotal,
                                          boolean profilePresent, long approvedWithoutAcceptance) {
        return stepsTotal > 0 && stepsDone >= stepsTotal
                && profilePresent && approvedWithoutAcceptance == 0;
    }

    /**
     * Alerta de risco da tela de mudança de parâmetro: classe b (padrão de
     * fábrica com aceite fora do padrão) com valor diferente do padrão
     * exige o reconhecimento do alerta no aceite. Classe a travada não gera
     * alerta — ela é recusada antes (ver o serviço). Classe c (só amplia)
     * e d (livre) não geram alerta por si: os limites legais delas vivem
     * em parameter_legal_limits e são conferidos pelo serviço.
     */
    public static boolean requiresRiskAlert(String paramClass, String factoryDefaultJson, String proposedJson) {
        if (!"b".equals(paramClass) || factoryDefaultJson == null) {
            return false;
        }
        return !factoryDefaultJson.equals(proposedJson);
    }

    /** Hash do conteúdo canônico de uma versão de parâmetro (chave + valor + vigência). */
    public static String contentHash(String definitionKey, String valueJson,
                                     LocalDate validFrom, LocalDate validTo) {
        String canonical = definitionKey + "\n" + valueJson + "\n" + validFrom
                + "\n" + (validTo == null ? "" : validTo.toString());
        return sha256Hex(canonical);
    }

    /** Certificado de descarte: hash determinístico do tenant + pedido, registrado em tenant_lifecycle. */
    public static String deletionCertificateHash(String tenantId, Instant requestedAt) {
        return sha256Hex(tenantId + "\n" + requestedAt.toString());
    }

    static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
