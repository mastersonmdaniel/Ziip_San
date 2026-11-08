package br.com.ziipsan.reporting.situation;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-93: o retrato que o console mostra — montado SOMENTE de linhas
 * da tabela de estado (mais os indicadores lidos do registro da
 * P3.41, quando ligados). Os seis conjuntos do aceite aparecem
 * separados. As OS aparecem nos conjuntos que o aceite nomeia ("OS
 * em execucao e vencidas") como predicados INDEPENDENTES (correcao
 * D1 da revisao ED-93-REV-1): {@code workOrdersExecuting} e
 * {@code workOrdersOverdue} NAO sao disjuntas — a OS em execucao E
 * vencida esta nas duas listas; {@code workOrdersOtherOpen} e o
 * residual (aberta nem em execucao nem vencida). {@link #allRows()}
 * devolve cada linha UMA vez (sem duplicar a sobreposicao).
 *
 * <p>{@code freshnessByKind} e calculado contra a
 * {@link SituationLatencyPolicy} configurada, pelo fato MAIS ANTIGO
 * de cada conjunto (pior caso — correcao D2 da mesma revisao, criterio
 * unificado com a sonda de latencia): conjunto sem linha e
 * {@code NO_DATA} — ausencia declarada, nunca frescor presumido.
 */
public record SituationSnapshot(UUID tenantId, Instant generatedAt,
                                List<SituationStateRow> incidents,
                                List<SituationStateRow> workOrdersExecuting,
                                List<SituationStateRow> workOrdersOverdue,
                                List<SituationStateRow> workOrdersOtherOpen,
                                List<SituationStateRow> crews,
                                List<SituationStateRow> telemetryAlarms,
                                List<SituationStateRow> reservoirs,
                                List<SituationStateRow> unitsOutOfOperation,
                                List<SituationIndicators.ConsoleIndicatorView> indicators,
                                Map<SituationItemKind, SituationLatencyPolicy.Freshness> freshnessByKind) {

    public SituationSnapshot {
        Objects.requireNonNull(tenantId, "tenant obrigatorio");
        Objects.requireNonNull(generatedAt, "instante da leitura obrigatorio");
        incidents = List.copyOf(incidents);
        workOrdersExecuting = List.copyOf(workOrdersExecuting);
        workOrdersOverdue = List.copyOf(workOrdersOverdue);
        workOrdersOtherOpen = List.copyOf(workOrdersOtherOpen);
        crews = List.copyOf(crews);
        telemetryAlarms = List.copyOf(telemetryAlarms);
        reservoirs = List.copyOf(reservoirs);
        unitsOutOfOperation = List.copyOf(unitsOutOfOperation);
        indicators = List.copyOf(indicators);
        EnumMap<SituationItemKind, SituationLatencyPolicy.Freshness> copy = new EnumMap<>(SituationItemKind.class);
        copy.putAll(freshnessByKind);
        freshnessByKind = Map.copyOf(copy);
    }

    /**
     * Todas as linhas do retrato (para o mapa e para contagens), cada
     * uma UMA vez: como execucao e vencida se sobrepoem (D1), a linha
     * da combinacao e deduplicada aqui — mapa e contagens nunca a
     * veem em dobro.
     */
    public List<SituationStateRow> allRows() {
        return java.util.stream.Stream.of(incidents, workOrdersExecuting, workOrdersOverdue,
                        workOrdersOtherOpen, crews, telemetryAlarms, reservoirs, unitsOutOfOperation)
                .flatMap(List::stream)
                .distinct()
                .toList();
    }
}
