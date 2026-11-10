package br.com.ziipsan.commercial.billing.composition;

import br.com.ziipsan.commercial.billing.bill.BillInput;
import br.com.ziipsan.commercial.billing.bill.CreditReceivable;
import br.com.ziipsan.commercial.billing.bill.DebitReceivable;
import br.com.ziipsan.commercial.billing.bill.TaxWithholdingPolicy;
import br.com.ziipsan.commercial.billing.servicecatalog.ServicePriceItem;
import br.com.ziipsan.commercial.billing.tariff.TariffGroup;
import br.com.ziipsan.commercial.billing.tariff.TariffSchedule;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Tudo o que a composicao precisa, PRE-RESOLVIDO pelo chamador
 * (desenho da Camada 0, secao 2.1 item 2): o mesmo contrato do
 * {@code BillInput} vigente — o maestro nao le banco, nao le registry
 * e nao le o catalogo; consome as resolucoes que o chamador obteve do
 * {@code ChargeResolver} da Camada 1 (consumo SO pelo Resolver,
 * obrigacao R1-02).
 *
 * <p>Conteudo:
 * <ul>
 *   <li><b>classico</b> (o contrato do {@code BillInput} da entrega da
 *       P2.5, fontes corrigidas pela errata R-03): schedule ja
 *       SELECCIONADO por municipio pelo chamador (seleccao previa
 *       D-03.3 — a montagem e do chamador; o maestro so verifica a
 *       coerencia), periodo, grupos/economias, consumo de agua, esgoto,
 *       debitos, creditos, politica de retencao e o
 *       {@code fixedPartValue} JA RESOLVIDO pelo chamador (a resolucao
 *       continua na {@code FixedPartCharge} vigente, Res. ARESC
 *       105/2018 — regra de fronteira (iv));</li>
 *   <li><b>resolvidos do catalogo</b>: {@code resolvedCharges}, a lista
 *       devolvida pelo {@code ChargeResolver.resolve(unitRef,
 *       municipalityIbge, competence)} da Camada 1;</li>
 *   <li>{@code municipalityIbge} da UC, DERIVADO pelo chamador da cadeia
 *       territorial (PM-05); {@code null} = sem municipio derivado, so
 *       escopo "empresa". <b>Regra de fronteira (i):</b> o maestro
 *       VALIDA o formato (exactamente 7 digitos ou {@code null}) e
 *       FALHA FECHADO se malformado — a validacao acontece aqui, na
 *       construcao do contexto, e de novo na composicao;</li>
 *   <li>{@code tariffVersionScopes}: mapa pre-resolvido
 *       id da versao de tarifa -&gt; IBGE do escopo dela (valor
 *       {@code null} = escopo "empresa"), usado na coerencia D-03;</li>
 *   <li><b>entradas por modelo</b>, por codigo de cobranca:
 *       {@code catalogQuantities} (quantidades do CATALOG_PRICE),
 *       {@code priceItems} (item da ED-34 resolvido pelo
 *       {@code priceItemCode}), {@code percentBases} (base do
 *       PERCENT_OF_BASE), {@code percentRoundingModes} (modo de
 *       arredondamento EXIGIDO por cobranca do PERCENT_OF_BASE —
 *       regra de fronteira (iii): modo ausente = excecao, nunca
 *       implicito), {@code fixedUnits} (unidades/economias das
 *       cobrancas FIXED_PER_UNIT genericas) e a porta
 *       {@code externalValueProvider} (valor externo do
 *       EXTERNAL_VALUE; a implementacao sobre a ED-25 e da Camada 3);</li>
 *   <li>{@code fixedPartChargeCode}: o codigo, declarado pelo chamador,
 *       da cobranca de catalogo que representa a parcela fixa classica
 *       (TFDI) — e essa linha que transporta o {@code fixedPartValue}
 *       resolvido, nem mais nem menos (INV-C2-02);</li>
 *   <li>{@code parameterVersionIds}: ids herdados do mecanismo vigente
 *       de fotografia de parametros, carregados para o snapshot;</li>
 *   <li>{@code resolution}: a resolucao CORRENTE, posta pelo maestro
 *       quando despacha para uma calculadora ({@link #withResolution});
 *       fora desse despacho e {@code null}.</li>
 * </ul>
 *
 * <p>Coleccoes nulas nascem vazias e todas as coleccoes sao copiadas
 * para formas imutaveis na construcao.
 *
 * <p><b>Pre-condicao de espelhamento da INV-C2-01 (correccao
 * R2-02):</b> a equivalencia classica desta camada vale para
 * contextos em que as resolucoes ESPELHAM os servicos classicos da
 * entrada — agua faturada ({@code waterConsumption != null})
 * corresponde a uma resolucao {@code PROGRESSIVE_BY_CONSUMPTION} em
 * {@code resolvedCharges}, e esgoto ({@code sewer != null})
 * corresponde a uma resolucao {@code DERIVED_FROM_VOLUME}. O
 * {@code BillComposer} verifica os dois sentidos no inicio da
 * composicao e falha fechado na divergencia: resolucao sem servico
 * classico, ou servico classico sem resolucao, e excecao — nunca
 * agua/esgoto 0,00 em silencio.
 */
public record ChargeCalculationContext(String unitRef, String billReference,
                                       String municipalityIbge, LocalDate competence,
                                       TariffSchedule schedule, LocalDate periodFrom,
                                       LocalDate periodTo, List<TariffGroup> groups,
                                       Integer waterConsumption, BillInput.SewerBilling sewer,
                                       List<DebitReceivable> debits,
                                       List<CreditReceivable> credits,
                                       TaxWithholdingPolicy taxPolicy,
                                       BigDecimal fixedPartValue, String fixedPartChargeCode,
                                       List<ChargeResolution> resolvedCharges,
                                       Map<UUID, String> tariffVersionScopes,
                                       Map<String, BigDecimal> catalogQuantities,
                                       Map<String, ServicePriceItem> priceItems,
                                       Map<String, BigDecimal> percentBases,
                                       Map<String, RoundingMode> percentRoundingModes,
                                       Map<String, Integer> fixedUnits,
                                       ExternalValueProvider externalValueProvider,
                                       List<UUID> parameterVersionIds,
                                       ChargeResolution resolution) {

    /**
     * Porta do valor externo (EXTERNAL_VALUE): devolve o valor devido
     * da cobranca resolvida, ja resolvido da fonte externa (convenio).
     * Nesta camada so vive o CONTRATO; a implementacao sobre os
     * contratos da ED-25 e da Camada 3 ({@code CobillExternalValueProvider}).
     */
    public interface ExternalValueProvider {
        BigDecimal resolveValue(ChargeResolution resolution, ChargeCalculationContext context);
    }

    public ChargeCalculationContext {
        Objects.requireNonNull(unitRef, "referencia da unidade e obrigatoria (opaca)");
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        // Regra de fronteira (i): formato do municipalityIbge validado
        // pelo maestro, com falha fechada se malformado (decisao sobre
        // a R1-04 da Camada 1: a C1 ja valida nas definicoes; aqui
        // valida-se o municipio DERIVADO da UC que o chamador entrega).
        validateMunicipalityFormat(municipalityIbge, "municipalityIbge da UC");
        debits = debits == null ? List.of() : List.copyOf(debits);
        credits = credits == null ? List.of() : List.copyOf(credits);
        groups = groups == null ? List.of() : List.copyOf(groups);
        fixedPartValue = fixedPartValue == null ? Money.ZERO : fixedPartValue;
        if (fixedPartValue.signum() < 0) {
            throw new IllegalArgumentException("Parcela fixa (TFDI) negativa: " + fixedPartValue);
        }
        resolvedCharges = resolvedCharges == null ? List.of() : List.copyOf(resolvedCharges);
        // Cuidado: os VALORES deste mapa podem ser null (escopo
        // "empresa"), por isso a copia e um LinkedHashMap imutavel —
        // Map.copyOf recusaria os valores nulos.
        tariffVersionScopes = tariffVersionScopes == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(tariffVersionScopes));
        for (Map.Entry<UUID, String> scope : tariffVersionScopes.entrySet()) {
            Objects.requireNonNull(scope.getKey(), "id de versao de tarifa do mapa de escopos");
            validateMunicipalityFormat(scope.getValue(),
                    "escopo da versao de tarifa " + scope.getKey());
        }
        catalogQuantities = catalogQuantities == null ? Map.of() : Map.copyOf(catalogQuantities);
        priceItems = priceItems == null ? Map.of() : Map.copyOf(priceItems);
        percentBases = percentBases == null ? Map.of() : Map.copyOf(percentBases);
        percentRoundingModes = percentRoundingModes == null
                ? Map.of() : Map.copyOf(percentRoundingModes);
        fixedUnits = fixedUnits == null ? Map.of() : Map.copyOf(fixedUnits);
        parameterVersionIds = parameterVersionIds == null
                ? List.of() : List.copyOf(parameterVersionIds);
    }

    /** Valida o formato IBGE: exactamente 7 digitos numericos, ou {@code null} (escopo empresa). */
    public static void validateMunicipalityFormat(String municipalityIbge, String field) {
        if (municipalityIbge != null && !municipalityIbge.matches("[0-9]{7}")) {
            throw new IllegalArgumentException(field + " malformado: codigo IBGE tem exactamente "
                    + "7 digitos numericos (ou null = escopo empresa): " + municipalityIbge);
        }
    }

    /** Copia deste contexto com a resolucao corrente posta (despacho do maestro/calculadora). */
    public ChargeCalculationContext withResolution(ChargeResolution current) {
        return new ChargeCalculationContext(unitRef, billReference, municipalityIbge, competence,
                schedule, periodFrom, periodTo, groups, waterConsumption, sewer, debits, credits,
                taxPolicy, fixedPartValue, fixedPartChargeCode, resolvedCharges, tariffVersionScopes,
                catalogQuantities, priceItems, percentBases, percentRoundingModes, fixedUnits,
                externalValueProvider, parameterVersionIds, current);
    }

    /**
     * A resolucao corrente, exigida pela calculadora do modelo dado.
     *
     * @throws IllegalArgumentException se nao houver resolucao corrente
     *         ou se o modelo dela divergir do esperado
     */
    public ChargeResolution requireResolution(CalculationModel expected) {
        if (resolution == null) {
            throw new IllegalArgumentException(
                    "calculadora de " + expected + " sem resolucao corrente no contexto");
        }
        CalculationModel actual = resolution.definition().calculationModel();
        if (actual != expected) {
            throw new IllegalArgumentException("calculadora de " + expected
                    + " recebeu resolucao de modelo " + actual + ": " + resolution.definition().code());
        }
        if (resolution.version().calculationModel() != expected) {
            throw new IllegalArgumentException("versao da cobranca " + resolution.definition().code()
                    + " declara modelo " + resolution.version().calculationModel()
                    + ", divergente do modelo da definicao " + expected);
        }
        return resolution;
    }

    /**
     * Coerencia tarifa x cobranca (adendo D-03.2, regra de fronteira
     * (ii)): para cobrancas PROGRESSIVE_BY_CONSUMPTION /
     * DERIVED_FROM_VOLUME, o escopo da versao de tarifa referenciada
     * tem de ser IGUAL ao {@code municipalityIbge} da definicao
     * vencedora OU {@code null} (escopo "empresa"). Cruzamento
     * municipio A x tarifa do municipio B = excecao fechada, com o par
     * (cobranca, tarifa) identificado. O escopo vem do mapa
     * pre-resolvido {@link #tariffVersionScopes()}: versao de tarifa
     * referenciada SEM entrada no mapa = escopo nao pre-resolvido =
     * falha fechada (nunca se presume "empresa").
     */
    public void validateTariffCoherence(ChargeResolution res) {
        CalculationModel model = res.definition().calculationModel();
        if (model != CalculationModel.PROGRESSIVE_BY_CONSUMPTION
                && model != CalculationModel.DERIVED_FROM_VOLUME) {
            return;
        }
        UUID tariffVersionId = res.version().tariffVersionId();
        if (tariffVersionId == null) {
            return;
        }
        if (!tariffVersionScopes.containsKey(tariffVersionId)) {
            throw new IllegalStateException("coerencia D-03 nao verificavel: escopo da versao de "
                    + "tarifa " + tariffVersionId + " nao foi pre-resolvido pelo chamador "
                    + "(cobranca " + res.definition().code() + ")");
        }
        String tariffScope = tariffVersionScopes.get(tariffVersionId);
        String definitionScope = res.definition().municipalityIbge();
        if (tariffScope != null && !tariffScope.equals(definitionScope)) {
            throw new IllegalStateException("coerencia D-03 violada: cobranca "
                    + res.definition().code() + " (escopo " + scopeLabel(definitionScope)
                    + ") referencia versao de tarifa " + tariffVersionId + " de escopo "
                    + scopeLabel(tariffScope) + " — cruzamento de municipios recusado");
        }
    }

    private static String scopeLabel(String scope) {
        return scope == null ? "empresa" : "municipio " + scope;
    }

    /** O {@code BillInput} classico equivalente (o contrato vigente, sem catalogo). */
    public BillInput toBillInput() {
        return new BillInput(schedule, periodFrom, periodTo, groups, waterConsumption, sewer,
                debits, credits, taxPolicy, fixedPartValue);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Construtor fluido do contexto (o record tem muitos componentes pre-resolvidos). */
    public static final class Builder {
        private String unitRef;
        private String billReference;
        private String municipalityIbge;
        private LocalDate competence;
        private TariffSchedule schedule;
        private LocalDate periodFrom;
        private LocalDate periodTo;
        private List<TariffGroup> groups;
        private Integer waterConsumption;
        private BillInput.SewerBilling sewer;
        private List<DebitReceivable> debits;
        private List<CreditReceivable> credits;
        private TaxWithholdingPolicy taxPolicy;
        private BigDecimal fixedPartValue;
        private String fixedPartChargeCode;
        private List<ChargeResolution> resolvedCharges;
        private Map<UUID, String> tariffVersionScopes;
        private Map<String, BigDecimal> catalogQuantities;
        private Map<String, ServicePriceItem> priceItems;
        private Map<String, BigDecimal> percentBases;
        private Map<String, RoundingMode> percentRoundingModes;
        private Map<String, Integer> fixedUnits;
        private ExternalValueProvider externalValueProvider;
        private List<UUID> parameterVersionIds;
        private ChargeResolution resolution;

        public Builder unitRef(String v) { this.unitRef = v; return this; }
        public Builder billReference(String v) { this.billReference = v; return this; }
        public Builder municipalityIbge(String v) { this.municipalityIbge = v; return this; }
        public Builder competence(LocalDate v) { this.competence = v; return this; }
        public Builder schedule(TariffSchedule v) { this.schedule = v; return this; }
        public Builder periodFrom(LocalDate v) { this.periodFrom = v; return this; }
        public Builder periodTo(LocalDate v) { this.periodTo = v; return this; }
        public Builder groups(List<TariffGroup> v) { this.groups = v; return this; }
        public Builder waterConsumption(Integer v) { this.waterConsumption = v; return this; }
        public Builder sewer(BillInput.SewerBilling v) { this.sewer = v; return this; }
        public Builder debits(List<DebitReceivable> v) { this.debits = v; return this; }
        public Builder credits(List<CreditReceivable> v) { this.credits = v; return this; }
        public Builder taxPolicy(TaxWithholdingPolicy v) { this.taxPolicy = v; return this; }
        public Builder fixedPartValue(BigDecimal v) { this.fixedPartValue = v; return this; }
        public Builder fixedPartChargeCode(String v) { this.fixedPartChargeCode = v; return this; }
        public Builder resolvedCharges(List<ChargeResolution> v) { this.resolvedCharges = v; return this; }
        public Builder tariffVersionScopes(Map<UUID, String> v) { this.tariffVersionScopes = v; return this; }
        public Builder catalogQuantities(Map<String, BigDecimal> v) { this.catalogQuantities = v; return this; }
        public Builder priceItems(Map<String, ServicePriceItem> v) { this.priceItems = v; return this; }
        public Builder percentBases(Map<String, BigDecimal> v) { this.percentBases = v; return this; }
        public Builder percentRoundingModes(Map<String, RoundingMode> v) { this.percentRoundingModes = v; return this; }
        public Builder fixedUnits(Map<String, Integer> v) { this.fixedUnits = v; return this; }
        public Builder externalValueProvider(ExternalValueProvider v) { this.externalValueProvider = v; return this; }
        public Builder parameterVersionIds(List<UUID> v) { this.parameterVersionIds = v; return this; }
        public Builder resolution(ChargeResolution v) { this.resolution = v; return this; }

        public ChargeCalculationContext build() {
            return new ChargeCalculationContext(unitRef, billReference, municipalityIbge, competence,
                    schedule, periodFrom, periodTo, groups, waterConsumption, sewer, debits, credits,
                    taxPolicy, fixedPartValue, fixedPartChargeCode, resolvedCharges,
                    tariffVersionScopes, catalogQuantities, priceItems, percentBases,
                    percentRoundingModes, fixedUnits, externalValueProvider, parameterVersionIds,
                    resolution);
        }
    }
}
