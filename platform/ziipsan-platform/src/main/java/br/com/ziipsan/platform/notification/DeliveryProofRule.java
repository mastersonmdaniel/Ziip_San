package br.com.ziipsan.platform.notification;

import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Regras puras da prova de entrega (P3.5), calculáveis à mão e testadas sem banco:
 *
 * <ul>
 *   <li><b>Prova de entrega.</b> Um aviso tem prova quando alguma tentativa dele
 *       chegou a {@link DeliveryStatus#DELIVERED}. Tentativa só enviada, rejeitada
 *       ({@code BOUNCED}) ou reclamada ({@code COMPLAINED}) NÃO é prova — rejeição
 *       e reclamação contam como aviso não entregue. Sem prova, a OS de corte é
 *       BLOQUEADA ({@link #assertCutoffAllowed}): é a regra que a P3.4 aplica na
 *       seleção/execução do corte, com teste dedicado.</li>
 *   <li><b>Domínio alinhado.</b> O envio só é aceito com SPF, DKIM e DMARC
 *       alinhados ao domínio de {@code msg.email_from_domain} DO tenant —
 *       requisito de remetente em massa de Gmail, Yahoo e Microsoft.</li>
 *   <li><b>Taxa de reclamação.</b> Reclamações ÷ envios precisa ficar ABAIXO de
 *       0,10% ({@link #MAX_COMPLAINT_RATE}); atingir 0,10% já viola o requisito
 *       ("abaixo de", não "até"). A taxa é por tenant: a violação pausa só o
 *       tenant infrator.</li>
 * </ul>
 */
public final class DeliveryProofRule {

    /** Teto do requisito de remetente em massa: taxa de reclamação ABAIXO de 0,10%. */
    public static final BigDecimal MAX_COMPLAINT_RATE = new BigDecimal("0.0010");

    private DeliveryProofRule() {
    }

    /** Verdade se alguma tentativa do aviso foi entregue — rejeitada/reclamada não contam. */
    public static boolean hasProof(Collection<DeliveryAttempt> attemptsOfNotice) {
        if (attemptsOfNotice == null) {
            throw new IllegalArgumentException("as tentativas do aviso são obrigatórias (podem ser vazias, não nulas)");
        }
        return attemptsOfNotice.stream().anyMatch(a -> a.status() == DeliveryStatus.DELIVERED);
    }

    /**
     * O portão do corte: sem prova de entrega do aviso, a OS de corte não nasce
     * nem executa — recusa 409 nomeando a regra. É o teste do aceite ("aviso de
     * corte sem prova de entrega bloqueia a OS").
     */
    public static void assertCutoffAllowed(Collection<DeliveryAttempt> attemptsOfNotice) {
        if (!hasProof(attemptsOfNotice)) {
            throw new ApiException.Conflict("cutoff-notice-not-delivered",
                    "Aviso de corte sem prova de entrega: a OS de corte está bloqueada.");
        }
    }

    /** Recusa (422) enviar de domínio sem SPF, DKIM e DMARC alinhados ao {@code msg.email_from_domain} do tenant. */
    public static void assertDomainAligned(String configuredDomain, SenderDomainStatus status) {
        if (configuredDomain == null || configuredDomain.isBlank() || status == null) {
            throw new IllegalArgumentException("msg.email_from_domain e o estado do domínio são obrigatórios");
        }
        if (!configuredDomain.equalsIgnoreCase(status.domain())) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "msg.email_from_domain", "domain-mismatch",
                    "O domínio conferido no provedor não é o domínio de envio configurado do tenant."));
        }
        if (!status.fullyAligned()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "msg.email_from_domain", "domain-not-aligned",
                    "Domínio de envio sem SPF, DKIM e DMARC alinhados: o envio é recusado."));
        }
    }

    /**
     * Taxa de reclamação = reclamações ÷ envios, com 6 casas. Sem envios no
     * período, a taxa não existe: erro explícito, nunca zero presumido.
     */
    public static BigDecimal complaintRate(long sent, long complaints) {
        if (sent <= 0) {
            throw new IllegalArgumentException("a taxa de reclamação exige envios no período (recebido: " + sent + ")");
        }
        if (complaints < 0 || complaints > sent) {
            throw new IllegalArgumentException("reclamações fora do intervalo [0, envios]: " + complaints);
        }
        return BigDecimal.valueOf(complaints).divide(BigDecimal.valueOf(sent), 6, RoundingMode.HALF_UP);
    }

    /** Verdade quando a taxa atinge ou passa de 0,10% — o requisito é ficar ABAIXO dela. */
    public static boolean complaintRateBreaches(long sent, long complaints) {
        return complaintRate(sent, complaints).compareTo(MAX_COMPLAINT_RATE) >= 0;
    }
}
