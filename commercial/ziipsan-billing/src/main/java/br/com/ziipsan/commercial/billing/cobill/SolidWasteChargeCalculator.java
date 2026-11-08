package br.com.ziipsan.commercial.billing.cobill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * O componente de RESIDUOS SOLIDOS no calculo da conta (ED-25, CAD-90):
 * dado o valor mensal do servico (resolvido pelo chamador da tabela do
 * prestador/tenant - nada e semeado aqui), a competencia e o estado da
 * unidade, decide o valor cofaturado e COMO ele entra na cobranca.
 *
 * <p>Regras, uma a uma (cada uma com teste):
 * <ol>
 *   <li>cofaturamento desligado ({@code cobill.enabled=false}) ou
 *       contrato fora da vigencia: componente zero, sem linha;</li>
 *   <li>primeira cobranca sem a antecedencia do aviso (P2.32/NR 13):
 *       RECUSADA pelo portao de {@link CobillNoticeRule};</li>
 *   <li>ISENCAO vigente na competencia (situacao especial EXEMPTION da
 *       ED-22, entregue pelo chamador como fato - a ED-25 nao reinterpreta
 *       a ED-22): valor zero, com a linha discriminada zerada e o motivo
 *       registrado, para a conta mostrar POR QUE nao cobrou;</li>
 *   <li>RETIRADA a pedido do usuario vigente na competencia
 *       ({@link CobillWithdrawalRule}): valor zero, sem linha de cobranca;</li>
 *   <li>valores DISCRIMINADOS: a linha carrega rotulo, prestador, servico
 *       e o identificador unico do documento - nunca um total opaco;</li>
 *   <li>forma de cobranca ({@code cobill.billing_mode}): COBILLED soma no
 *       total da conta como componente proprio discriminado; fatura
 *       DESVINCULADA e CARNES ficam FORA do total da conta de agua
 *       ({@code includedInBillTotal=false}) e vao ao seu documento.</li>
 * </ol>
 *
 * <p>PENDENCIA HERDADA DA ED-22 (declarada, nao resolvida aqui): a
 * semantica do mes de retirada de uma situacao especial no dicionario
 * 23b S3.17 ("INTERPRETACAO PENDENTE" da ED-22 - ultimo mes valido x
 * primeiro mes que nao vale) NAO esta nesta VM. A isencao chega a esta
 * regra como FATO por competencia ({@code exemptInCompetence}), resolvido
 * pelo chamador com a leitura vigente da ED-22; se o 23b inverter a
 * semantica, a correcao e no chamador/ED-22, nao nesta regra.
 */
public final class SolidWasteChargeCalculator {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private SolidWasteChargeCalculator() {
    }

    /**
     * Linha discriminada do cofaturamento na conta/documento.
     *
     * @param documentIdentifier identificador unico do documento de
     *        cobranca (ver {@link CobillBookletPlan}); obrigatorio quando
     *        ha valor; em linha zerada de isencao pode vir o identificador
     *        da competencia sem cobranca - aqui exigido sempre, para a
     *        trilha ser completa
     */
    public record ChargeLine(String documentIdentifier, String label, String providerName,
                             CobillContract.ServiceKind serviceKind, BigDecimal amount,
                             boolean includedInBillTotal, boolean exempt) {
        public ChargeLine {
            if (documentIdentifier == null || documentIdentifier.isBlank()) {
                throw new IllegalArgumentException("identificador unico do documento e obrigatorio");
            }
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("rotulo discriminado e obrigatorio");
            }
            Objects.requireNonNull(providerName, "prestador e obrigatorio na linha discriminada");
            Objects.requireNonNull(serviceKind, "servico e obrigatorio na linha discriminada");
            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("valor cofaturado invalido: " + amount);
            }
        }
    }

    /** Composicao da cobranca da competencia: total proprio + cofaturado. */
    public record BillComposition(BigDecimal ownTotal, ChargeLine cobillLine) {
        public BillComposition {
            if (ownTotal == null || ownTotal.signum() < 0) {
                throw new IllegalArgumentException("total proprio invalido: " + ownTotal);
            }
        }

        /** Valor do componente cofaturado (zero quando nao ha linha). */
        public BigDecimal cobillAmount() {
            return cobillLine == null ? ZERO : cobillLine.amount();
        }

        /**
         * Total do DOCUMENTO da conta de agua: so inclui o cofaturado no
         * modo COBILLED; fatura desvinculada e carne tem documento proprio
         * e NAO inflam o total da conta.
         */
        public BigDecimal billDocumentTotal() {
            if (cobillLine != null && cobillLine.includedInBillTotal()) {
                return ownTotal.add(cobillLine.amount());
            }
            return ownTotal;
        }
    }

    /**
     * @param monthlyServiceAmount valor mensal do servico de residuos da
     *        unidade na competencia (tabela do prestador/tenant, resolvido
     *        pelo chamador)
     * @param exemptInCompetence   fato da isencao (ED-22) nesta competencia
     * @param withdrawn            fato da retirada a pedido nesta competencia
     * @param firstCharge          e a primeira cobranca do contrato para a
     *        unidade (o portao do aviso so se aplica ao inicio)
     */
    public static BillComposition compose(CobillParameters parameters, CobillContract contract,
                                          YearMonth competence, BigDecimal ownTotal,
                                          BigDecimal monthlyServiceAmount,
                                          boolean exemptInCompetence, boolean withdrawn,
                                          boolean firstCharge, String documentIdentifier) {
        Objects.requireNonNull(parameters, "parametros sao obrigatorios");
        Objects.requireNonNull(contract, "contrato e obrigatorio");
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        if (monthlyServiceAmount == null || monthlyServiceAmount.signum() < 0) {
            throw new IllegalArgumentException("valor mensal do servico invalido: " + monthlyServiceAmount);
        }
        BillComposition empty = new BillComposition(ownTotal, null);
        if (!parameters.enabled()) {
            return empty;
        }
        LocalDate competenceStart = competence.atDay(1);
        if (!contract.covers(competenceStart) || competenceStart.isBefore(contract.startDate())) {
            return empty;
        }
        if (firstCharge) {
            CobillNoticeRule.requireLeadTime(contract, parameters);
        }
        if (withdrawn) {
            return empty;
        }
        boolean included = parameters.billingMode() == CobillParameters.BillingMode.COBILLED;
        if (exemptInCompetence) {
            return new BillComposition(ownTotal, new ChargeLine(documentIdentifier, contract.billLabel(),
                    contract.providerName(), contract.serviceKind(), ZERO, included, true));
        }
        if (monthlyServiceAmount.signum() == 0) {
            return empty;
        }
        return new BillComposition(ownTotal, new ChargeLine(documentIdentifier, contract.billLabel(),
                contract.providerName(), contract.serviceKind(), monthlyServiceAmount, included, false));
    }
}
