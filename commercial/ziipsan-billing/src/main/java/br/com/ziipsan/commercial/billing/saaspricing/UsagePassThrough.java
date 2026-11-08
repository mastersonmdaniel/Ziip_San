package br.com.ziipsan.commercial.billing.saaspricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Franquia ou repasse de MENSAGENS e de MODELO DE LINGUAGEM (aceite da
 * ED-10, GES-19): calculado com (a) a medicao de uso e custo por tenant
 * (P2.12 usage_ledger + ED-41 UsageStore/UsageRecord) e (b) o custo por
 * CATEGORIA de modelo de mensagem (ED-41: MARKETING/UTILITY/
 * AUTHENTICATION do WhatsApp; SMS como categoria propria) e por
 * categoria de modelo de linguagem.
 *
 * <p>Franquias, custos unitarios e o modo (FRANCHISE x
 * FULL_PASS_THROUGH) sao dados do contrato/planilha vigente: NENHUM
 * valor e fixado aqui. Uso medido sem custo unitario vigente = recusa
 * nomeada, nunca repasse a custo presumido. Quantidades chegam por
 * fatos (leitura do chamador); esta classe e aritmetica pura.
 * Convencao declarada: LLM_TOKENS tem custo unitario por 1.000 tokens;
 * as demais categorias, por mensagem.
 */
public final class UsagePassThrough {
    private UsagePassThrough() {}

    /** Categoria de cobranca do uso: espelha as categorias da ED-41 + SMS + LLM. */
    public enum UsageCategory { WHATSAPP_MARKETING, WHATSAPP_UTILITY, WHATSAPP_AUTHENTICATION, SMS, LLM_TOKENS }

    /**
     * Linha de franquia/repasse de UMA categoria na competencia.
     * @param franchiseQuantity franquia contratada (obrigatoria no modo FRANCHISE quando ha uso)
     * @param unitCost custo unitario vigente (por mensagem; LLM: por 1.000 tokens)
     * @param measuredQuantity uso MEDIDO na competencia (P2.12/ED-41)
     */
    public record UsageLine(UsageCategory category, Long franchiseQuantity, BigDecimal unitCost, long measuredQuantity) {
        public UsageLine {
            if (category == null) throw new IllegalArgumentException("categoria de uso e obrigatoria");
            if (measuredQuantity < 0) throw new IllegalArgumentException("uso medido invalido: " + category);
            if (franchiseQuantity != null && franchiseQuantity < 0) throw new IllegalArgumentException("franquia invalida: " + category);
            if (unitCost != null && unitCost.signum() < 0) throw new IllegalArgumentException("custo unitario invalido: " + category);
        }
    }

    public record LineCharge(UsageCategory category, long measuredQuantity, long franchiseQuantity,
                             long billableQuantity, BigDecimal unitCost, BigDecimal amount) {}
    public record PassThroughStatement(UUID tenantId, YearMonth competence,
                                       List<LineCharge> lines, BigDecimal total) {}

    public static LineCharge charge(UsageLine line, SaasPricingParameters.PassThroughMode mode) {
        if (line == null || mode == null) throw new IllegalArgumentException("linha de uso e modo de repasse sao obrigatorios");
        long franchise = line.franchiseQuantity() == null ? 0 : line.franchiseQuantity();
        if (line.measuredQuantity() == 0)
            return new LineCharge(line.category(), 0, franchise, 0, line.unitCost(), new BigDecimal("0.00"));
        if (line.unitCost() == null)
            throw new IllegalStateException("custo unitario [A DEFINIR] para a categoria " + line.category()
                    + ": repasse recusado sem custo vigente (GES-19)");
        if (mode == SaasPricingParameters.PassThroughMode.FRANCHISE && line.franchiseQuantity() == null)
            throw new IllegalStateException("franquia [A DEFINIR] para a categoria " + line.category()
                    + ": modo FRANCHISE exige franquia contratada declarada");
        long billable = Math.max(0, line.measuredQuantity() - franchise);
        if (mode == SaasPricingParameters.PassThroughMode.FULL_PASS_THROUGH) billable = line.measuredQuantity();
        BigDecimal divisor = line.category() == UsageCategory.LLM_TOKENS ? new BigDecimal("1000") : BigDecimal.ONE;
        BigDecimal amount = line.unitCost().multiply(BigDecimal.valueOf(billable))
                .divide(divisor, 2, RoundingMode.HALF_UP);
        return new LineCharge(line.category(), line.measuredQuantity(), franchise, billable, line.unitCost(), amount);
    }

    public static PassThroughStatement statement(UUID tenantId, YearMonth competence,
                                                 List<UsageLine> lines, SaasPricingParameters params) {
        if (tenantId == null || competence == null || lines == null) throw new IllegalArgumentException("tenant, competencia e linhas sao obrigatorios");
        if (params == null) throw new IllegalArgumentException("parametros comerciais sao obrigatorios");
        var mode = params.passThroughMode();
        List<LineCharge> charges = lines.stream().map(l -> charge(l, mode)).toList();
        BigDecimal total = charges.stream().map(LineCharge::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PassThroughStatement(tenantId, competence, List.copyOf(charges), PriceCalculator.money(total));
    }
}
