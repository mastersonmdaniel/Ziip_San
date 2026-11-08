package br.com.ziipsan.platform.portal.selfcare;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * POC-69 do roteiro VISAN (ED-38): pelo menos os servicos do ensaio —
 * faturas pendentes e quitadas do ultimo ano, segunda via (pendente e
 * quitada), simulacao de fatura por consumo e categoria, simulacao de
 * parcelamento e certidao de quitacao anual (esta em
 * {@link AnnualSettlementCertificate}).
 *
 * <p>Fronteiras: a lista de faturas vem do historico da P3.20 (porta);
 * a segunda via delega a regra da P3.3/P3.20 pela porta (PDF + Pix da
 * P3.2 sao da integracao); a simulacao de parcelamento e SIMULACAO —
 * nao cria acordo (o acordo e da P4.1) — com a mesma aritmetica
 * declarada: divisao igual, ultima parcela absorve o residuo, juros
 * SIMPLES mensais sobre o saldo (total = divida x (1 + taxa x parcelas))
 * quando houver taxa informada pelo chamador a partir da politica
 * vigente. Nenhuma taxa e presumida aqui.
 *
 * <p>Nota de regime (correcao D3 da revisao cruzada): a P4.1 define o
 * {@code interest_method} do ACORDO como PRICE, COMPOUND ou NONE
 * (DAD-21, escolhido pela politica do acordo) — nao ha metodo "simples"
 * na P4.1 e ela nao fixa um regime unico para esta SIMULACAO. O regime
 * simples acima e o regime DECLARADO desta simulacao (que nao cria
 * acordo); qual metodo da P4.1 um acordo real viria a usar segue
 * [A DEFINIR] na integracao, pela politica vigente do tenant.
 */
public final class Poc69SelfCareService {

    public static final int POC69_WINDOW_MONTHS = 12;

    public enum BillStatus { PENDING, SETTLED }

    public record BillSummary(UUID billId, YearMonth reference, LocalDate dueDate,
                              BigDecimal amount, BillStatus status) { }

    public interface BillsPort {
        boolean isUnitLinkedToConsumer(UUID tenantId, String consumerSubject, UUID unitId);
        List<BillSummary> bills(UUID tenantId, UUID unitId, YearMonth fromInclusive, YearMonth toInclusive);
        boolean billBelongsToUnit(UUID tenantId, UUID unitId, UUID billId);
        /** Segunda via (P3.3/P3.20): identificador do documento gerado pela integracao. */
        Optional<String> secondCopyDocumentRef(UUID tenantId, UUID billId);
    }

    public record TariffBand(BigDecimal upToM3, BigDecimal pricePerM3) {
        public TariffBand {
            if (upToM3 == null || pricePerM3 == null || pricePerM3.signum() < 0) {
                throw new IllegalArgumentException("faixa tarifaria invalida");
            }
        }
    }

    /** Estrutura tarifaria de entrada da simulacao (vem da tabela vigente do tenant; nada presumido). */
    public record TariffStructure(BigDecimal minimumFee, List<TariffBand> bands, BigDecimal sewagePercent) {
        public TariffStructure {
            if (minimumFee == null || bands == null || bands.isEmpty() || sewagePercent == null) {
                throw new IllegalArgumentException("estrutura tarifaria da simulacao e obrigatoria");
            }
            bands = List.copyOf(bands);
        }
    }

    public record Simulation(BigDecimal consumptionM3, BigDecimal waterAmount, BigDecimal sewageAmount,
                             BigDecimal totalAmount, boolean simulationOnly) { }

    public record InstallmentSimulation(BigDecimal totalDebt, int installments, BigDecimal monthlyRate,
                                        List<BigDecimal> amounts, boolean simulationOnly) { }

    private final BillsPort port;

    public Poc69SelfCareService(BillsPort port) {
        if (port == null) {
            throw new IllegalArgumentException("porta de faturas e obrigatoria");
        }
        this.port = port;
    }

    private void requireLinked(UUID tenantId, String consumerSubject, UUID unitId) {
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank() || unitId == null) {
            throw new IllegalArgumentException("tenant, consumidor e unidade sao obrigatorios");
        }
        if (!port.isUnitLinkedToConsumer(tenantId, consumerSubject, unitId)) {
            throw new IllegalStateException("Unidade nao vinculada a esta conta do portal.");
        }
    }

    /** Pendentes e quitadas do ultimo ano (janela do POC-69), mais recentes primeiro. */
    public List<BillSummary> lastYearBills(UUID tenantId, String consumerSubject, UUID unitId, YearMonth current) {
        requireLinked(tenantId, consumerSubject, unitId);
        if (current == null) {
            throw new IllegalArgumentException("mes de referencia e obrigatorio");
        }
        YearMonth from = current.minusMonths(POC69_WINDOW_MONTHS - 1L);
        List<BillSummary> all = new ArrayList<>(port.bills(tenantId, unitId, from, current));
        all.removeIf(b -> b.reference().isBefore(from) || b.reference().isAfter(current));
        all.sort(Comparator.comparing(BillSummary::reference).reversed());
        return List.copyOf(all);
    }

    public List<BillSummary> pending(UUID tenantId, String consumerSubject, UUID unitId, YearMonth current) {
        return lastYearBills(tenantId, consumerSubject, unitId, current).stream()
                .filter(b -> b.status() == BillStatus.PENDING).toList();
    }

    public List<BillSummary> settled(UUID tenantId, String consumerSubject, UUID unitId, YearMonth current) {
        return lastYearBills(tenantId, consumerSubject, unitId, current).stream()
                .filter(b -> b.status() == BillStatus.SETTLED).toList();
    }

    /** Segunda via de fatura pendente OU quitada (POC-69 pede as duas). */
    public String secondCopy(UUID tenantId, String consumerSubject, UUID unitId, UUID billId) {
        requireLinked(tenantId, consumerSubject, unitId);
        if (billId == null || !port.billBelongsToUnit(tenantId, unitId, billId)) {
            throw new IllegalStateException("Fatura nao encontrada para a unidade vinculada.");
        }
        return port.secondCopyDocumentRef(tenantId, billId)
                .orElseThrow(() -> new IllegalStateException("segunda via nao gerada para a fatura " + billId));
    }

    /** Simulacao de fatura por consumo e categoria (estrutura tarifaria de entrada; determinista). */
    public static Simulation simulateBill(BigDecimal consumptionM3, TariffStructure tariff) {
        if (consumptionM3 == null || consumptionM3.signum() < 0 || tariff == null) {
            throw new IllegalArgumentException("consumo e estrutura tarifaria sao obrigatorios");
        }
        BigDecimal remaining = consumptionM3;
        BigDecimal previousLimit = BigDecimal.ZERO;
        BigDecimal water = tariff.minimumFee();
        for (TariffBand band : tariff.bands()) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal bandWidth = band.upToM3().subtract(previousLimit);
            BigDecimal inBand = remaining.min(bandWidth);
            if (inBand.signum() > 0) {
                water = water.add(inBand.multiply(band.pricePerM3()));
                remaining = remaining.subtract(inBand);
            }
            previousLimit = band.upToM3();
        }
        if (remaining.signum() > 0) {
            TariffBand last = tariff.bands().get(tariff.bands().size() - 1);
            water = water.add(remaining.multiply(last.pricePerM3()));
        }
        BigDecimal sewage = water.multiply(tariff.sewagePercent()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal w = water.setScale(2, RoundingMode.HALF_UP);
        return new Simulation(consumptionM3, w, sewage, w.add(sewage), true);
    }

    /**
     * Simulacao de parcelamento: divisao igual, ultima absorve o residuo;
     * NUNCA cria acordo (P4.1). Regime de juros SIMPLES, como declarado no
     * cabecalho da classe: total = divida x (1 + taxa/100 x parcelas),
     * sem capitalizacao (correcao D3: o codigo anterior usava pow,
     * composto, contradizendo o rotulo).
     */
    public static InstallmentSimulation simulateInstallments(BigDecimal totalDebt, int installments,
                                                             BigDecimal monthlyRate) {
        if (totalDebt == null || totalDebt.signum() <= 0 || installments < 1 || monthlyRate == null
                || monthlyRate.signum() < 0) {
            throw new IllegalArgumentException("divida positiva, parcelas >= 1 e taxa >= 0 sao obrigatorios");
        }
        BigDecimal total = totalDebt;
        if (monthlyRate.signum() > 0) {
            BigDecimal rateFraction = monthlyRate.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP);
            BigDecimal factor = BigDecimal.ONE.add(rateFraction.multiply(BigDecimal.valueOf(installments)));
            total = totalDebt.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal each = total.divide(BigDecimal.valueOf(installments), 2, RoundingMode.HALF_UP);
        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal acc = BigDecimal.ZERO;
        for (int i = 0; i < installments; i++) {
            BigDecimal a = i < installments - 1 ? each : total.subtract(acc);
            amounts.add(a);
            acc = acc.add(a);
        }
        return new InstallmentSimulation(totalDebt, installments, monthlyRate, List.copyOf(amounts), true);
    }
}
