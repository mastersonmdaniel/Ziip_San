package br.com.ziipsan.commercial.billing.municipality;

import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.BillLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.Closing;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.Consolidated;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.ConsumptionLine;
import br.com.ziipsan.commercial.billing.municipality.MunicipalityClosing.MunicipalityParcel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * MM-09 — Consolidação oficial do perfil integrado (decisão do dono
 * n.º 45; MAPA-DE-PACOTES, pacote MM-09): para um tenant e uma
 * referência, o total do tenant MAIS as parcelas por município, lidos
 * de UMA fonte auditável — o fecho do MM-04
 * ({@link MunicipalityClosing}) sobre as views
 * {@code bills_municipality_cut} / {@code consumption_records_municipality_cut}.
 *
 * <p><b>Fonte única, por construção.</b> Esta classe não consulta
 * tabelas, não tem segunda agregação e não recalcula totais a partir
 * das linhas: {@link #of} chama {@link MunicipalityClosing#closeReference}
 * UMA vez e {@link #wrap} limita-se a embrulhar o {@link Closing}
 * resultante, verificando (guarda, não fonte) que o consolidado do
 * fecho é exactamente a soma das suas parcelas — divergência é erro
 * explícito, nunca um total alternativo. O total oficial é, portanto,
 * o {@code Consolidated} do MM-04: a soma das parcelas, e nada mais.
 *
 * <p><b>Proveniência por número.</b> Cada parcela carrega duas
 * proveniências — uma por fonte-view (contas / consumos) — com
 * município, referência e a derivação consumida (MM-01, por nome). A
 * proveniência do TOTAL é a lista das proveniências das parcelas: o
 * total não tem fonte própria, e é isso que a lista declara.
 *
 * <p><b>Alinhamento com a ED-77 (integração, não recriação).</b> Os
 * indicadores por município da ED-77 vivem sobre
 * {@code municipality_code} (VARCHAR(20), dimensão DECLARADA nas
 * tabelas {@code indicator_sheet_meta}/{@code universalization_goals}/
 * {@code sheet_ibge_households} — metas, fichas e base de domicílios
 * escritas por município). Este pacote não reconstrói indicador
 * nenhum: declara apenas a chave de junção — o código IBGE da parcela
 * ({@link ParcelFigure#ed77MunicipalityCode()}), o mesmo domínio de
 * valores da derivação MM-01 ({@code municipality_ibge}, 7 dígitos).
 * A declaração completa, com as divergências de nome/tipo/derivação
 * registadas como achado (não corrigidas aqui), está no
 * RELATORIO-MM-09, §3.
 *
 * <p><b>Sem persistência.</b> 0 tabelas novas: a consolidação é função
 * determinística e reproduzível das views + fecho, com a proveniência
 * no próprio resultado; congelar uma cópia criaria uma segunda fonte
 * do mesmo facto. Justificação completa no RELATORIO-MM-09, §4.
 *
 * <p>Classe pura, sem JPA/Spring, ao lado do fecho do MM-04 — nenhum
 * ficheiro entregue de outro pacote é editado por este pacote.
 */
public final class OfficialConsolidation {

    /** Fonte-view oficial das contas (MM-04, sobre {@code bills}). */
    public static final String BILLS_SOURCE_VIEW = "bills_municipality_cut";

    /** Fonte-view oficial dos consumos (MM-04, sobre {@code consumption_records}). */
    public static final String CONSUMPTION_SOURCE_VIEW = "consumption_records_municipality_cut";

    /**
     * Derivação consumida, declarada em cada proveniência: a cadeia
     * canónica do MM-01 (D-MM-02), por nome — nunca reimplementada.
     */
    public static final String DERIVATION =
            "MM-01 consumer_unit_municipality "
            + "(consumer_units -> blocks -> commercial_sectors -> localities.municipality_ibge) "
            + "via views MM-04";

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    /**
     * Proveniência de um número do consolidado: de que município, de
     * que referência e de que fonte-view ele veio, e por que derivação.
     * {@code municipalityIbge} nulo = parcela "sem derivação" do MM-04
     * (a proveniência continua completa: município declarado nulo,
     * nunca omitido).
     */
    public record Provenance(String municipalityIbge, LocalDate referenceMonth,
                             String sourceView, String derivation) {
        public Provenance {
            Objects.requireNonNull(referenceMonth, "referenceMonth");
            Objects.requireNonNull(sourceView, "sourceView");
            Objects.requireNonNull(derivation, "derivation");
        }
    }

    /**
     * Uma parcela do consolidado oficial: a parcela do fecho MM-04
     * (valores intactos, sem recálculo) + a proveniência de cada
     * família de números (contas e consumos têm fontes-view distintas).
     */
    public record ParcelFigure(MunicipalityParcel parcel,
                               Provenance billsProvenance,
                               Provenance consumptionProvenance) {
        public ParcelFigure {
            Objects.requireNonNull(parcel, "parcel");
            Objects.requireNonNull(billsProvenance, "billsProvenance");
            Objects.requireNonNull(consumptionProvenance, "consumptionProvenance");
            if (!Objects.equals(parcel.municipalityIbge(), billsProvenance.municipalityIbge())
                    || !Objects.equals(parcel.municipalityIbge(), consumptionProvenance.municipalityIbge())) {
                throw new IllegalArgumentException(
                        "proveniencia de municipio diferente da parcela: " + parcel.municipalityIbge());
            }
        }

        /** IBGE da parcela (nulo na parcela "sem derivação"). */
        public String municipalityIbge() {
            return parcel.municipalityIbge();
        }

        /**
         * Chave de junção declarada com a camada de indicadores da
         * ED-77: o {@code municipality_code} desta parcela é o seu
         * código IBGE. Nulo na parcela "sem derivação" — declarado:
         * linhas sem município derivado não têm indicador ED-77.
         */
        public String ed77MunicipalityCode() {
            return parcel.municipalityIbge();
        }
    }

    /**
     * O consolidado oficial de um tenant numa referência: parcelas com
     * proveniência + o total do MM-04. {@code totalProvenance} é a
     * lista (ordenada como as parcelas; contas antes de consumos em
     * cada parcela) das proveniências que SOMADAS produzem o total —
     * o total não tem proveniência própria além desta.
     */
    public record Official(UUID tenantId, LocalDate referenceMonth,
                           List<ParcelFigure> parcels, Consolidated total,
                           List<Provenance> totalProvenance) {
        public Official {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(referenceMonth, "referenceMonth");
            parcels = List.copyOf(parcels);
            Objects.requireNonNull(total, "total");
            totalProvenance = List.copyOf(totalProvenance);
        }

        /** Parcela oficial de um município (IBGE), se existir neste consolidado. */
        public Optional<ParcelFigure> parcelFor(String municipalityIbge) {
            Objects.requireNonNull(municipalityIbge, "municipalityIbge");
            return parcels.stream()
                    .filter(p -> municipalityIbge.equals(p.municipalityIbge())).findFirst();
        }

        /** Parcela "sem derivação" (IBGE nulo), se existir. */
        public Optional<ParcelFigure> unassignedParcel() {
            return parcels.stream().filter(p -> p.municipalityIbge() == null).findFirst();
        }

        /**
         * Alinhamento declarado com a ED-77: verdadeiro quando existe
         * parcela cujo código IBGE é exactamente o
         * {@code municipality_code} dado — a junção entre o consolidado
         * oficial e os indicadores/fichas/metas da ED-77 faz-se por
         * igualdade deste código, e por nenhum outro mecanismo.
         */
        public boolean alignsWithEd77(String ed77MunicipalityCode) {
            Objects.requireNonNull(ed77MunicipalityCode, "ed77MunicipalityCode");
            return parcels.stream()
                    .anyMatch(p -> ed77MunicipalityCode.equals(p.ed77MunicipalityCode()));
        }

        /** Verdadeiro no fecho vazio: zero declarado (sem parcelas, total zero). */
        public boolean isDeclaredZero() {
            return parcels.isEmpty()
                    && total.billCount() == 0 && total.consumptionRecordCount() == 0
                    && total.billsTotal().compareTo(ZERO_MONEY) == 0;
        }
    }

    private OfficialConsolidation() {
    }

    /**
     * Produz o consolidado oficial de (tenant, referência) a partir
     * das linhas tal como as views do MM-04 as devolvem: UMA chamada
     * ao fecho do MM-04 — é a única agregação que existe neste pacote.
     */
    public static Official of(UUID tenantId, LocalDate referenceMonth,
                              Collection<BillLine> bills,
                              Collection<ConsumptionLine> consumptions) {
        Objects.requireNonNull(referenceMonth, "referenceMonth");
        return wrap(MunicipalityClosing.closeReference(tenantId, referenceMonth, bills, consumptions));
    }

    /**
     * Embrulha um fecho do MM-04 já produzido (pela mesma e única via)
     * como consolidado oficial. O fecho tem de ser de UMA referência:
     * sem referência não há proveniência completa, e a recusa é
     * explícita. Guarda de fonte única: o total do fecho é confrontado
     * com a soma das parcelas do PRÓPRIO fecho; divergência (que o
     * desenho do MM-04 torna impossível) é erro, nunca correção.
     */
    public static Official wrap(Closing closing) {
        Objects.requireNonNull(closing, "closing");
        LocalDate reference = closing.referenceMonth();
        if (reference == null) {
            throw new IllegalArgumentException(
                    "consolidado oficial exige fecho por referencia (closeReference do MM-04): "
                    + "sem referencia nao ha proveniencia completa");
        }
        List<ParcelFigure> figures = new ArrayList<>();
        List<Provenance> totalProvenance = new ArrayList<>();
        for (MunicipalityParcel parcel : closing.parcels()) {
            Provenance billsProv = new Provenance(
                    parcel.municipalityIbge(), reference, BILLS_SOURCE_VIEW, DERIVATION);
            Provenance consProv = new Provenance(
                    parcel.municipalityIbge(), reference, CONSUMPTION_SOURCE_VIEW, DERIVATION);
            figures.add(new ParcelFigure(parcel, billsProv, consProv));
            totalProvenance.add(billsProv);
            totalProvenance.add(consProv);
        }
        requireTotalIsSumOfParcels(closing);
        return new Official(closing.tenantId(), reference, figures,
                closing.consolidated(), totalProvenance);
    }

    private static void requireTotalIsSumOfParcels(Closing closing) {
        long billCount = 0;
        long recordCount = 0;
        long billed = 0;
        long calculated = 0;
        BigDecimal total = ZERO_MONEY;
        BigDecimal water = ZERO_MONEY;
        BigDecimal sewer = ZERO_MONEY;
        for (MunicipalityParcel p : closing.parcels()) {
            billCount += p.billCount();
            recordCount += p.consumptionRecordCount();
            billed += p.billedConsumption();
            calculated += p.calculatedConsumption();
            total = total.add(p.billsTotal());
            water = water.add(p.waterValue());
            sewer = sewer.add(p.sewerValue());
        }
        Consolidated c = closing.consolidated();
        if (billCount != c.billCount() || recordCount != c.consumptionRecordCount()
                || billed != c.billedConsumption() || calculated != c.calculatedConsumption()
                || total.compareTo(c.billsTotal()) != 0
                || water.compareTo(c.waterValue()) != 0
                || sewer.compareTo(c.sewerValue()) != 0) {
            throw new IllegalStateException(
                    "consolidado do fecho diverge da soma das suas parcelas: "
                    + "fonte unica violada, nenhum total alternativo e produzido");
        }
    }
}
