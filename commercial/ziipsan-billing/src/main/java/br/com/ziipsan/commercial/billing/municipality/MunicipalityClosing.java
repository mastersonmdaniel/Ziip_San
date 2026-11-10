package br.com.ziipsan.commercial.billing.municipality;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * MM-04 — Fechamento de ciclo por município + consolidado do tenant
 * (P-BF-08; decisão do dono n.º 45; decisão de coordenação D-MM-03).
 *
 * <p>Classe NOVA, ao lado do fechamento vigente do ciclo
 * ({@code CycleService}/{@code billing_references}, P2.5 sobre a V4_3 —
 * NENHUM ficheiro vigente é editado por este pacote). O {@code CycleService}
 * fecha a referência do tenant inteiro; este fecho é a leitura oficial
 * da MESMA referência recortada por município, sobre as views do MM-04
 * ({@code bills_municipality_cut} / {@code consumption_records_municipality_cut},
 * que derivam o município da UC pela view {@code consumer_unit_municipality}
 * do MM-01 — consumida por nome, nunca reimplementada).
 *
 * <p><b>O consolidado é, por construção, a soma das parcelas.</b> Não
 * existe segunda fonte nem segunda consulta para o total do tenant:
 * {@link #close} agrupa as linhas por município uma única vez e o
 * consolidado calcula-se somando as parcelas resultantes
 * ({@code sumParcels}). É esta a invariante de regressão do bloco
 * multimunicípio: os totais do tenant não mudam — o consolidado de hoje
 * é o caso particular em que só há uma parcela (ou em que se somam todas).
 *
 * <p>Linhas sem município derivável ({@code municipalityIbge == null} —
 * UC sem derivação na cadeia do MM-01) NÃO são descartadas: formam a
 * parcela "sem derivação" (IBGE nulo, sempre a última da lista) e entram
 * no consolidado como qualquer outra. Descartá-las mudaria o total do
 * tenant em silêncio — exactamente o que a invariante proíbe.
 *
 * <p>Classe pura, sem JPA/Spring: opera sobre as linhas tal como as
 * views as devolvem (records abaixo), como a derivação do MM-01.
 * Dinheiro em {@link BigDecimal} com a escala do módulo (2 casas —
 * {@code bills.total_value} é {@code NUMERIC(14,2)} na V4_1); somas de
 * valores com escala 2 mantêm escala 2.
 */
public final class MunicipalityClosing {

    /**
     * Consulta oficial do fecho de contas por município (sobre a view do
     * MM-04; o chamador liga tenant e referência). É a fonte das linhas
     * {@link BillLine} deste fecho — não há outra consulta de contas
     * para o recorte.
     */
    public static final String BILLS_BY_MUNICIPALITY_SQL =
            "SELECT municipality_ibge, count(*) AS bill_count, sum(total_value) AS total_value, "
            + "sum(water_value) AS water_value, sum(sewer_value) AS sewer_value "
            + "FROM bills_municipality_cut "
            + "WHERE tenant_id = ? AND reference_month = ? GROUP BY municipality_ibge";

    /**
     * Consulta oficial do fecho de consumos por município (sobre a view
     * do MM-04; o chamador liga tenant e referência). Fonte das linhas
     * {@link ConsumptionLine}.
     */
    public static final String CONSUMPTION_BY_MUNICIPALITY_SQL =
            "SELECT municipality_ibge, count(*) AS record_count, "
            + "sum(billed_consumption) AS billed_consumption, "
            + "sum(calculated_consumption) AS calculated_consumption "
            + "FROM consumption_records_municipality_cut "
            + "WHERE tenant_id = ? AND reference = ? GROUP BY municipality_ibge";

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    /**
     * Linha de conta tal como {@code bills_municipality_cut} a devolve
     * (colunas do fecho; a view expõe a linha inteira de {@code bills},
     * V4_1, mais o IBGE). {@code municipalityIbge} nulo = sem derivação.
     */
    public record BillLine(UUID tenantId, UUID billId, UUID unitId, LocalDate referenceMonth,
                           String municipalityIbge, BigDecimal totalValue,
                           BigDecimal waterValue, BigDecimal sewerValue) {
        public BillLine {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(billId, "billId");
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(referenceMonth, "referenceMonth");
            Objects.requireNonNull(totalValue, "totalValue");
            Objects.requireNonNull(waterValue, "waterValue");
            Objects.requireNonNull(sewerValue, "sewerValue");
        }
    }

    /**
     * Linha de consumo tal como {@code consumption_records_municipality_cut}
     * a devolve ({@code consumption_records}, metering V5_1, mais o IBGE).
     * {@code municipalityIbge} nulo = sem derivação.
     */
    public record ConsumptionLine(UUID tenantId, UUID recordId, UUID unitId, LocalDate reference,
                                  String serviceKind, String municipalityIbge,
                                  int billedConsumption, int calculatedConsumption) {
        public ConsumptionLine {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(recordId, "recordId");
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(serviceKind, "serviceKind");
            if (billedConsumption < 0 || calculatedConsumption < 0) {
                throw new IllegalArgumentException("consumo negativo nao existe no dominio (V5_1)");
            }
        }
    }

    /**
     * Parcela de UM município no fecho (ou a parcela "sem derivação",
     * quando {@code municipalityIbge} é nulo). Contagens e somas são
     * exactamente as das linhas desse município — nem mais, nem menos.
     */
    public record MunicipalityParcel(String municipalityIbge, long billCount, BigDecimal billsTotal,
                                     BigDecimal waterValue, BigDecimal sewerValue,
                                     long consumptionRecordCount, long billedConsumption,
                                     long calculatedConsumption) {
        /** Verdadeiro quando a parcela agrega linhas sem município derivável. */
        public boolean unassigned() {
            return municipalityIbge == null;
        }
    }

    /**
     * Consolidado do tenant: a soma das parcelas, e nada mais. Não tem
     * fonte própria — quem o constrói é {@code sumParcels}, a partir da
     * lista de parcelas do mesmo fecho.
     */
    public record Consolidated(UUID tenantId, long billCount, BigDecimal billsTotal,
                               BigDecimal waterValue, BigDecimal sewerValue,
                               long consumptionRecordCount, long billedConsumption,
                               long calculatedConsumption) {
    }

    /**
     * Resultado do fecho: parcelas por município (ordenadas por IBGE,
     * "sem derivação" por último) + consolidado. {@code referenceMonth}
     * é a referência do fecho quando ele foi pedido por referência
     * ({@link #closeReference}); nulo no fecho sobre o conjunto dado.
     */
    public record Closing(UUID tenantId, LocalDate referenceMonth,
                          List<MunicipalityParcel> parcels, Consolidated consolidated) {
        public Closing {
            parcels = List.copyOf(parcels);
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(consolidated, "consolidated");
        }

        /** Parcela de um município (IBGE de 7 dígitos), se existir neste fecho. */
        public Optional<MunicipalityParcel> parcelFor(String municipalityIbge) {
            Objects.requireNonNull(municipalityIbge, "municipalityIbge");
            return parcels.stream().filter(p -> municipalityIbge.equals(p.municipalityIbge())).findFirst();
        }

        /** Parcela "sem derivação" (linhas cuja UC não deriva município), se existir. */
        public Optional<MunicipalityParcel> unassignedParcel() {
            return parcels.stream().filter(MunicipalityParcel::unassigned).findFirst();
        }
    }

    private MunicipalityClosing() {
    }

    /**
     * Fecha o conjunto de linhas dado: agrupa por município e consolida
     * somando as parcelas. Todas as linhas têm de ser do tenant indicado
     * (a disciplina das FK compostas por tenant do domínio) — linha de
     * outro tenant é erro explícito, nunca soma silenciosa.
     */
    public static Closing close(UUID tenantId, Collection<BillLine> bills,
                                Collection<ConsumptionLine> consumptions) {
        return build(tenantId, null, bills, consumptions);
    }

    /**
     * Fecho de UMA referência de ciclo (o caso do fechamento mensal):
     * considera só as linhas cuja referência é {@code referenceMonth}
     * ({@code bills.reference_month} / {@code consumption_records.reference},
     * sempre dia 1) e fecha como {@link #close}.
     */
    public static Closing closeReference(UUID tenantId, LocalDate referenceMonth,
                                         Collection<BillLine> bills,
                                         Collection<ConsumptionLine> consumptions) {
        Objects.requireNonNull(referenceMonth, "referenceMonth");
        List<BillLine> monthBills = bills.stream()
                .filter(b -> referenceMonth.equals(b.referenceMonth())).toList();
        List<ConsumptionLine> monthConsumptions = consumptions.stream()
                .filter(c -> referenceMonth.equals(c.reference())).toList();
        return build(tenantId, referenceMonth, monthBills, monthConsumptions);
    }

    private static Closing build(UUID tenantId, LocalDate referenceMonth,
                                 Collection<BillLine> bills, Collection<ConsumptionLine> consumptions) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(bills, "bills");
        Objects.requireNonNull(consumptions, "consumptions");

        Map<String, Acc> byMunicipality = new LinkedHashMap<>();
        for (BillLine b : bills) {
            requireTenant(tenantId, b.tenantId());
            byMunicipality.computeIfAbsent(b.municipalityIbge(), k -> new Acc()).addBill(b);
        }
        for (ConsumptionLine c : consumptions) {
            requireTenant(tenantId, c.tenantId());
            byMunicipality.computeIfAbsent(c.municipalityIbge(), k -> new Acc()).addConsumption(c);
        }

        List<MunicipalityParcel> parcels = new ArrayList<>();
        byMunicipality.forEach((ibge, acc) -> parcels.add(acc.toParcel(ibge)));
        // Ordem determinística e auditável: IBGE crescente; "sem derivação" por último.
        parcels.sort(Comparator.comparing(MunicipalityParcel::municipalityIbge,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return new Closing(tenantId, referenceMonth, parcels, sumParcels(tenantId, parcels));
    }

    /**
     * O consolidado — SOMENTE a soma das parcelas. É a única via de
     * construção do total do tenant neste fecho: não recebe linhas,
     * não consulta nada, não tem fonte divergente possível.
     */
    private static Consolidated sumParcels(UUID tenantId, List<MunicipalityParcel> parcels) {
        long billCount = 0;
        long recordCount = 0;
        long billed = 0;
        long calculated = 0;
        BigDecimal total = ZERO_MONEY;
        BigDecimal water = ZERO_MONEY;
        BigDecimal sewer = ZERO_MONEY;
        for (MunicipalityParcel p : parcels) {
            billCount += p.billCount();
            recordCount += p.consumptionRecordCount();
            billed += p.billedConsumption();
            calculated += p.calculatedConsumption();
            total = total.add(p.billsTotal());
            water = water.add(p.waterValue());
            sewer = sewer.add(p.sewerValue());
        }
        return new Consolidated(tenantId, billCount, total, water, sewer,
                recordCount, billed, calculated);
    }

    private static void requireTenant(UUID expected, UUID actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    "linha de outro tenant no fecho: esperado " + expected + ", veio " + actual);
        }
    }

    /** Acumulador mutável de uma parcela, durante o agrupamento. */
    private static final class Acc {
        private long billCount;
        private long recordCount;
        private long billed;
        private long calculated;
        private BigDecimal total = ZERO_MONEY;
        private BigDecimal water = ZERO_MONEY;
        private BigDecimal sewer = ZERO_MONEY;

        void addBill(BillLine b) {
            billCount++;
            total = total.add(b.totalValue());
            water = water.add(b.waterValue());
            sewer = sewer.add(b.sewerValue());
        }

        void addConsumption(ConsumptionLine c) {
            recordCount++;
            billed += c.billedConsumption();
            calculated += c.calculatedConsumption();
        }

        MunicipalityParcel toParcel(String municipalityIbge) {
            return new MunicipalityParcel(municipalityIbge, billCount, total, water, sewer,
                    recordCount, billed, calculated);
        }
    }
}
