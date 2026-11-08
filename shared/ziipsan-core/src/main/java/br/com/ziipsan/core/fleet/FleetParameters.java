package br.com.ziipsan.core.fleet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parametros da frota (ED-59, OPE-28) — mecanismo de parametros do
 * tenant (catalogo do core, SQL irmao em db/pendente). REGRA DURA:
 * NENHUM parametro desta tarefa tem valor de fabrica — nenhuma fonte
 * das dependencias (aceite da ED-59, ED-56, ED-36, P1.35) fixa dias
 * de alerta, itens de checklist nem limite de desvio de consumo:
 * <ul>
 *   <li>{@code fleet.checklist_required} (BOOLEAN): o aceite nomeia a
 *       chave; sem valor do tenant, o checklist NAO e exigido
 *       (comportamento declarado). Com {@code true}, os itens vem de
 *       {@code fleet.checklist_items} — sem itens definidos, a
 *       exigencia nao pode ser cumprida e a saida e recusada
 *       (falha fechada declarada, nunca itens inventados);</li>
 *   <li>{@code fleet.docs_alert_days} (INTEGER): janela do alerta de
 *       vencimento de habilitacao/licenciamento. Sem valor, NAO ha
 *       estado "vencendo" — so vencido/ valido por comparacao de
 *       datas (o vencido nao depende de parametro);</li>
 *   <li>{@code fleet.checklist_items} (TABLE): itens do checklist
 *       pre-viagem por tenant. Conteudo do tenant, nunca do produto;</li>
 *   <li>{@code fleet.consumption_deviation_percent} (DECIMAL): limite
 *       de desvio do consumo medio para alerta. Sem valor, nenhum
 *       alerta de desvio e emitido (declarado).</li>
 * </ul>
 * A manutencao de veiculos NAO ganha parametros novos: ela reusa os
 * {@code maint.pm_generation_lead_days} e
 * {@code maint.pm_overdue_tolerance_days} da ED-56 (ver
 * {@link VehicleMaintenance}).
 */
public final class FleetParameters {

    public static final String CHECKLIST_REQUIRED = "fleet.checklist_required";
    public static final String DOCS_ALERT_DAYS = "fleet.docs_alert_days";
    public static final String CHECKLIST_ITEMS = "fleet.checklist_items";
    public static final String CONSUMPTION_DEVIATION_PERCENT = "fleet.consumption_deviation_percent";

    private FleetParameters() {
    }

    /** Exigencia de checklist: so com valor explicito {@code true} do tenant. */
    public static boolean checklistRequired(Optional<Boolean> tenantValue) {
        return tenantValue != null && tenantValue.orElse(false);
    }

    /** Janela de alerta de documentos: vazia sem valor do tenant (sem "vencendo"). */
    public static Optional<Integer> docsAlertDays(Optional<Integer> tenantValue) {
        if (tenantValue == null || tenantValue.isEmpty()) return Optional.empty();
        int days = tenantValue.get();
        if (days < 0) throw new IllegalArgumentException(DOCS_ALERT_DAYS + " nao pode ser negativo: " + days);
        return Optional.of(days);
    }

    /** Itens do checklist do tenant (lista ordenada de codigos de item); vazia sem parametro. */
    public static List<String> checklistItems(Map<String, Object> tenantTable) {
        if (tenantTable == null) return List.of();
        Object raw = tenantTable.get("items");
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list))
            throw new IllegalArgumentException(CHECKLIST_ITEMS + ": campo items deve ser lista de codigos");
        List<String> items = list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return List.copyOf(items);
    }

    /** Limite de desvio de consumo (%); vazio sem valor do tenant (sem alerta). */
    public static Optional<BigDecimal> consumptionDeviationPercent(Optional<BigDecimal> tenantValue) {
        if (tenantValue == null || tenantValue.isEmpty()) return Optional.empty();
        BigDecimal pct = tenantValue.get();
        if (pct.signum() < 0) throw new IllegalArgumentException(CONSUMPTION_DEVIATION_PERCENT + " nao pode ser negativo: " + pct);
        return Optional.of(pct);
    }
}
