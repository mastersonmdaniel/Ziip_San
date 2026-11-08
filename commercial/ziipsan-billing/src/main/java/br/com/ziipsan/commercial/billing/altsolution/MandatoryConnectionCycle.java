package br.com.ziipsan.commercial.billing.altsolution;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Ciclo da ligação obrigatória à rede disponível (ED-88): Lei 11.445/2007,
 * art. 45; NR 13/ANA, arts. 17 a 20; NR 11/ANA, art. 8º.
 *
 * <p>Sequência: comunicação da disponibilidade → aviso na fatura anterior
 * (piso de 90 dias de {@code avail.notice_days} antes da primeira cobrança
 * de disponibilidade) → ligação dentro do teto de
 * {@code avail.max_connect_months} (1 ano), OU dispensa só por inviabilidade
 * comprovada, OU notificação/OS de ligação compulsória pela ED-13 com multa
 * após o prazo. A cobrança do VALOR de disponibilidade é da P2.5 (RAS-22):
 * valor e multiplicador ficam lá — aqui só se decide a ELEGIBILIDADE do
 * imóvel à cobrança ({@link #availabilityChargeApplies}).
 *
 * <p>A obrigação NÃO tem estado "desligada" nem método de desligamento:
 * ver {@link AltSolutionParameters#requireObligationNotDisabled(String)}.
 */
public final class MandatoryConnectionCycle {

    /** Efeito da ED-13 que executa a ligação (código lido da ED-13, ConnectionOperation.CONNECT). */
    public static final String ED13_CONNECT_EFFECT_CODE = "CONNECTION_CONNECT";

    public enum Status {
        AVAILABILITY_COMMUNICATED, BILL_NOTICE_ISSUED, CONNECTED, EXEMPTED, COMPULSORY_ORDER_ISSUED
    }

    public enum CompulsoryKind { NOTIFICATION, COMPULSORY_CONNECTION_ORDER }

    /**
     * Caso de um imóvel com rede disponível. {@code firstAvailabilityChargeOn}
     * só existe depois do aviso na fatura; {@code exemptionProofRef} só
     * existe no estado EXEMPTED.
     */
    public record AvailabilityCase(UUID propertyId, UUID consumerUnitId, Status status,
                                   LocalDate availabilityCommunicatedOn, LocalDate billNoticeOn,
                                   LocalDate firstAvailabilityChargeOn, LocalDate connectedOn,
                                   UUID connectionServiceOrderId, String exemptionProofRef,
                                   LocalDate compulsoryIssuedOn, CompulsoryKind compulsoryKind,
                                   String finePriceItemCode) {
        public AvailabilityCase {
            Objects.requireNonNull(propertyId, "imovel e obrigatorio");
            Objects.requireNonNull(consumerUnitId, "unidade consumidora e obrigatoria");
            Objects.requireNonNull(status, "situacao do caso e obrigatoria");
            Objects.requireNonNull(availabilityCommunicatedOn, "data da comunicacao da disponibilidade e obrigatoria");
            // Aviso + primeira cobranca sao obrigatorios nos estados que nascem
            // deles. CONNECTED e EXEMPTED podem ocorrer direto da comunicacao
            // (ligar ou comprovar inviabilidade antes de qualquer fatura).
            if (status == Status.BILL_NOTICE_ISSUED || status == Status.COMPULSORY_ORDER_ISSUED) {
                Objects.requireNonNull(billNoticeOn, "aviso na fatura anterior e obrigatorio neste estado: " + status);
                Objects.requireNonNull(firstAvailabilityChargeOn, "primeira cobranca de disponibilidade e obrigatoria neste estado: " + status);
            }
            if (status == Status.CONNECTED) {
                Objects.requireNonNull(connectedOn, "ligacao conectada exige a data da ligacao");
                Objects.requireNonNull(connectionServiceOrderId, "ligacao conectada exige a OS da ED-13");
            }
            if (status == Status.EXEMPTED && (exemptionProofRef == null || exemptionProofRef.isBlank()))
                throw new IllegalArgumentException("caso dispensado exige a referencia da prova de inviabilidade");
            if (status == Status.COMPULSORY_ORDER_ISSUED) {
                Objects.requireNonNull(compulsoryIssuedOn, "notificacao/OS compulsoria exige a data de emissao");
                Objects.requireNonNull(compulsoryKind, "notificacao/OS compulsoria exige o tipo");
            }
        }
    }

    /** Resultado da ligação: o caso conectado e a solução individual desativada (se havia). */
    public record ConnectionResult(AvailabilityCase availabilityCase, AltSolution altSolution,
                                   boolean connectedLate) {}

    private MandatoryConnectionCycle() {}

    /** Passo 1 — comunicação da disponibilidade da rede ao imóvel. */
    public static AvailabilityCase communicate(UUID propertyId, UUID consumerUnitId, LocalDate communicatedOn) {
        Objects.requireNonNull(communicatedOn, "data da comunicacao e obrigatoria");
        return new AvailabilityCase(propertyId, consumerUnitId, Status.AVAILABILITY_COMMUNICATED,
                communicatedOn, null, null, null, null, null, null, null, null);
    }

    /**
     * Passo 2 — aviso na fatura anterior: a primeira cobrança de
     * disponibilidade só pode nascer {@code noticeDays} (piso 90, classe c)
     * depois do aviso. Aviso antes da comunicação é recusado.
     */
    public static AvailabilityCase issueBillNotice(AvailabilityCase c, LocalDate billNoticeOn,
                                                   LocalDate firstChargeOn, int noticeDays) {
        requireStatus(c, Status.AVAILABILITY_COMMUNICATED, "aviso na fatura");
        Objects.requireNonNull(billNoticeOn, "data do aviso na fatura e obrigatoria");
        Objects.requireNonNull(firstChargeOn, "data da primeira cobranca de disponibilidade e obrigatoria");
        if (noticeDays < AltSolutionParameters.NOTICE_DAYS_FLOOR)
            throw new IllegalArgumentException("avail.notice_days abaixo do piso de "
                    + AltSolutionParameters.NOTICE_DAYS_FLOOR + " dias: " + noticeDays);
        if (billNoticeOn.isBefore(c.availabilityCommunicatedOn()))
            throw new IllegalArgumentException("aviso na fatura anterior a comunicacao da disponibilidade");
        LocalDate earliest = billNoticeOn.plusDays(noticeDays);
        if (firstChargeOn.isBefore(earliest))
            throw new IllegalArgumentException("primeira cobranca de disponibilidade em " + firstChargeOn
                    + " viola o aviso de " + noticeDays + " dias: o mais cedo e " + earliest);
        return copy(c, Status.BILL_NOTICE_ISSUED, billNoticeOn, firstChargeOn, null, null, null, null, null, null);
    }

    /** Prazo final para ligar: comunicação + {@code maxConnectMonths} (teto 12, classe c). */
    public static LocalDate connectDeadline(AvailabilityCase c, int maxConnectMonths) {
        Objects.requireNonNull(c, "caso e obrigatorio");
        if (maxConnectMonths < 1 || maxConnectMonths > AltSolutionParameters.MAX_CONNECT_MONTHS_CEILING)
            throw new IllegalArgumentException("avail.max_connect_months fora de 1.."
                    + AltSolutionParameters.MAX_CONNECT_MONTHS_CEILING + ": " + maxConnectMonths);
        return c.availabilityCommunicatedOn().plusMonths(maxConnectMonths);
    }

    /**
     * Elegibilidade à cobrança do valor de disponibilidade (o valor e o
     * multiplicador são da P2.5, RAS-22): imóvel avisado, ainda não ligado
     * nem dispensado, na data ou após a primeira cobrança pactuada no aviso.
     */
    public static boolean availabilityChargeApplies(AvailabilityCase c, LocalDate on) {
        Objects.requireNonNull(c, "caso e obrigatorio");
        Objects.requireNonNull(on, "data de referencia e obrigatoria");
        if (c.status() == Status.CONNECTED || c.status() == Status.EXEMPTED) return false;
        if (c.firstAvailabilityChargeOn() == null) return false;
        return !on.isBefore(c.firstAvailabilityChargeOn());
    }

    /**
     * Dispensa: só por inviabilidade COMPROVADA ({@code avail.exemption_requires_proof},
     * classe a). Sem referência de prova, a dispensa é recusada — sempre.
     */
    public static AvailabilityCase exempt(AvailabilityCase c, String proofRef, boolean requiresProof) {
        if (c.status() == Status.CONNECTED)
            throw new IllegalStateException("imovel ja ligado nao pode ser dispensado");
        if (c.status() == Status.EXEMPTED)
            throw new IllegalStateException("imovel ja dispensado");
        if (!requiresProof)
            throw new IllegalArgumentException("avail.exemption_requires_proof (classe a) esta travado em true: "
                    + "dispensa sem prova de inviabilidade nao existe");
        if (proofRef == null || proofRef.isBlank())
            throw new IllegalArgumentException("dispensa recusada: inviabilidade sem prova registrada "
                    + "(avail.exemption_requires_proof, classe a)");
        return copy(c, Status.EXEMPTED, c.billNoticeOn(), c.firstAvailabilityChargeOn(), null, null,
                proofRef.trim(), c.compulsoryIssuedOn(), c.compulsoryKind(), c.finePriceItemCode());
    }

    /**
     * Notificação ou OS de ligação compulsória pela ED-13, com multa: só
     * depois do prazo máximo, para imóvel nem ligado nem dispensado. O valor
     * da multa NÃO é calculado aqui (não há valor no acceptance): o item de
     * preço da ED-34 é registrado para a cobrança na integração.
     */
    public static AvailabilityCase issueCompulsory(AvailabilityCase c, CompulsoryKind kind, LocalDate issuedOn,
                                                   int maxConnectMonths, String finePriceItemCode) {
        if (c.status() == Status.CONNECTED || c.status() == Status.EXEMPTED)
            throw new IllegalStateException("ligacao compulsoria nao cabe para imovel " + c.status());
        if (c.status() == Status.COMPULSORY_ORDER_ISSUED)
            throw new IllegalStateException("ja ha notificacao/OS compulsoria emitida para o imovel");
        if (c.status() != Status.BILL_NOTICE_ISSUED)
            throw new IllegalStateException("ligacao compulsoria exige o aviso na fatura anterior ja emitido; "
                    + "estado atual: " + c.status());
        Objects.requireNonNull(kind, "tipo (notificacao ou OS compulsoria) e obrigatorio");
        Objects.requireNonNull(issuedOn, "data de emissao e obrigatoria");
        LocalDate deadline = connectDeadline(c, maxConnectMonths);
        if (!issuedOn.isAfter(deadline))
            throw new IllegalArgumentException("ligacao compulsoria so apos o prazo maximo de ligacao ("
                    + deadline + "); emissao em " + issuedOn + " e prematura");
        if (finePriceItemCode == null || finePriceItemCode.isBlank())
            throw new IllegalArgumentException("multa da ligacao compulsoria exige o item de preco do catalogo "
                    + "da ED-34 (nenhum valor de multa e inventado aqui)");
        return copy(c, Status.COMPULSORY_ORDER_ISSUED, c.billNoticeOn(), c.firstAvailabilityChargeOn(),
                null, null, null, issuedOn, kind, finePriceItemCode.trim());
    }

    /**
     * Ligação executada (OS da ED-13, efeito {@link #ED13_CONNECT_EFFECT_CODE}):
     * fecha o caso e DESATIVA a solução individual do imóvel, se houver uma
     * ativa. Ligar depois do prazo é aceito e marcado ({@code connectedLate}).
     */
    public static ConnectionResult connect(AvailabilityCase c, LocalDate connectedOn, UUID connectionServiceOrderId,
                                           int maxConnectMonths, AltSolution altSolutionOrNull) {
        if (c.status() == Status.CONNECTED)
            throw new IllegalStateException("imovel ja ligado");
        if (c.status() == Status.EXEMPTED)
            throw new IllegalStateException("imovel dispensado por inviabilidade comprovada nao liga por este ciclo");
        Objects.requireNonNull(connectedOn, "data da ligacao e obrigatoria");
        Objects.requireNonNull(connectionServiceOrderId, "OS de ligacao da ED-13 e obrigatoria");
        if (connectedOn.isBefore(c.availabilityCommunicatedOn()))
            throw new IllegalArgumentException("ligacao anterior a comunicacao da disponibilidade");
        boolean late = connectedOn.isAfter(connectDeadline(c, maxConnectMonths));
        AvailabilityCase done = copy(c, Status.CONNECTED, c.billNoticeOn(), c.firstAvailabilityChargeOn(),
                connectedOn, connectionServiceOrderId, null, c.compulsoryIssuedOn(), c.compulsoryKind(),
                c.finePriceItemCode());
        AltSolution deactivated = null;
        if (altSolutionOrNull != null) {
            if (!altSolutionOrNull.propertyId().equals(c.propertyId()))
                throw new IllegalArgumentException("solucao individual de outro imovel nao e desativada por esta ligacao");
            deactivated = altSolutionOrNull.active()
                    ? altSolutionOrNull.deactivateByConnection(connectedOn, connectionServiceOrderId)
                    : altSolutionOrNull;
        }
        return new ConnectionResult(done, deactivated, late);
    }

    private static void requireStatus(AvailabilityCase c, Status expected, String step) {
        Objects.requireNonNull(c, "caso e obrigatorio");
        if (c.status() != expected)
            throw new IllegalStateException(step + " exige o estado " + expected + "; estado atual: " + c.status());
    }

    private static AvailabilityCase copy(AvailabilityCase c, Status status, LocalDate billNoticeOn,
                                         LocalDate firstChargeOn, LocalDate connectedOn, UUID orderId,
                                         String proofRef, LocalDate compulsoryOn, CompulsoryKind kind,
                                         String fineCode) {
        return new AvailabilityCase(c.propertyId(), c.consumerUnitId(), status, c.availabilityCommunicatedOn(),
                billNoticeOn, firstChargeOn, connectedOn, orderId,
                proofRef != null ? proofRef : c.exemptionProofRef(), compulsoryOn, kind, fineCode);
    }
}
