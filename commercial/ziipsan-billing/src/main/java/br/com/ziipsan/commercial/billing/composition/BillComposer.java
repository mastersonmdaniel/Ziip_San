package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.CreditApplier;
import br.com.ziipsan.commercial.billing.bill.DebitLine;
import br.com.ziipsan.commercial.billing.bill.DebitReceivable;
import br.com.ziipsan.commercial.billing.bill.Installments;
import br.com.ziipsan.commercial.billing.bill.TaxLine;
import br.com.ziipsan.commercial.billing.bill.TaxWithholding;
import br.com.ziipsan.commercial.billing.catalog.CalculationModel;
import br.com.ziipsan.commercial.billing.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.tariff.SewerCharge;
import br.com.ziipsan.commercial.billing.tariff.TariffCharge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * O maestro (desenho da Camada 0, secao 2.1 item 11): compoe a conta a
 * partir do catalogo, na ORDEM NORMATIVA da secao 2.3, invocando —
 * nunca reimplementando — as pecas vigentes. Dominio puro e estatico,
 * como o {@code BillCalculator} vigente, que NAO e editado nem
 * substituido nesta camada.
 *
 * <p>Ordem executada:
 * <ol>
 *   <li>agua (PROGRESSIVE_BY_CONSUMPTION propria, delegacao no
 *       {@code TariffEngine});</li>
 *   <li>esgoto (DERIVED_FROM_VOLUME proprio, idem);</li>
 *   <li>fixa (FIXED_PER_UNIT propria; a designada transporta o
 *       {@code fixedPartValue} vigente — INV-C2-02);</li>
 *   <li>demais proprias resolvidas pelo catalogo, ordenadas por
 *       {@code code};</li>
 *   <li>debitos: a parcela do mes de cada debito, via
 *       {@link Installments} vigente — FORA do catalogo (PD-TAR-05);
 *       debito sem parcela seleccionada lanca a mesma excecao do
 *       vigente (INV-C2-05);</li>
 *   <li>creditos: via {@link CreditApplier} vigente, com teto =
 *       proprias + debitos (o terceiro NAO entra no teto —
 *       INV-C2-03);</li>
 *   <li>retencao: via {@link TaxWithholding} vigente sobre
 *       proprias + debitos - creditos (terceiro fora da base —
 *       INV-C2-04);</li>
 *   <li>linhas de terceiro, POR ULTIMO: calculadas so neste passo,
 *       somam apenas ao {@code thirdPartyTotal} e ao total.</li>
 * </ol>
 * Total = (proprias + debitos - creditos - impostos) + terceiro, onde
 * o parentesis e exactamente a formula vigente do CHECK
 * {@code chk_bills_total_with_withholding_tax}.
 *
 * <p>Antes de compor, o maestro revalida o formato do
 * {@code municipalityIbge} da UC (regra de fronteira (i): falha
 * fechada se malformado) e cada calculadora tarifaria verifica a
 * coerencia D-03 sobre os escopos pre-resolvidos (regra (ii)). O
 * schedule do contexto ja vem seleccionado por municipio pelo
 * chamador (seleccao previa D-03.3, na montagem).
 *
 * <p><b>Pre-condicao da INV-C2-01 — espelhamento resolucoes/servicos
 * classicos (correccao R2-02):</b> a equivalencia com o
 * {@code BillCalculator} vigente vale para contextos em que as
 * resolucoes ESPELHAM os servicos classicos da entrada: agua faturada
 * ({@code waterConsumption != null}) corresponde a uma resolucao
 * PROGRESSIVE_BY_CONSUMPTION presente, e esgoto
 * ({@code sewer != null}) corresponde a uma resolucao
 * DERIVED_FROM_VOLUME presente. Os dois sentidos sao verificados no
 * inicio da composicao ({@link #validateClassicMirror}) e a
 * divergencia falha fechada — nunca agua ou esgoto 0,00 em silencio,
 * nunca servico classico ignorado.
 *
 * <p><b>TFDI avulsa nunca se perde em silencio (correccao R2-01):</b>
 * a decisao sobre o {@code fixedPartValue} resolvido e pela presenca
 * da LINHA DESIGNADA — a linha {@code FIXED_PER_UNIT} cujo codigo e o
 * {@code fixedPartChargeCode} declarado pelo chamador: presente, o
 * valor da linha TEM de ser igual (em valor) ao resolvido, e a
 * divergencia e excecao fechada; ausente, o resolvido entra
 * exactamente como no vigente (uma vez no subtotal proprio e
 * projectado no resultado), haja ou nao outras linhas
 * {@code FIXED_PER_UNIT} genericas.
 */
public final class BillComposer {

    private static final Map<CalculationModel, ChargeCalculator> CALCULATORS = calculators();

    private BillComposer() {
    }

    private static Map<CalculationModel, ChargeCalculator> calculators() {
        Map<CalculationModel, ChargeCalculator> map = new EnumMap<>(CalculationModel.class);
        for (ChargeCalculator calculator : List.of(
                new ProgressiveConsumptionCalculator(),
                new DerivedVolumePercentCalculator(),
                new FixedPerUnitCalculator(),
                new CatalogPriceCalculator(),
                new PercentOnBaseCalculator(),
                new ExternalValueCalculator())) {
            map.put(calculator.model(), calculator);
        }
        return Map.copyOf(map);
    }

    /** A calculadora registada para o modelo (uma por modelo, seis no total). */
    public static ChargeCalculator calculatorFor(CalculationModel model) {
        ChargeCalculator calculator = CALCULATORS.get(
                Objects.requireNonNull(model, "modelo e obrigatorio"));
        if (calculator == null) {
            throw new IllegalStateException("sem calculadora registada para o modelo " + model);
        }
        return calculator;
    }

    public static BillComposition compose(ChargeCalculationContext context) {
        Objects.requireNonNull(context, "contexto de composicao e obrigatorio");
        // Regra de fronteira (i): revalidacao na composicao (a primeira
        // validacao e na construcao do contexto).
        ChargeCalculationContext.validateMunicipalityFormat(context.municipalityIbge(),
                "municipalityIbge da UC");

        List<ChargeResolution> sorted = context.resolvedCharges().stream()
                .sorted(Comparator.comparing((ChargeResolution r) -> r.definition().code()))
                .toList();

        // Pre-condicao da INV-C2-01 (correccao R2-02): espelhamento
        // resolucoes <-> servicos classicos, verificado ANTES de
        // qualquer linha ser calculada.
        validateClassicMirror(context, sorted);

        List<BillChargeLine> ownLines = new ArrayList<>();
        // 1. agua -> 2. esgoto -> 3. fixa -> 4. demais proprias por code
        for (CalculationModel step : List.of(CalculationModel.PROGRESSIVE_BY_CONSUMPTION,
                CalculationModel.DERIVED_FROM_VOLUME, CalculationModel.FIXED_PER_UNIT)) {
            for (ChargeResolution resolution : sorted) {
                if (resolution.definition().calculationModel() == step
                        && !isThirdParty(resolution)) {
                    ownLines.add(calculate(context, resolution));
                }
            }
        }
        for (ChargeResolution resolution : sorted) {
            CalculationModel model = resolution.definition().calculationModel();
            if (model != CalculationModel.PROGRESSIVE_BY_CONSUMPTION
                    && model != CalculationModel.DERIVED_FROM_VOLUME
                    && model != CalculationModel.FIXED_PER_UNIT
                    && !isThirdParty(resolution)) {
                ownLines.add(calculate(context, resolution));
            }
        }

        BigDecimal ownSubtotal = Money.ZERO;
        for (BillChargeLine line : ownLines) {
            ownSubtotal = ownSubtotal.add(line.amount());
        }
        // Parcela fixa classica (TFDI) — correccao R2-01: a decisao e
        // pela presenca da LINHA DESIGNADA (a linha FIXED_PER_UNIT do
        // codigo declarado em fixedPartChargeCode), NAO por qualquer
        // linha fixa. Antes, havendo uma taxa fixa generica de catalogo
        // sem a linha da TFDI, o valor avulso resolvido perdia-se em
        // silencio (nem subtotal, nem campo, nem excecao).
        //  (a) linha designada presente: o valor dela TEM de ser igual
        //      ao fixedPartValue resolvido — divergindo, excecao
        //      fechada (a composicao nao escolhe em silencio); igual,
        //      nada se soma outra vez (a linha ja esta no subtotal —
        //      sem duplicacao, INV-C2-02);
        //  (b) linha designada ausente: o valor resolvido entra
        //      exactamente como no vigente — uma unica vez no subtotal
        //      proprio e projectado no campo fixedPartValue — haja ou
        //      nao outras linhas FIXED_PER_UNIT genericas (essas ja
        //      estao no subtotal pelas suas proprias linhas).
        BillChargeLine designatedFixedLine =
                designatedFixedLine(ownLines, context.fixedPartChargeCode());
        BigDecimal fixedPartValue;
        if (designatedFixedLine != null) {
            validateDesignatedFixedLine(designatedFixedLine, context.fixedPartValue());
            fixedPartValue = designatedFixedLine.amount();
        } else {
            fixedPartValue = context.fixedPartValue();
            ownSubtotal = ownSubtotal.add(fixedPartValue);
        }

        TariffCharge water = null;
        SewerCharge sewer = null;
        BigDecimal waterValue = Money.ZERO;
        BigDecimal sewerValue = Money.ZERO;
        for (BillChargeLine line : ownLines) {
            if (line.model() == CalculationModel.PROGRESSIVE_BY_CONSUMPTION) {
                waterValue = waterValue.add(line.amount());
                if (water == null && line.detail() instanceof TariffCharge charge) {
                    water = charge;
                }
            }
            if (line.model() == CalculationModel.DERIVED_FROM_VOLUME) {
                sewerValue = sewerValue.add(line.amount());
                if (sewer == null && line.detail() instanceof SewerCharge charge) {
                    sewer = charge;
                }
            }
        }

        // 5. debitos (Installments vigente, fora do catalogo)
        List<DebitLine> debitLines = new ArrayList<>();
        BigDecimal debitsValue = Money.ZERO;
        for (DebitReceivable debit : context.debits()) {
            if (!debit.hasInstallmentToCharge()) {
                // Mesma excecao (tipo e mensagem) do BillCalculator vigente (INV-C2-05).
                throw new IllegalArgumentException("Débito " + debit.id() + " não tem parcela a cobrar; "
                        + "o chamador deve selecionar só débitos com parcelas pendentes");
            }
            BigDecimal amount = Installments.next(debit.total(), debit.installments(),
                    debit.installmentsDone());
            debitLines.add(new DebitLine(debit.id(), debit.debitTypeId(), debit.debitTypeCode(),
                    debit.installmentsDone() + 1, debit.installments(), amount));
            debitsValue = debitsValue.add(amount);
        }

        // 6. creditos (CreditApplier vigente; teto = proprias + debitos)
        BigDecimal charges = ownSubtotal.add(debitsValue);
        CreditApplier.Application credits = CreditApplier.apply(charges, context.credits());
        BigDecimal creditsValue = credits.totalApplied();

        // 7. retencao (TaxWithholding vigente; base = proprias + debitos - creditos)
        BigDecimal taxBase = charges.subtract(creditsValue);
        List<TaxLine> taxLines = new ArrayList<>();
        BigDecimal taxesValue = Money.ZERO;
        if (context.taxPolicy() != null) {
            taxLines.addAll(TaxWithholding.withhold(taxBase, context.taxPolicy()));
            for (TaxLine line : taxLines) {
                taxesValue = taxesValue.add(line.amount());
            }
            taxBase = taxLines.get(0).base();
        } else {
            taxBase = Money.ZERO;
        }

        // 8. terceiro, POR ULTIMO: so soma ao total; fora de teto e de taxBase
        List<BillChargeLine> thirdPartyLines = new ArrayList<>();
        for (ChargeResolution resolution : sorted) {
            if (isThirdParty(resolution)) {
                thirdPartyLines.add(calculate(context, resolution));
            }
        }
        BigDecimal thirdPartyTotal = Money.ZERO;
        for (BillChargeLine line : thirdPartyLines) {
            thirdPartyTotal = thirdPartyTotal.add(line.amount());
        }

        List<BillChargeLine> lines = new ArrayList<>(ownLines);
        lines.addAll(thirdPartyLines);
        BigDecimal total = charges.subtract(creditsValue).subtract(taxesValue).add(thirdPartyTotal);
        return new BillComposition(lines, water, sewer, waterValue, sewerValue, fixedPartValue,
                ownSubtotal, thirdPartyTotal, debitLines, debitsValue, credits.lines(),
                creditsValue, taxBase, taxLines, taxesValue, total);
    }

    private static BillChargeLine calculate(ChargeCalculationContext context,
                                            ChargeResolution resolution) {
        return calculatorFor(resolution.definition().calculationModel())
                .calculate(context.withResolution(resolution));
    }

    /**
     * A linha designada da TFDI: a linha propria {@code FIXED_PER_UNIT}
     * cujo codigo e o {@code fixedPartChargeCode} declarado pelo
     * chamador (o mesmo criterio com que a
     * {@code FixedPerUnitCalculator} designa a linha que transporta o
     * valor resolvido). Sem codigo declarado, nao ha linha designada.
     */
    static BillChargeLine designatedFixedLine(List<BillChargeLine> ownLines,
                                              String fixedPartChargeCode) {
        if (fixedPartChargeCode == null) {
            return null;
        }
        BillChargeLine designated = null;
        for (BillChargeLine line : ownLines) {
            if (line.model() == CalculationModel.FIXED_PER_UNIT
                    && line.chargeCode().equals(fixedPartChargeCode)) {
                designated = line;
            }
        }
        return designated;
    }

    /**
     * Guarda da correccao R2-01: a linha designada tem de valer
     * EXACTAMENTE (em valor) o {@code fixedPartValue} resolvido;
     * divergindo, a composicao recusa-se — nunca escolhe um dos dois
     * valores em silencio. Com as calculadoras desta camada a linha
     * designada transporta o resolvido por construcao (INV-C2-02),
     * pelo que a guarda e a rede fechada para qualquer outra origem
     * de linha designada.
     */
    static void validateDesignatedFixedLine(BillChargeLine designatedLine,
                                            BigDecimal resolvedFixedPartValue) {
        if (designatedLine.amount().compareTo(resolvedFixedPartValue) != 0) {
            throw new IllegalStateException("TFDI divergente (R2-01): a linha designada "
                    + designatedLine.chargeCode() + " vale " + designatedLine.amount()
                    + " mas o fixedPartValue resolvido e " + resolvedFixedPartValue
                    + " — composicao recusada (sem escolha silenciosa)");
        }
    }

    /**
     * Espelhamento resolucoes/servicos classicos (correccao R2-02,
     * pre-condicao da INV-C2-01 declarada no Javadoc desta classe e do
     * {@link ChargeCalculationContext}): os dois sentidos, para agua e
     * para esgoto. So contam resolucoes PROPRIAS (as de terceiro nao
     * espelham servico classico nenhum).
     */
    static void validateClassicMirror(ChargeCalculationContext context,
                                      List<ChargeResolution> sorted) {
        boolean hasProgressive = false;
        boolean hasDerived = false;
        for (ChargeResolution resolution : sorted) {
            if (isThirdParty(resolution)) {
                continue;
            }
            if (resolution.definition().calculationModel()
                    == CalculationModel.PROGRESSIVE_BY_CONSUMPTION) {
                hasProgressive = true;
            }
            if (resolution.definition().calculationModel()
                    == CalculationModel.DERIVED_FROM_VOLUME) {
                hasDerived = true;
            }
        }
        if (hasProgressive && context.waterConsumption() == null) {
            throw new IllegalArgumentException("espelhamento INV-C2-01 violado (R2-02): "
                    + "resolucao PROGRESSIVE_BY_CONSUMPTION presente mas waterConsumption "
                    + "e null (servico classico de agua ausente) — composicao recusada");
        }
        if (!hasProgressive && context.waterConsumption() != null) {
            throw new IllegalArgumentException("espelhamento INV-C2-01 violado (R2-02): "
                    + "waterConsumption=" + context.waterConsumption() + " presente mas "
                    + "nenhuma resolucao PROGRESSIVE_BY_CONSUMPTION no contexto — a agua "
                    + "nunca compoe 0,00 em silencio");
        }
        if (hasDerived && context.sewer() == null) {
            throw new IllegalArgumentException("espelhamento INV-C2-01 violado (R2-02): "
                    + "resolucao DERIVED_FROM_VOLUME presente mas sewer e null (servico "
                    + "classico de esgoto ausente) — composicao recusada");
        }
        if (!hasDerived && context.sewer() != null) {
            throw new IllegalArgumentException("espelhamento INV-C2-01 violado (R2-02): "
                    + "sewer presente mas nenhuma resolucao DERIVED_FROM_VOLUME no "
                    + "contexto — o esgoto nunca compoe 0,00 em silencio");
        }
    }

    private static boolean isThirdParty(ChargeResolution resolution) {
        // EXTERNAL_VALUE nasce sempre de terceiro (desenho, item 8);
        // os demais modelos seguem o dono declarado na definicao.
        return resolution.definition().calculationModel() == CalculationModel.EXTERNAL_VALUE
                || resolution.definition().isThirdParty();
    }
}
