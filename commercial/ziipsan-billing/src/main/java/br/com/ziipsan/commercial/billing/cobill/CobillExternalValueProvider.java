package br.com.ziipsan.commercial.billing.cobill;

import br.com.ziipsan.commercial.billing.composition.ChargeCalculationContext;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Implementacao da porta de valor externo da Camada 2
 * ({@link ChargeCalculationContext.ExternalValueProvider}) sobre os
 * contratos vigentes da ED-25 — desenho da Camada 0, secao 3.1 item 3;
 * INV-C3-06.
 *
 * <p><b>Sem formula nova:</b> o valor devolvido e, por construcao, o
 * componente cofaturado de
 * {@link SolidWasteChargeCalculator#compose(CobillParameters,
 * CobillContract, YearMonth, BigDecimal, BigDecimal, boolean, boolean,
 * boolean, String) compose(...)} vigente para a mesma
 * UC/competencia/contrato — esta classe so monta a chamada e devolve
 * {@code BillComposition.cobillAmount()}. O valor continua a NAO viver
 * no contrato (desenho da ED-25 preservado): vive na tabela do
 * prestador/tenant e chega resolvido nos factos (abaixo).
 *
 * <p><b>Ponte de factos (o minimo de invencao):</b> o contexto da
 * Camada 2 traz a resolucao do catalogo e a competencia, mas NAO traz
 * os factos do convenio (parametros do tenant, contrato, valor mensal
 * do prestador, isencao/retirada como factos, total proprio,
 * identificador do documento). Esses factos — exactamente os argumentos
 * que o {@code compose} vigente ja exige do chamador — entram pela
 * porta tipada propria desta classe, {@link FactsResolver}, que devolve
 * o record {@link CobillFacts} por resolucao/contexto. Nada e lido de
 * banco aqui (o mesmo contrato do maestro: tudo pre-resolvido pelo
 * chamador) e nenhum facto e presumido: facto ausente = falha fechada.
 *
 * <p><b>Ligacao resolucao x contrato (falha fechada):</b> a resolucao
 * tem de ser de modelo {@code EXTERNAL_VALUE} e o
 * {@code externalContractId} da versao tem de ser o id do contrato dos
 * factos; quando a definicao declara {@code thirdPartyProviderId}, ele
 * tem de ser o prestador do contrato. Divergencia = excecao — nunca se
 * cobra um contrato em nome de outra resolucao.
 *
 * <p><b>FRONTEIRA {@code billing_mode} (declarada, nao escondida):</b>
 * o {@code compose} vigente discrimina o valor em todos os modos, mas
 * so o soma ao total do documento da conta no modo {@code COBILLED}
 * ({@code ChargeLine.includedInBillTotal()}); em
 * {@code SEPARATE_INVOICE}/{@code BOOKLET_MONTHLY}/{@code BOOKLET_ANNUAL}
 * o valor tem documento proprio e fica FORA do total da conta
 * ({@code BillComposition.billDocumentTotal()}). Esta porta devolve
 * sempre o VALOR DISCRIMINADO ({@code cobillAmount()}), porque e esse o
 * contrato da porta da Camada 2; quem decide se ele entra no total e o
 * maestro, pela natureza {@code THIRD_PARTY} da linha que a calculadora
 * {@code ExternalValueCalculator} constroi. Consequencia de fiacao para
 * a INTEGRACAO: ligar esta porta a uma cobranca do catalogo pressupoe
 * {@code billing_mode == COBILLED} para essa cobranca; nos demais modos
 * o valor discriminado segue para o seu documento proprio (caminho
 * vigente da ED-25) e NAO deve ser registado como linha da conta — o
 * chamador pode verificar o modo antes de compor via
 * {@link #resolveComposition(ChargeResolution, ChargeCalculationContext)}
 * (a composicao integral, com a linha e o seu
 * {@code includedInBillTotal()}).
 *
 * <p><b>Casos zero (INV-C3-06, todos via {@code compose}):</b>
 * cofaturamento desligado, contrato fora de vigencia ou competencia
 * anterior ao inicio, retirada vigente e valor mensal zero devolvem
 * {@code 0.00} sem linha; isencao vigente devolve {@code 0.00} COM a
 * linha discriminada zerada na composicao (o zero devolvido e o mesmo).
 * A primeira cobranca sem a antecedencia do aviso (NR 13) continua
 * RECUSADA pelo portao vigente — a excecao propaga, nao e convertida
 * em zero.
 */
public final class CobillExternalValueProvider implements ChargeCalculationContext.ExternalValueProvider {

    /**
     * Os factos pre-resolvidos do convenio para uma composicao — os
     * argumentos do {@code compose} vigente da ED-25, nomeados:
     *
     * @param parameters           parametros do tenant ({@link CobillParameters})
     * @param contract             contrato de cofaturamento da UC/servico
     * @param ownTotal             total proprio da conta na competencia
     *                             (o {@code compose} o carrega na
     *                             composicao; o valor cofaturado nao
     *                             depende dele)
     * @param monthlyServiceAmount valor mensal do servico, resolvido da
     *                             tabela do prestador/tenant — nunca do
     *                             contrato
     * @param exemptInCompetence   facto da isencao (ED-22) na competencia
     * @param withdrawn            facto da retirada a pedido na competencia
     * @param firstCharge          se e a primeira cobranca do contrato
     *                             para a unidade (portao do aviso, NR 13)
     * @param documentIdentifier   identificador unico do documento;
     *                             exigido pelo {@code compose} quando ha
     *                             linha (normal ou zerada de isencao) —
     *                             ausente nesse caso, a linha vigente
     *                             recusa (falha fechada)
     */
    public record CobillFacts(CobillParameters parameters, CobillContract contract,
                              BigDecimal ownTotal, BigDecimal monthlyServiceAmount,
                              boolean exemptInCompetence, boolean withdrawn,
                              boolean firstCharge, String documentIdentifier) {
        public CobillFacts {
            Objects.requireNonNull(parameters, "parametros do cofaturamento sao obrigatorios");
            Objects.requireNonNull(contract, "contrato de cofaturamento e obrigatorio");
            Objects.requireNonNull(ownTotal, "total proprio da conta e obrigatorio");
            Objects.requireNonNull(monthlyServiceAmount,
                    "valor mensal do servico (tabela do prestador) e obrigatorio");
        }
    }

    /**
     * Porta tipada dos factos do convenio: o chamador (camada de
     * aplicacao) resolve parametros, contrato e factos da
     * UC/competencia e entrega-os prontos, por resolucao e contexto.
     */
    @FunctionalInterface
    public interface FactsResolver {
        CobillFacts resolve(ChargeResolution resolution, ChargeCalculationContext context);
    }

    private final FactsResolver factsResolver;

    public CobillExternalValueProvider(FactsResolver factsResolver) {
        this.factsResolver = Objects.requireNonNull(factsResolver,
                "porta de factos do convenio e obrigatoria");
    }

    /** Conveniencia: factos fixos (uma UC/competencia ja resolvida pelo chamador). */
    public CobillExternalValueProvider(CobillFacts fixedFacts) {
        this((resolution, context) -> Objects.requireNonNull(fixedFacts,
                "factos do convenio sao obrigatorios"));
    }

    /**
     * O valor cofaturado devido: exactamente
     * {@code compose(...).cobillAmount()} para os mesmos factos
     * (INV-C3-06, tolerancia zero).
     */
    @Override
    public BigDecimal resolveValue(ChargeResolution resolution, ChargeCalculationContext context) {
        return resolveComposition(resolution, context).cobillAmount();
    }

    /**
     * A composicao integral do {@code compose} vigente para os factos
     * da resolucao/contexto — expoe a linha discriminada e o seu
     * {@code includedInBillTotal()} para o chamador decidir a fronteira
     * do {@code billing_mode} (ver o javadoc da classe).
     */
    public SolidWasteChargeCalculator.BillComposition resolveComposition(
            ChargeResolution resolution, ChargeCalculationContext context) {
        Objects.requireNonNull(resolution, "resolucao da cobranca e obrigatoria");
        Objects.requireNonNull(context, "contexto de calculo e obrigatorio");
        if (resolution.definition().calculationModel() != CalculationModel.EXTERNAL_VALUE
                || resolution.version().calculationModel() != CalculationModel.EXTERNAL_VALUE) {
            throw new IllegalArgumentException("a porta de convenio so resolve cobrancas "
                    + "EXTERNAL_VALUE: " + resolution.definition().code());
        }
        CobillFacts facts = Objects.requireNonNull(factsResolver.resolve(resolution, context),
                "factos do convenio nao resolvidos para a cobranca "
                        + resolution.definition().code());
        if (!facts.contract().id().equals(resolution.version().externalContractId())) {
            throw new IllegalStateException("contrato dos factos (" + facts.contract().id()
                    + ") diverge do externalContractId da versao da cobranca "
                    + resolution.definition().code() + " (" + resolution.version().externalContractId()
                    + ") — cobranca recusada");
        }
        if (resolution.definition().thirdPartyProviderId() != null
                && !resolution.definition().thirdPartyProviderId().equals(facts.contract().providerId())) {
            throw new IllegalStateException("prestador do contrato dos factos ("
                    + facts.contract().providerId() + ") diverge do thirdPartyProviderId da "
                    + "definicao da cobranca " + resolution.definition().code()
                    + " (" + resolution.definition().thirdPartyProviderId() + ") — cobranca recusada");
        }
        // O tipo generalizado do contrato tem de ser o servico que esta
        // porta sabe compor; o adapter falha fechado sem par vigente.
        CobillServiceKind kind = CobillServiceKindAdapter.toKind(facts.contract().serviceKind());
        if (kind != CobillServiceKind.SOLID_WASTE) {
            throw new IllegalStateException("tipo de servico sem calculadora nesta camada: " + kind);
        }
        YearMonth competence = YearMonth.from(context.competence());
        return SolidWasteChargeCalculator.compose(facts.parameters(), facts.contract(), competence,
                facts.ownTotal(), facts.monthlyServiceAmount(), facts.exemptInCompetence(),
                facts.withdrawn(), facts.firstCharge(), facts.documentIdentifier());
    }
}
