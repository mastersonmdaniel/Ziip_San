package br.com.ziipsan.operations.metering.telemetry;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * O documento do parâmetro {@code ami.alert_thresholds} (ED-17), lido
 * SOMENTE pela classe {@code Json} do core. Formato publicado desta entrega:
 *
 * <pre>{@code
 * {
 *   "alarmActions": { "CONTINUOUS_FLOW": "BOTH", "REVERSE_FLOW": "OPEN_SERVICE_ORDER" },
 *   "peakHourlyLitres": 850.0
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code alarmActions}: a ação de cada {@link MeterAlarmKind} quando o
 *       alarme dispara ({@link AlarmAction}). Tipo sem ação declarada não
 *       ganha ação presumida: {@link #actionFor} devolve vazio e o alarme é
 *       persistido sem despacho, com a ausência visível;</li>
 *   <li>{@code peakHourlyLitres}: limiar do alerta de pico — o consumo de
 *       uma hora ESTRITAMENTE acima dele dispara o alerta. Ausente, não há
 *       alerta de pico para o tenant.</li>
 * </ul>
 *
 * JSON inválido, ação desconhecida ou limiar negativo são erro explícito:
 * configuração errada não vira "sem limiar" em silêncio.
 */
public record AmiAlertThresholds(Map<MeterAlarmKind, AlarmAction> alarmActions,
                                 BigDecimal peakHourlyLitres) {

    public AmiAlertThresholds {
        alarmActions = alarmActions == null ? Map.of() : Map.copyOf(alarmActions);
        if (peakHourlyLitres != null && peakHourlyLitres.signum() < 0) {
            throw new IllegalArgumentException("ami.alert_thresholds: peakHourlyLitres não pode ser negativo");
        }
    }

    public static AmiAlertThresholds parse(String json) {
        JsonNode root = Json.readTree(json);
        if (root.isMissingNode() || !root.isObject()) {
            throw new IllegalArgumentException("ami.alert_thresholds: documento JSON de limiares é obrigatório");
        }
        Map<MeterAlarmKind, AlarmAction> actions = new EnumMap<>(MeterAlarmKind.class);
        JsonNode actionsNode = root.get("alarmActions");
        if (actionsNode != null && !actionsNode.isNull()) {
            if (!actionsNode.isObject()) {
                throw new IllegalArgumentException("ami.alert_thresholds: alarmActions precisa ser um objeto");
            }
            actionsNode.properties().forEach(entry -> {
                MeterAlarmKind kind;
                AlarmAction action;
                try {
                    kind = MeterAlarmKind.valueOf(entry.getKey());
                    action = AlarmAction.valueOf(entry.getValue().asString());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "ami.alert_thresholds: ação inválida para o alarme " + entry.getKey(), e);
                }
                actions.put(kind, action);
            });
        }
        BigDecimal peak = null;
        JsonNode peakNode = root.get("peakHourlyLitres");
        if (peakNode != null && !peakNode.isNull()) {
            if (!peakNode.isNumber()) {
                throw new IllegalArgumentException("ami.alert_thresholds: peakHourlyLitres precisa ser número");
            }
            peak = peakNode.decimalValue();
        }
        return new AmiAlertThresholds(actions, peak);
    }

    /** A ação configurada para o tipo de alarme; vazia quando o tenant não declarou nenhuma. */
    public Optional<AlarmAction> actionFor(MeterAlarmKind kind) {
        return Optional.ofNullable(alarmActions.get(kind));
    }
}
