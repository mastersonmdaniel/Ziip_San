package br.com.ziipsan.commercial.billing.municipality;

import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.BillLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.Closing;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.ConsumptionLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.MunicipalityParcel;
import br.com.ziipsan.commercial.billing.municipality.OfficialConsolidation.Provenance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * MM-09 — Selecção por município dos relatórios sobre
 * {@code bills}/{@code consumption_records}: "gerar o relatório só do
 * município X" = correr sobre a parcela X do recorte do MM-04, e sobre
 * mais nada.
 *
 * <p><b>Por que razão um serviço NOVO ao lado, e não uma extensão do
 * {@code ReportRegistry} da P3.22</b> (evidência completa no
 * RELATORIO-MM-09, §2, na leitura corrigida pela ERRATA-MM-09 §2 —
 * bastam os bloqueadores 2 e 3, cada um só por si, com a guarda
 * anti-duplicado do {@code registerExternal}; conferidos nos
 * ficheiros entregues da P3.22):
 * <ol>
 *   <li>{@code ReportRegistry} é {@code public final class}, sem
 *       interface — não há subclasse possível. Não vale como
 *       ausência de costura de extensão: o registo tem costura de
 *       extensão desenhada ({@code registerExternal} +
 *       {@code ExternalReportRegistrar} +
 *       {@code ExternalDefinitionStore}), e registar uma definição
 *       externa nova não exige editar o registo (ERRATA-MM-09 §2);</li>
 *   <li>{@code ReportDefinition} recusa, no construtor compacto,
 *       qualquer {@code viewName} fora do padrão {@code rpt_*} — as
 *       views do MM-04 ({@code bills_municipality_cut},
 *       {@code consumption_records_municipality_cut}) são recusadas
 *       também pela via externa ({@code ExternalReportRegistrar}
 *       delega na mesma validação);</li>
 *   <li>as definições de fábrica sobre contas/consumos não declaram
 *       coluna nem filtro {@code municipality_ibge}, e o
 *       {@code ReportQueryBuilder} recusa filtro não declarado —
 *       declarar o filtro exigiria editar {@code ReportRegistry} e
 *       as views da V15_1, ambos ficheiros entregues da P3.22.</li>
 * </ol>
 * Editar ficheiros entregues está fora do âmbito deste pacote (brief);
 * a fiação da selecção DENTRO do registo/execução da P3.22 fica como
 * pendência nomeada <b>P-MM09-02</b>. Este serviço entrega a selecção
 * em si, sobre a mesma e única fonte (views/fecho do MM-04), pronta a
 * ser chamada pela fiação futura.
 *
 * <p><b>Âmbito declarado da selecção</b> (da V15_1 da P3.22, conferida
 * linha a linha): relatórios cuja view lê {@code bills} directamente —
 * {@link #BILLS_BASED_REPORTS}; via {@code bill_items} com JOIN a
 * {@code bills} — {@link #BILL_ITEMS_BASED_REPORTS}; cuja view lê
 * {@code consumption_records} — {@link #CONSUMPTION_BASED_REPORTS}.
 * Para todos eles, a parcela municipal das linhas subjacentes é a das
 * views do MM-04 (a UC da conta/consumo é a mesma), logo a selecção
 * por parcela é a semântica correcta de "só do município X". Relatórios
 * sobre outras fontes (pagamentos, protocolos, cadastro puro) estão
 * FORA desta selecção — não correm sobre {@code bills}/
 * {@code consumption_records}.
 *
 * <p><b>Garantia da selecção.</b> {@link #selectFromLines} filtra as
 * linhas pelo IBGE pedido ANTES de fechar (o fecho do MM-04 só vê
 * linhas da parcela X), e {@link #select} extrai a parcela X de um
 * fecho já produzido: as duas vias devolvem a mesma parcela, e a
 * selecção declara os municípios excluídos. Município ausente no
 * fecho = parcela zero DECLARADA (não erro, não ausência silenciosa).
 */
public final class MunicipalityReportSelection {

    /** Relatórios da P3.22 cuja view da V15_1 lê {@code bills} directamente. */
    public static final List<String> BILLS_BASED_REPORTS =
            List.of("RPT-006", "RPT-009", "RPT-010", "RPT-011", "RPT-017", "RPT-019");

    /** Relatórios da P3.22 cuja view lê {@code bill_items} com JOIN a {@code bills}. */
    public static final List<String> BILL_ITEMS_BASED_REPORTS =
            List.of("RPT-007", "RPT-016");

    /** Relatórios da P3.22 cuja view da V15_1 lê {@code consumption_records}. */
    public static final List<String> CONSUMPTION_BASED_REPORTS =
            List.of("RPT-012", "RPT-014");

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    /**
     * Resultado da selecção: a parcela do município pedido (valores do
     * fecho do MM-04, intactos), a sua proveniência nas duas
     * fontes-view e a lista ordenada dos municípios presentes no fecho
     * que ficaram de fora — a selecção é auditável também pelo que
     * exclui. A parcela "sem derivação" (IBGE nulo) NUNCA é
     * seleccionável por esta via (não é um município) nem é listada
     * como município excluído; continua visível no consolidado oficial.
     */
    public record Selection(UUID tenantId, LocalDate referenceMonth, String municipalityIbge,
                            MunicipalityParcel parcel,
                            Provenance billsProvenance, Provenance consumptionProvenance,
                            List<String> excludedMunicipalities) {
        public Selection {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(referenceMonth, "referenceMonth");
            Objects.requireNonNull(municipalityIbge, "municipalityIbge");
            Objects.requireNonNull(parcel, "parcel");
            Objects.requireNonNull(billsProvenance, "billsProvenance");
            Objects.requireNonNull(consumptionProvenance, "consumptionProvenance");
            excludedMunicipalities = List.copyOf(excludedMunicipalities);
            if (!municipalityIbge.equals(parcel.municipalityIbge())) {
                throw new IllegalArgumentException(
                        "parcela da seleccao nao e a do municipio pedido: " + municipalityIbge);
            }
        }

        /** Verdadeiro quando o município não tem linhas no fecho: zero declarado. */
        public boolean isDeclaredZero() {
            return parcel.billCount() == 0 && parcel.consumptionRecordCount() == 0
                    && parcel.billsTotal().compareTo(ZERO_MONEY) == 0;
        }
    }

    private MunicipalityReportSelection() {
    }

    /**
     * Selecção sobre um fecho do MM-04 já produzido (a via do
     * consolidado oficial): devolve exactamente a parcela do município
     * pedido nesse fecho — nem uma linha a mais, porque a parcela FOI
     * construída só das linhas desse município pelo agrupamento único
     * do MM-04.
     */
    public static Selection select(Closing closing, String municipalityIbge) {
        Objects.requireNonNull(closing, "closing");
        Objects.requireNonNull(municipalityIbge, "municipalityIbge");
        if (municipalityIbge.isBlank()) {
            throw new IllegalArgumentException("seleccao exige o IBGE do municipio");
        }
        if (closing.referenceMonth() == null) {
            throw new IllegalArgumentException(
                    "seleccao exige fecho por referencia (closeReference do MM-04)");
        }
        MunicipalityParcel parcel = closing.parcelFor(municipalityIbge)
                .orElseGet(() -> zeroParcel(municipalityIbge));
        return build(closing.tenantId(), closing.referenceMonth(), municipalityIbge,
                parcel, excludedOf(closing, municipalityIbge));
    }

    /**
     * Selecção a partir das linhas das views do MM-04: filtra pelo
     * IBGE pedido ANTES de fechar — o fecho só recebe linhas da
     * parcela X, logo o seu consolidado É a parcela X (provado nos
     * testes contra a via {@link #select}). Os municípios excluídos
     * são lidos do conjunto completo, para a declaração ser completa.
     */
    public static Selection selectFromLines(UUID tenantId, LocalDate referenceMonth,
                                            String municipalityIbge,
                                            Collection<BillLine> bills,
                                            Collection<ConsumptionLine> consumptions) {
        Objects.requireNonNull(municipalityIbge, "municipalityIbge");
        if (municipalityIbge.isBlank()) {
            throw new IllegalArgumentException("seleccao exige o IBGE do municipio");
        }
        List<BillLine> onlyBills = bills.stream()
                .filter(b -> municipalityIbge.equals(b.municipalityIbge())).toList();
        List<ConsumptionLine> onlyCons = consumptions.stream()
                .filter(c -> municipalityIbge.equals(c.municipalityIbge())).toList();
        Closing filtered = MunicipalityClosing.closeReference(
                tenantId, referenceMonth, onlyBills, onlyCons);
        MunicipalityParcel parcel = filtered.parcelFor(municipalityIbge)
                .orElseGet(() -> zeroParcel(municipalityIbge));
        // Guarda de defesa em profundidade (R9-02): inalcançável por
        // construção — as linhas foram filtradas pelo IBGE pedido antes
        // de fechar (:167-170) e o closeReference do MM-04 agrupa as
        // parcelas só pelos IBGEs das linhas recebidas (records
        // imutáveis), pelo que o fecho filtrado só pode conter a
        // parcela pedida. Mantém-se como falha fechada explícita
        // contra regressão futura do filtro ou do agrupamento;
        // nenhum teste a exercita, por desenho.
        for (MunicipalityParcel p : filtered.parcels()) {
            if (!municipalityIbge.equals(p.municipalityIbge())) {
                throw new IllegalStateException(
                        "fecho filtrado contem parcela alheia a seleccao: " + p.municipalityIbge());
            }
        }
        Set<String> present = new TreeSet<>();
        bills.forEach(b -> { if (b.municipalityIbge() != null) present.add(b.municipalityIbge()); });
        consumptions.forEach(c -> { if (c.municipalityIbge() != null) present.add(c.municipalityIbge()); });
        present.remove(municipalityIbge);
        return build(tenantId, referenceMonth, municipalityIbge, parcel, List.copyOf(present));
    }

    /** Todos os códigos de relatório a que esta selecção se aplica (ordenados). */
    public static List<String> selectableReportCodes() {
        Set<String> all = new TreeSet<>();
        all.addAll(BILLS_BASED_REPORTS);
        all.addAll(BILL_ITEMS_BASED_REPORTS);
        all.addAll(CONSUMPTION_BASED_REPORTS);
        return List.copyOf(all);
    }

    private static Selection build(UUID tenantId, LocalDate referenceMonth, String municipalityIbge,
                                   MunicipalityParcel parcel, List<String> excluded) {
        return new Selection(tenantId, referenceMonth, municipalityIbge, parcel,
                new Provenance(municipalityIbge, referenceMonth,
                        OfficialConsolidation.BILLS_SOURCE_VIEW, OfficialConsolidation.DERIVATION),
                new Provenance(municipalityIbge, referenceMonth,
                        OfficialConsolidation.CONSUMPTION_SOURCE_VIEW, OfficialConsolidation.DERIVATION),
                excluded);
    }

    private static MunicipalityParcel zeroParcel(String municipalityIbge) {
        return new MunicipalityParcel(municipalityIbge, 0, ZERO_MONEY, ZERO_MONEY, ZERO_MONEY,
                0, 0, 0);
    }

    private static List<String> excludedOf(Closing closing, String municipalityIbge) {
        List<String> excluded = new ArrayList<>();
        for (MunicipalityParcel p : closing.parcels()) {
            if (p.municipalityIbge() != null && !p.municipalityIbge().equals(municipalityIbge)) {
                excluded.add(p.municipalityIbge());
            }
        }
        return List.copyOf(excluded);
    }
}
