package br.com.ziipsan.commercial.billing.cobill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Repasse e SEGREGACAO CONTABIL do cofaturamento (ED-25) pela regra por
 * evento da ED-32: o valor cofaturado NAO e receita do prestador de
 * agua - e valor de terceiro em transito. Por isso:
 *
 * <ul>
 *   <li>na EMISSAO, o fato contabil e proprio do cofaturamento
 *       ({@link CobillAccountingEvent#COBILL_ISSUED}), distinto do
 *       {@code BILL_ISSUED} da ED-32;</li>
 *   <li>no RECEBIMENTO, o pagamento e imputado entre proprio e terceiro
 *       pela ordem de {@code cobill.payment_allocation}; so a parte
 *       imputada ao terceiro vira repasse devido;</li>
 *   <li>o REPASSE ({@link CobillAccountingEvent#COBILL_TRANSFERRED}) e o
 *       valor recebido do terceiro, integral - retencao de qualquer
 *       percentual NAO esta no acceptance e nao foi inventada aqui:
 *       remuneracao do cofaturamento, se houver, e clausula do contrato
 *       ({@link CobillContract}) fora desta regra.</li>
 * </ul>
 *
 * <p>PENDENCIA DE INTEGRACAO DECLARADA: o enum
 * {@code AccountingEventKey} da ED-32 e fechado e nao tem as chaves do
 * cofaturamento. Esta entrega emite os fatos com as chaves nomeadas em
 * {@link CobillAccountingEvent}; a integracao acrescenta as tres chaves
 * ao enum da ED-32 e liga {@code JournalEntryGenerator} - a regra por
 * evento (debito/credito/historico em {@code acc.*}) e configuracao do
 * tenant, nao codigo, exatamente como na ED-32.
 */
public final class CobillSettlement {

    private CobillSettlement() {
    }

    /** Chaves de evento do cofaturamento, para a regra por evento da ED-32. */
    public enum CobillAccountingEvent {
        /** Emissao do valor cofaturado (passivo com o terceiro, nao receita). */
        COBILL_ISSUED,
        /** Recebimento imputado ao servico cofaturado. */
        COBILL_RECEIVED,
        /** Repasse ao prestador do servico cofaturado. */
        COBILL_TRANSFERRED
    }

    /**
     * Esfera do fato, espelho de {@code JournalEntryGenerator.Sphere} da
     * ED-32 (correcao pos-reprovacao D3). Enum local pelo mesmo motivo de
     * {@link CobillAccountingEvent}: a ED-25 nao importa o modulo
     * accounting; na integracao, os valores sao os mesmos e o mapeamento
     * e direto.
     */
    public enum Sphere {
        PARTICULAR, PUBLICO
    }

    /**
     * Escopo do fato (regulatorio x gerencial trafegam separados), espelho
     * de {@code JournalEntryGenerator.Scope} da ED-32 (correcao D3).
     */
    public enum Scope {
        REGULATORY, MANAGERIAL
    }

    /**
     * Fato contabil do cofaturamento, no MESMO desenho do {@code Fact} da
     * ED-32 (correcao pos-reprovacao D3): os 9 campos do fato real -
     * chave de evento, valor positivo, mes contabil (dia 1, vindo da
     * P2.5), DATA DO FATO (recorte diario da exportacao da ED-32),
     * esfera, escopo, contrato, municipio e referencia de origem
     * rastreavel (o identificador unico do documento). As unicas
     * divergencias restantes sao as ja declaradas: a chave de evento e o
     * enum local {@link CobillAccountingEvent} (o enum da ED-32 e
     * fechado - pendencia de integracao) e os espelhos
     * {@link Sphere}/{@link Scope} acima. A versao original omitia
     * {@code factDate}, {@code sphere}, {@code scope} e
     * {@code municipalityId} sem declarar - defeito D3, corrigido aqui
     * emitindo os quatro campos (nascem no chamador, como no fato real).
     * Como no {@code Fact} real, {@code municipalityId} nao e exigido
     * nao-nulo; {@code contractId} segue exigido (desenho desta regra).
     */
    public record CobillFact(CobillAccountingEvent event, BigDecimal amount, LocalDate accountingMonth,
                             LocalDate factDate, Sphere sphere, Scope scope, UUID contractId,
                             UUID municipalityId, String sourceReference) {
        public CobillFact {
            Objects.requireNonNull(event, "evento e obrigatorio");
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("valor do fato deve ser positivo: " + amount);
            }
            Objects.requireNonNull(accountingMonth, "mes contabil (P2.5) e obrigatorio");
            if (accountingMonth.getDayOfMonth() != 1) {
                throw new IllegalArgumentException("mes contabil deve ser o dia 1: " + accountingMonth);
            }
            Objects.requireNonNull(factDate, "data do fato e obrigatoria (recorte diario da exportacao, ED-32)");
            Objects.requireNonNull(sphere, "esfera e obrigatoria");
            Objects.requireNonNull(scope, "escopo (regulatorio/gerencial) e obrigatorio");
            if (sourceReference == null || sourceReference.isBlank()) {
                throw new IllegalArgumentException("referencia de origem (identificador do documento) e obrigatoria");
            }
            Objects.requireNonNull(contractId, "contrato de cofaturamento e obrigatorio no fato");
        }
    }

    /** Resultado da imputacao de um pagamento: quanto quitou de cada parte. */
    public record Allocation(BigDecimal ownSettled, BigDecimal cobillSettled,
                             BigDecimal ownOutstanding, BigDecimal cobillOutstanding) {
        /** Repasse devido ao terceiro = exatamente o recebido imputado ao cofaturado. */
        public BigDecimal transferDue() {
            return cobillSettled;
        }
    }

    /**
     * Imputa um pagamento (possivelmente parcial) entre o devido proprio
     * e o devido cofaturado, pela ordem do parametro
     * {@code cobill.payment_allocation}. Pagamento maior que o devido
     * total e recusado: excedente e credito do usuario (fluxo da P2.5),
     * nao repasse.
     */
    public static Allocation allocate(CobillParameters parameters, BigDecimal ownDue,
                                      BigDecimal cobillDue, BigDecimal payment) {
        Objects.requireNonNull(parameters, "parametros sao obrigatorios");
        requireNonNegative(ownDue, "devido proprio");
        requireNonNegative(cobillDue, "devido cofaturado");
        if (payment == null || payment.signum() <= 0) {
            throw new IllegalArgumentException("pagamento deve ser positivo: " + payment);
        }
        BigDecimal totalDue = ownDue.add(cobillDue);
        if (payment.compareTo(totalDue) > 0) {
            throw new IllegalArgumentException("pagamento maior que o devido total (excedente e "
                    + "credito do usuario, fluxo da P2.5): pago " + payment + ", devido " + totalDue);
        }
        BigDecimal ownSettled;
        BigDecimal cobillSettled;
        switch (parameters.paymentAllocation()) {
            case OWN_FIRST -> {
                ownSettled = payment.min(ownDue);
                cobillSettled = payment.subtract(ownSettled);
            }
            case PRO_RATA -> {
                if (totalDue.signum() == 0) {
                    ownSettled = BigDecimal.ZERO;
                    cobillSettled = BigDecimal.ZERO;
                } else {
                    ownSettled = payment.multiply(ownDue).divide(totalDue, 2, RoundingMode.DOWN);
                    cobillSettled = payment.subtract(ownSettled);
                    if (cobillSettled.compareTo(cobillDue) > 0) {
                        cobillSettled = cobillDue;
                        ownSettled = payment.subtract(cobillSettled);
                    }
                }
            }
            default -> throw new IllegalStateException("modo de imputacao nao tratado");
        }
        return new Allocation(ownSettled, cobillSettled,
                ownDue.subtract(ownSettled), cobillDue.subtract(cobillSettled));
    }

    /**
     * Fato de repasse a partir da imputacao: so ha repasse do que foi
     * recebido do terceiro. Data do fato, esfera, escopo e municipio vem
     * do chamador, como no {@code Fact} da ED-32 (correcao D3).
     */
    public static CobillFact transferFact(Allocation allocation, LocalDate accountingMonth,
                                            LocalDate factDate, Sphere sphere, Scope scope,
                                            String sourceReference, UUID contractId, UUID municipalityId) {
        Objects.requireNonNull(allocation, "imputacao e obrigatoria");
        if (allocation.transferDue().signum() <= 0) {
            throw new IllegalArgumentException("sem valor de terceiro recebido, nao ha repasse");
        }
        return new CobillFact(CobillAccountingEvent.COBILL_TRANSFERRED, allocation.transferDue(),
                accountingMonth, factDate, sphere, scope, contractId, municipalityId, sourceReference);
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " invalido: " + value);
        }
    }
}
