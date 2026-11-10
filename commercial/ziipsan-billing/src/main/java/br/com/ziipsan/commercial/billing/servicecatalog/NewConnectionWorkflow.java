package br.com.ziipsan.commercial.billing.servicecatalog;

import br.com.ziipsan.commercial.billingrules.math.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluxo encadeado da ligação nova (ED-34): pedido, documentos, viabilidade (rede, pressão,
 * diâmetro), orçamento, guia/NFS-e, vistoria do padrão, execução, ativação com hidrômetro,
 * primeira leitura — mais a entrega do contrato de adesão (P3.20) e o vínculo do cliente.
 *
 * <p>Regras nomeadas no acceptance:
 * <ul>
 *   <li>esgoto simultâneo onde há rede: {@code connection.new_requires_sewer_when_available}
 *       (parâmetro do tenant) + rede de esgoto disponível ⇒ a etapa SEWER_CONNECTION entra
 *       no fluxo e o orçamento inclui o item de esgoto; sem rede, não entra;</li>
 *   <li>orçamento por bitola, pavimento e categoria: os códigos de item são os do catálogo
 *       do tenant para aquela combinação — esta classe resolve PREÇO na {@link PriceTable},
 *       não escolhe código nem valor (nada inventado);</li>
 *   <li>prazos de vistoria e de ligação são lidos da regra da agência (NR 11, arts. 72 e 73)
 *       e entram como {@code deadlineHours} das etapas INSPECTION/EXECUTION pelo chamador;</li>
 *   <li>dias mínimos de cobrança após a ativação (TR-300): o mínimo é parâmetro do tenant
 *       entregue pelo chamador; {@link #billableDays} aplica max(real, mínimo).</li>
 * </ul>
 */
public final class NewConnectionWorkflow {

    private NewConnectionWorkflow() {}

    public static WorkflowDefinition definition(boolean sewerAvailable, boolean requiresSewerWhenAvailable,
                                                Integer inspectionDeadlineHours, Integer executionDeadlineHours) {
        List<WorkflowDefinition.Step> s = new ArrayList<>();
        s.add(new WorkflowDefinition.Step("REQUEST", "Pedido de ligacao nova", List.of(), null));
        s.add(new WorkflowDefinition.Step("DOCUMENTS", "Documentos exigidos do tipo", List.of("REQUEST"), null));
        s.add(new WorkflowDefinition.Step("FEASIBILITY", "Viabilidade: rede, pressao e diametro", List.of("DOCUMENTS"), null));
        s.add(new WorkflowDefinition.Step("QUOTE", "Orcamento por bitola, pavimento e categoria", List.of("FEASIBILITY"), null));
        s.add(new WorkflowDefinition.Step("PAYMENT_GUIDE", "Guia ou NFS-e do orcamento", List.of("QUOTE"), null));
        s.add(new WorkflowDefinition.Step("INSPECTION", "Vistoria do padrao", List.of("PAYMENT_GUIDE"), inspectionDeadlineHours));
        s.add(new WorkflowDefinition.Step("EXECUTION", "Execucao da ligacao de agua", List.of("INSPECTION"), executionDeadlineHours));
        List<String> activationDeps = new ArrayList<>(List.of("EXECUTION"));
        if (sewerAvailable && requiresSewerWhenAvailable) {
            s.add(new WorkflowDefinition.Step("SEWER_CONNECTION", "Ligacao de esgoto simultanea (ha rede)", List.of("INSPECTION"), executionDeadlineHours));
            activationDeps.add("SEWER_CONNECTION");
        }
        s.add(new WorkflowDefinition.Step("ACTIVATION", "Ativacao com hidrometro instalado", activationDeps, null));
        s.add(new WorkflowDefinition.Step("CONTRACT_DELIVERY", "Entrega do contrato de adesao vigente (P3.20)", List.of("ACTIVATION"), null));
        s.add(new WorkflowDefinition.Step("CUSTOMER_LINK", "Vinculo do cliente a unidade", List.of("ACTIVATION"), null));
        s.add(new WorkflowDefinition.Step("FIRST_READING", "Primeira leitura", List.of("ACTIVATION"), null));
        return new WorkflowDefinition(s);
    }

    /** Orçamento = soma dos itens de serviço e materiais resolvidos na vigência da data. */
    public record QuoteLine(String code, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) {}
    public record Quote(List<QuoteLine> lines, BigDecimal total) {}

    public static Quote quote(PriceTable table, LocalDate on, List<ServiceChargeOnClose.MaterialUse> serviceItems,
                              List<ServiceChargeOnClose.MaterialUse> materialItems) {
        List<QuoteLine> lines = new ArrayList<>();
        BigDecimal total = Money.ZERO;
        for (ServiceChargeOnClose.MaterialUse i : serviceItems) {
            ServicePriceItem p = table.service(i.materialCode(), on);
            if (!p.billable()) throw new IllegalStateException("item " + i.materialCode() + " sem homologacao registrada: nao orcavel como faturavel");
            BigDecimal amt = Money.roundHalfUp(p.unitPrice().multiply(i.quantity()));
            lines.add(new QuoteLine(i.materialCode(), i.quantity(), p.unitPrice(), amt));
            total = total.add(amt);
        }
        for (ServiceChargeOnClose.MaterialUse i : materialItems) {
            MaterialPriceItem p = table.material(i.materialCode(), on);
            BigDecimal amt = Money.roundHalfUp(p.unitPrice().multiply(i.quantity()));
            lines.add(new QuoteLine(i.materialCode(), i.quantity(), p.unitPrice(), amt));
            total = total.add(amt);
        }
        return new Quote(List.copyOf(lines), Money.roundHalfUp(total));
    }

    /** Dias cobráveis do primeiro período (TR-300): nunca menos que o mínimo do parâmetro. */
    public static long billableDays(LocalDate activation, LocalDate firstReading, int minimumDaysParameter) {
        if (minimumDaysParameter <= 0) throw new IllegalArgumentException("dias minimos de cobranca (TR-300) devem vir do parametro do tenant e ser positivos");
        if (firstReading.isBefore(activation)) throw new IllegalArgumentException("primeira leitura anterior a ativacao");
        long actual = ChronoUnit.DAYS.between(activation, firstReading);
        return Math.max(actual, minimumDaysParameter);
    }
}
