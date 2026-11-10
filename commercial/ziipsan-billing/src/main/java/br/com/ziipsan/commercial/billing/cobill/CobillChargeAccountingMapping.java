package br.com.ziipsan.commercial.billing.cobill;

import br.com.ziipsan.commercial.accounting.event.AccountingEventKey;
import br.com.ziipsan.commercial.billing.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billing.composition.BillChargeLine;

import java.util.Objects;

/**
 * De-para contabil do cofaturamento, por DONO x NATUREZA do facto
 * (Camada 3; desenho da Camada 0, secao 3.1 item 4; INV-C3-07): traduz
 * os eventos locais que a ED-25 ja nomeia
 * ({@link CobillSettlement.CobillAccountingEvent}) para as chaves do
 * enum da ED-32 ({@link AccountingEventKey}) — as tres constantes
 * {@code COBILL_*} acrescentadas aditivamente por esta camada, que
 * destrava a pendencia de integracao declarada na ED-25 ("falta a
 * fiacao no enum da ED-32").
 *
 * <p><b>Regra de segregacao (INV-C3-07):</b> o de-para so existe para
 * dono {@link ChargeOwner#THIRD_PARTY}: linha de terceiro mapeia para
 * evento {@code COBILL_*} (o valor e do parceiro, em transito — nunca
 * receita propria, desenho de {@link CobillSettlement}). Linha
 * {@link ChargeOwner#OWN} NUNCA mapeia para {@code COBILL_*}: pedir o
 * de-para com dono proprio e recusado (falha fechada), porque um facto
 * proprio lancado como cofaturamento corromperia a segregacao
 * contabil. A natureza do facto (emitido/recebido/repassado) vem do
 * evento local; dono e natureza sao os dois eixos e nenhum substitui o
 * outro.
 *
 * <p>A fiacao no {@code JournalEntryGenerator} da ED-32 (a regra por
 * evento {@code acc.*} que consome a chave) e da INTEGRACAO: esta
 * classe entrega so o de-para; nenhuma regra contabil e inventada aqui.
 */
public final class CobillChargeAccountingMapping {

    private CobillChargeAccountingMapping() {
    }

    /**
     * Evento local da ED-25 + dono -> chave da ED-32.
     *
     * @throws IllegalArgumentException se o dono nao for
     *         {@code THIRD_PARTY} (linha propria nunca vira {@code COBILL_*})
     */
    public static AccountingEventKey toAccountingEventKey(
            CobillSettlement.CobillAccountingEvent event, ChargeOwner owner) {
        Objects.requireNonNull(event, "evento local do cofaturamento e obrigatorio");
        Objects.requireNonNull(owner, "dono da cobranca e obrigatorio");
        if (owner != ChargeOwner.THIRD_PARTY) {
            throw new IllegalArgumentException("evento COBILL_* e so de cobranca de terceiro; "
                    + "dono " + owner + " nunca mapeia para COBILL_* (INV-C3-07)");
        }
        return switch (event) {
            case COBILL_ISSUED -> AccountingEventKey.COBILL_ISSUED;
            case COBILL_RECEIVED -> AccountingEventKey.COBILL_RECEIVED;
            case COBILL_TRANSFERRED -> AccountingEventKey.COBILL_TRANSFERRED;
        };
    }

    /** De-para a partir da linha composta pelo maestro (o dono vem da linha). */
    public static AccountingEventKey forLine(
            BillChargeLine line, CobillSettlement.CobillAccountingEvent event) {
        Objects.requireNonNull(line, "linha da cobranca e obrigatoria");
        return toAccountingEventKey(event, line.owner());
    }

    /**
     * Sentido inverso: chave da ED-32 -> evento local da ED-25.
     * Chave que nao e {@code COBILL_*} = falha fechada (nao ha evento
     * local correspondente; nunca se presume um).
     */
    public static CobillSettlement.CobillAccountingEvent toLocalEvent(AccountingEventKey key) {
        Objects.requireNonNull(key, "chave de evento contabil e obrigatoria");
        return switch (key) {
            case COBILL_ISSUED -> CobillSettlement.CobillAccountingEvent.COBILL_ISSUED;
            case COBILL_RECEIVED -> CobillSettlement.CobillAccountingEvent.COBILL_RECEIVED;
            case COBILL_TRANSFERRED -> CobillSettlement.CobillAccountingEvent.COBILL_TRANSFERRED;
            default -> throw new IllegalArgumentException(
                    "chave sem correspondente no cofaturamento: " + key);
        };
    }

    /** A chave e um evento de cofaturamento ({@code COBILL_*})? */
    public static boolean isCobillKey(AccountingEventKey key) {
        Objects.requireNonNull(key, "chave de evento contabil e obrigatoria");
        return key == AccountingEventKey.COBILL_ISSUED
                || key == AccountingEventKey.COBILL_RECEIVED
                || key == AccountingEventKey.COBILL_TRANSFERRED;
    }
}
