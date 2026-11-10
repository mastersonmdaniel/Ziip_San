package br.com.ziipsan.operations.metering.hydrometry.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload de UMA OS selectiva de troca (II-5): um por linha do
 * {@link AnnualRenewalPlan.AnnualPlan} — correspondência 1:1 com o
 * plano, provada em teste. O payload é o contrato com a porta do núcleo
 * de OS ({@link SelectiveRenewalOrderService.RenewalOrderPort}); a
 * máquina de estados da OS é a do núcleo da P1.31 e NÃO é duplicada
 * aqui (precedente: portas da ED-14 {@code VerificationAlertService} e
 * da P3.28 {@code LeakMeterOrderGateway}).
 *
 * <p>{@code effectPayloadJson} é construído deterministicamente (ordem
 * fixa de chaves, score em {@code toPlainString}, datas ISO): o mesmo
 * plano produz sempre o mesmo JSON, byte a byte. O formato segue o
 * esquema declarado em {@link RenewalOrderType#effectSchemaJson()}.
 */
public record RenewalOrderPayload(
        UUID tenantId,
        int planYear,
        int position,
        UUID meterId,
        UUID unitId,
        String meterNumber,
        String serviceTypeCode,
        String targetClass,
        UUID targetId,
        BigDecimal totalScore,
        RenewalScoreEngine.Criterion dominantCriterion,
        MetrologicalClock.ClockStatus clockStatus,
        LocalDate nextVerificationOn,
        String effectPayloadJson) {

    /** Constrói o payload da linha do plano (alvo = o próprio hidrómetro, classe METER da ED-14). */
    public static RenewalOrderPayload fromPlanLine(UUID tenantId, AnnualRenewalPlan.AnnualPlan plan,
                                                   AnnualRenewalPlan.PlanLine line) {
        if (tenantId == null) throw new IllegalArgumentException("Tenant do payload é obrigatório");
        if (plan == null || line == null) throw new IllegalArgumentException("Plano e linha são obrigatórios");
        RenewalOrderType type = RenewalOrderType.METER_REPLACEMENT_SELECTIVE;
        String json = "{"
                + "\"meterId\":\"" + line.meterId() + "\""
                + ",\"unitId\":" + (line.unitId() == null ? "null" : "\"" + line.unitId() + "\"")
                + ",\"meterNumber\":\"" + escapeJson(line.meterNumber()) + "\""
                + ",\"planYear\":" + plan.planYear()
                + ",\"planPosition\":" + line.position()
                + ",\"renewalScore\":\"" + line.totalScore().toPlainString() + "\""
                + ",\"dominantCriterion\":\"" + line.dominantCriterion().name() + "\""
                + ",\"clockStatus\":\"" + line.clockStatus().name() + "\""
                + ",\"nextVerificationOn\":" + (line.nextVerificationOn() == null
                        ? "null" : "\"" + line.nextVerificationOn() + "\"")
                + "}";
        return new RenewalOrderPayload(tenantId, plan.planYear(), line.position(),
                line.meterId(), line.unitId(), line.meterNumber(),
                type.code(), RenewalOrderType.TARGET_CLASS, line.meterId(),
                line.totalScore(), line.dominantCriterion(), line.clockStatus(),
                line.nextVerificationOn(), json);
    }

    /** Escape mínimo e suficiente para os valores textuais do payload (número do hidrómetro). */
    private static String escapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
