package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.BatchSendPolicy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Parametros da emissao simultanea (ED-19) lidos do mapa de parametros do
 * tenant ja resolvido por quem chama (o servico de parametros do tenant e
 * de outra tarefa; aqui so se interpreta, com a disciplina das irmaes:
 * parametro ausente ou invalido RECUSA — nunca se assume valor de fabrica
 * silencioso para dinheiro ou para lote de envio).
 *
 * <ul>
 *   <li>{@code field.send_every_n} — envio a cada N imoveis (inteiro >= 1);</li>
 *   <li>{@code bill.field_divergence_double_approval_limit} — limite do
 *       |delta| do total para a dupla aprovacao da divergencia (P2.20);</li>
 *   <li>{@code field.printer_min_battery_pct} — bateria minima para sair
 *       em rota imprimindo (inteiro 0..100).</li>
 * </ul>
 * O nivel de assinatura por tipo continua sendo o da P3.34
 * ({@code sign.min_level.<tipo>}) — nao e duplicado aqui.
 */
public final class FieldEmissionParameters {

    public static final String PRINTER_MIN_BATTERY_KEY = "field.printer_min_battery_pct";

    private final Map<String, String> values;

    private FieldEmissionParameters(Map<String, String> values) {
        this.values = values;
    }

    public static FieldEmissionParameters from(Map<String, String> resolvedValues) {
        Objects.requireNonNull(resolvedValues, "mapa de parametros resolvidos e obrigatorio");
        return new FieldEmissionParameters(Map.copyOf(resolvedValues));
    }

    public BatchSendPolicy batchSendPolicy() {
        return new BatchSendPolicy(requirePositiveInt(BatchSendPolicy.SEND_EVERY_N_KEY));
    }

    public BigDecimal doubleApprovalLimit() {
        String raw = require(DivergenceApprovalService.DOUBLE_APPROVAL_LIMIT_KEY);
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.signum() < 0) {
                throw new IllegalArgumentException("limite negativo");
            }
            return value;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Parametro '"
                    + DivergenceApprovalService.DOUBLE_APPROVAL_LIMIT_KEY + "' invalido: " + raw, e);
        }
    }

    public int printerMinBatteryPct() {
        int value = requireInt(PRINTER_MIN_BATTERY_KEY);
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(
                    "Parametro '" + PRINTER_MIN_BATTERY_KEY + "' fora de [0, 100]: " + value);
        }
        return value;
    }

    private String require(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Parametro obrigatorio ausente: " + key
                    + " (sem valor assumido para a emissao simultanea)");
        }
        return value;
    }

    private int requireInt(String key) {
        String raw = require(key);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parametro '" + key + "' deveria ser inteiro: " + raw, e);
        }
    }

    private int requirePositiveInt(String key) {
        int value = requireInt(key);
        if (value < 1) {
            throw new IllegalArgumentException("Parametro '" + key + "' deve ser >= 1: " + value);
        }
        return value;
    }
}
