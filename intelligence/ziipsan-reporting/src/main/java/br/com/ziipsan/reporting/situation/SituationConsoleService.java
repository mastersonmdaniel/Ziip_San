package br.com.ziipsan.reporting.situation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-93: o console operacional nativo — o lado de LEITURA. Le
 * SOMENTE a tabela de estado resumida ({@link SituationStateStore},
 * admitida pela {@link SituationSourceGuard}) e os indicadores do
 * registro da P3.41 (via {@link SituationIndicators}, quando ligada).
 * Nao conhece porta de fonte nenhuma: nao ha caminho, neste servico,
 * para consulta livre ao primario nem para o Superset — a fronteira
 * do aceite e estrutural, e o script de coerencia a confere.
 *
 * <p>Este servico e o NUCLEO do console: controller HTTP, tela/mapa
 * web e fiacao sao da integracao (pendencia P-ED93-01); o modelo de
 * mapa que a tela consome e o {@link SituationMapView}.
 *
 * <p>Semantica das listas de OS (correcao D1 da revisao ED-93-REV-1):
 * "em execucao" e "vencida" sao predicados INDEPENDENTES — vencida e
 * o {@code timeliness} da ED-36, ortogonal ao status, como na fonte.
 * As listas {@code workOrdersExecuting} e {@code workOrdersOverdue}
 * NAO sao disjuntas: uma OS em execucao E vencida aparece nas DUAS;
 * {@code workOrdersOtherOpen} e o residual (aberta que nao esta em
 * execucao nem vencida). O aceite nomeia os dois conjuntos ("OS em
 * execucao e vencidas"), nao uma particao disjunta com prioridade.
 *
 * <p>Criterio de frescor por conjunto (correcao D2 da mesma revisao):
 * o frescor de um conjunto e o do seu fato MAIS ANTIGO
 * ({@code sourceAt} minimo — o pior caso de idade que o operador
 * ve), o MESMO criterio da sonda de latencia
 * ({@link SituationLatencyProbe}, que sempre usou o minimo). Um item
 * velho nunca some atras de um item novo do mesmo conjunto.
 */
public final class SituationConsoleService {

    private final SituationStateStore store;
    private final SituationIndicators indicatorsOrNull;
    private final List<SituationIndicators.Binding> bindings;

    /** Console sem indicadores ligados (pendencia nomeada no retrato: lista vazia). */
    public SituationConsoleService(SituationStateStore store) {
        this(store, null, List.of());
    }

    public SituationConsoleService(SituationStateStore store, SituationIndicators indicators,
                                   List<SituationIndicators.Binding> bindings) {
        this.store = Objects.requireNonNull(store, "tabela de estado obrigatoria");
        this.indicatorsOrNull = indicators;
        this.bindings = bindings == null ? List.of() : List.copyOf(bindings);
    }

    /**
     * Monta o retrato do console a partir da tabela de estado.
     * A fonte e conferida na entrada: qualquer coisa que nao seja a
     * tabela resumida nativa e recusada nomeada pela guarda.
     */
    public SituationSnapshot snapshot(UUID tenantId, SituationSourceGuard.FeedSource source,
                                      SituationLatencyPolicy policy, Instant readAt) {
        SituationSourceGuard.requireSummaryTable(source);
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        Objects.requireNonNull(policy, "politica de latencia obrigatoria (RNF ADR-0012)");
        Objects.requireNonNull(readAt, "instante da leitura obrigatorio");

        List<SituationStateRow> rows = store.rows(tenantId);
        List<SituationStateRow> incidents = byKind(rows, SituationItemKind.INCIDENT);
        List<SituationStateRow> alarms = byKind(rows, SituationItemKind.TELEMETRY_ALARM);
        List<SituationStateRow> reservoirs = byKind(rows, SituationItemKind.RESERVOIR);
        List<SituationStateRow> outages = byKind(rows, SituationItemKind.UNIT_OUTAGE);
        List<SituationStateRow> crews = byKind(rows, SituationItemKind.CREW);

        // D1 (revisao ED-93-REV-1): predicados independentes, listas
        // NAO disjuntas — IN_EXECUTION + OVERDUE entra nas duas listas.
        List<SituationStateRow> workOrders = byKind(rows, SituationItemKind.WORK_ORDER);
        List<SituationStateRow> executing = new ArrayList<>();
        List<SituationStateRow> overdue = new ArrayList<>();
        List<SituationStateRow> otherOpen = new ArrayList<>();
        for (SituationStateRow row : workOrders) {
            boolean isExecuting = "IN_EXECUTION".equals(row.statusLabel());
            boolean isOverdue = "OVERDUE".equals(row.detail().get("timeliness"));
            if (isExecuting) {
                executing.add(row);
            }
            if (isOverdue) {
                overdue.add(row);
            }
            if (!isExecuting && !isOverdue) {
                otherOpen.add(row);
            }
        }

        // D2 (revisao ED-93-REV-1): frescor por conjunto pelo fato
        // MAIS ANTIGO (pior caso), unificado com a sonda de latencia.
        Map<SituationItemKind, SituationLatencyPolicy.Freshness> freshness = new EnumMap<>(SituationItemKind.class);
        freshness.put(SituationItemKind.INCIDENT, policy.freshness(oldest(incidents), readAt));
        freshness.put(SituationItemKind.WORK_ORDER, policy.freshness(oldest(workOrders), readAt));
        freshness.put(SituationItemKind.CREW, policy.freshness(oldest(crews), readAt));
        freshness.put(SituationItemKind.TELEMETRY_ALARM, policy.freshness(oldest(alarms), readAt));
        freshness.put(SituationItemKind.RESERVOIR, policy.freshness(oldest(reservoirs), readAt));
        freshness.put(SituationItemKind.UNIT_OUTAGE, policy.freshness(oldest(outages), readAt));

        return new SituationSnapshot(tenantId, readAt, incidents, executing, overdue, otherOpen,
                crews, alarms, reservoirs, outages, List.of(), freshness);
    }

    /**
     * Retrato com os indicadores do registro da P3.41 ligados
     * (JOR-19). Sem servico de indicadores ligado, recusa nomeada —
     * indicador nao nasce de outro lugar.
     */
    public SituationSnapshot snapshotWithIndicators(UUID tenantId, SituationSourceGuard.FeedSource source,
                                                    SituationLatencyPolicy policy, Instant readAt,
                                                    LocalDate periodStart, LocalDate periodEnd, String areaCode,
                                                    Map<String, Map<String, java.math.BigDecimal>> variableValuesByCode) {
        if (indicatorsOrNull == null) {
            throw new br.com.ziipsan.reporting.indicator.IndicatorValidationException(
                    "SITUATION_INDICATORS_NOT_BOUND",
                    "indicadores do console nao ligados: liguem o registro da P3.41 (JOR-19) "
                            + "e declarem as ligacoes (" + SituationIndicators.UNBOUND_PENDING + ")");
        }
        SituationSnapshot base = snapshot(tenantId, source, policy, readAt);
        List<SituationIndicators.ConsoleIndicatorView> views = indicatorsOrNull.read(
                tenantId, bindings, periodStart, periodEnd, areaCode, variableValuesByCode);
        return new SituationSnapshot(base.tenantId(), base.generatedAt(), base.incidents(),
                base.workOrdersExecuting(), base.workOrdersOverdue(), base.workOrdersOtherOpen(),
                base.crews(), base.telemetryAlarms(), base.reservoirs(), base.unitsOutOfOperation(),
                views, base.freshnessByKind());
    }

    private static List<SituationStateRow> byKind(List<SituationStateRow> rows, SituationItemKind kind) {
        return rows.stream().filter(r -> r.kind() == kind).toList();
    }

    /**
     * O fato mais ANTIGO do conjunto (criterio de frescor D2: pior
     * caso — o mesmo {@code min} da {@link SituationLatencyProbe}).
     * Conjunto vazio devolve {@code null} (frescor NO_DATA).
     */
    private static Instant oldest(List<SituationStateRow> rows) {
        return rows.stream().map(SituationStateRow::sourceAt).min(Instant::compareTo).orElse(null);
    }
}
