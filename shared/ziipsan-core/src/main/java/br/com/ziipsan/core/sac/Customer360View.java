package br.com.ziipsan.core.sac;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Visao 360 do atendimento (ED-35): faturas, consumos, servicos (protocolos/OS)
 * e analise de agua da unidade consumidora, agregados para a tela do atendente.
 * Os dados chegam prontos das fontes donas (conta da P2.5, protocolos da P1.36,
 * analise de agua do cadastro/qualidade) — esta classe monta e resume; nao
 * consulta nada (fiacao de integracao alimenta).
 */
public final class Customer360View {

    public record BillSummary(String billId, LocalDate dueDate, BigDecimal amount, boolean paid) {}
    public record ConsumptionSummary(String competence, long consumptionM3) {}
    public record ServiceSummary(String protocolLabel, String status) {}
    public record WaterAnalysisSummary(LocalDate collectedOn, String result) {}

    private final UUID consumerUnitId;
    private final List<BillSummary> bills;
    private final List<ConsumptionSummary> consumptions;
    private final List<ServiceSummary> services;
    private final List<WaterAnalysisSummary> waterAnalyses;

    public Customer360View(UUID consumerUnitId, List<BillSummary> bills,
                           List<ConsumptionSummary> consumptions, List<ServiceSummary> services,
                           List<WaterAnalysisSummary> waterAnalyses) {
        this.consumerUnitId = Objects.requireNonNull(consumerUnitId, "unidade consumidora obrigatoria");
        this.bills = List.copyOf(bills);
        this.consumptions = List.copyOf(consumptions);
        this.services = List.copyOf(services);
        this.waterAnalyses = List.copyOf(waterAnalyses);
    }

    public long openBills() { return bills.stream().filter(b -> !b.paid()).count(); }
    public long overdueBillsOn(LocalDate date) {
        return bills.stream().filter(b -> !b.paid() && date.isAfter(b.dueDate())).count();
    }
    public BigDecimal openAmount() {
        return bills.stream().filter(b -> !b.paid()).map(BillSummary::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public UUID consumerUnitId() { return consumerUnitId; }
    public List<BillSummary> bills() { return bills; }
    public List<ConsumptionSummary> consumptions() { return consumptions; }
    public List<ServiceSummary> services() { return services; }
    public List<WaterAnalysisSummary> waterAnalyses() { return waterAnalyses; }
}
