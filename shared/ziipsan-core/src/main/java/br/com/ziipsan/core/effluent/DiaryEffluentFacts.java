package br.com.ziipsan.core.effluent;

import br.com.ziipsan.core.stationops.DiaryReading;
import br.com.ziipsan.core.stationops.ShiftLog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Leitura do diario de operacao da ED-87 para a ED-105 (aceite:
 * "volumes e paradas lidos do diario de operacao da ED-87"). Le as
 * classes REAIS da ED-87 ({@link ShiftLog}) — nenhum diario, leitura
 * ou ocorrencia e duplicado aqui:
 * <ul>
 *   <li><b>vazoes:</b> as leituras {@code FLOW} lancadas no diario,
 *       devolvidas COMO LANCADAS. Vazao e leitura instantanea: este
 *       extrator NAO integra vazao em volume (integrar exigiria um
 *       intervalo de integracao que o diario nao fixa — volume
 *       derivado seria numero inventado); o volume que vale para o
 *       lodo e o do proprio registro (pesagem/estimativa, OPE-25);</li>
 *   <li><b>paradas no diario:</b> equipamentos registrados
 *       {@code OUT_OF_SERVICE} no turno e as ocorrencias do diario —
 *       a parada de estacao do supervisório (tabela da ED-17) e
 *       consumida pela integracao, nao por este extrator.</li>
 * </ul>
 */
public final class DiaryEffluentFacts {

    public record Facts(
            UUID unitId,
            int shiftLogCount,
            List<BigDecimal> flowReadings,
            List<String> outOfServiceEquipment,
            int occurrenceCount) {
        public Facts {
            flowReadings = flowReadings == null ? List.of() : List.copyOf(flowReadings);
            outOfServiceEquipment = outOfServiceEquipment == null
                    ? List.of() : List.copyOf(outOfServiceEquipment);
        }

        public static Facts empty(UUID unitId) {
            return new Facts(unitId, 0, List.of(), List.of(), 0);
        }
    }

    private DiaryEffluentFacts() {}

    /** Fatos da unidade nos diarios dados; diarios de outra unidade sao ignorados. */
    public static Facts extract(UUID unitId, List<ShiftLog> logs) {
        if (unitId == null) throw new IllegalArgumentException("unidade e obrigatoria");
        if (logs == null) throw new IllegalArgumentException("diarios sao obrigatorios (pode ser lista vazia)");
        List<ShiftLog> own = logs.stream().filter(l -> l.unit().id().equals(unitId)).toList();
        List<BigDecimal> flows = own.stream()
                .flatMap(l -> l.readings().stream())
                .filter(r -> r.kind() == DiaryReading.Kind.FLOW)
                .map(DiaryReading::value)
                .toList();
        List<String> outOfService = own.stream()
                .flatMap(l -> l.equipment().stream())
                .filter(e -> e.situation() == ShiftLog.EquipmentSituation.OUT_OF_SERVICE)
                .map(ShiftLog.EquipmentState::equipmentCode)
                .toList();
        int occurrences = own.stream().mapToInt(l -> l.occurrences().size()).sum();
        return new Facts(unitId, own.size(), flows, outOfService, occurrences);
    }
}
