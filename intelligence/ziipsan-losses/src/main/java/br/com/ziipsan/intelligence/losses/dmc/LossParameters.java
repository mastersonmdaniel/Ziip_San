package br.com.ziipsan.intelligence.losses.dmc;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Parametros do modulo de perdas (ED-85; OPE-01/OPE-05), SEMPRE do
 * tenant — nenhum valor de fabrica alem do que o proprio aceite nomeia:
 * <pre>
 *   loss.dmc_target_l_per_conn_day   meta de perdas por DMC, em litros por
 *                                    ligacao por dia — o aceite nomeia a
 *                                    chave, NAO o valor: ausente = meta
 *                                    inexistente (NO_TARGET), nunca um
 *                                    numero presumido.
 *   loss.night_window                janela da vazao minima noturna;
 *                                    padrao DO ACEITE: 02:00 a 04:00.
 *   loss.legit_night_use_l_per_conn_h consumo noturno legitimo, em litros
 *                                    por ligacao por hora — o aceite diz
 *                                    expressamente "sem padrao de norma":
 *                                    ausente = MissingParameterException.
 *   loss.unbilled_estimate_method    metodo do volume autorizado nao
 *                                    faturado: MEASURED, FLOW_TIME ou
 *                                    FIXED_BY_SERVICE_TYPE — escolha do
 *                                    tenant, sem padrao de fabrica.
 * </pre>
 */
public final class LossParameters {

    public static final String DMC_TARGET = "loss.dmc_target_l_per_conn_day";
    public static final String NIGHT_WINDOW = "loss.night_window";
    public static final String LEGIT_NIGHT_USE = "loss.legit_night_use_l_per_conn_h";
    public static final String UNBILLED_METHOD = "loss.unbilled_estimate_method";

    /** Padrao NOMEADO no aceite da ED-85 (janela 02:00 a 04:00). */
    public static final String NIGHT_WINDOW_DEFAULT = "02:00-04:00";

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private LossParameters() {}

    /** Janela noturna [inicio, fim) no mesmo dia civil. */
    public record NightWindow(LocalTime start, LocalTime end) {
        public NightWindow {
            if (start == null || end == null)
                throw new IllegalArgumentException("janela noturna exige inicio e fim");
            if (!start.isBefore(end))
                throw new IllegalArgumentException("janela noturna exige inicio anterior ao fim (sem virada de dia nesta entrega): "
                        + start + "-" + end);
        }
        public boolean contains(LocalTime t) { return !t.isBefore(start) && t.isBefore(end); }
        public double hours() {
            return (end.toSecondOfDay() - start.toSecondOfDay()) / 3600.0;
        }
    }

    /** Metodo de estimativa do volume autorizado nao faturado (OPE-05). */
    public enum UnbilledEstimateMethod { MEASURED, FLOW_TIME, FIXED_BY_SERVICE_TYPE }

    /** Janela: ausente usa o padrao do aceite; presente exige "HH:mm-HH:mm". */
    public static NightWindow nightWindow(String raw) {
        String value = (raw == null || raw.isBlank()) ? NIGHT_WINDOW_DEFAULT : raw.trim();
        String[] parts = value.split("-");
        if (parts.length != 2) throw new IllegalArgumentException(
                NIGHT_WINDOW + " exige o formato HH:mm-HH:mm: " + raw);
        try {
            return new NightWindow(LocalTime.parse(parts[0].trim(), HHMM),
                    LocalTime.parse(parts[1].trim(), HHMM));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(NIGHT_WINDOW + " invalida: " + raw, e);
        }
    }

    /** Consumo noturno legitimo: SEM padrao — ausente e erro explicito. */
    public static double legitNightUseLPerConnH(Double raw) {
        if (raw == null) throw new MissingParameterException(LEGIT_NIGHT_USE,
                "consumo noturno legitimo nao configurado para o tenant; o aceite nao fixa padrao de norma");
        if (raw < 0 || Double.isNaN(raw)) throw new IllegalArgumentException(
                LEGIT_NIGHT_USE + " nao pode ser negativo: " + raw);
        return raw;
    }

    /** Meta do DMC quando o tenant a declarou; vazia = NO_TARGET (nunca presumida). */
    public static Optional<Double> dmcTargetLPerConnDay(Double raw) {
        if (raw == null) return Optional.empty();
        if (raw <= 0 || Double.isNaN(raw)) throw new IllegalArgumentException(
                DMC_TARGET + " deve ser positiva quando declarada: " + raw);
        return Optional.of(raw);
    }

    /** Metodo do volume nao faturado: escolha do tenant, sem padrao de fabrica. */
    public static UnbilledEstimateMethod unbilledEstimateMethod(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(UNBILLED_METHOD,
                "metodo de estimativa do volume autorizado nao faturado nao configurado para o tenant");
        try {
            return UnbilledEstimateMethod.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(UNBILLED_METHOD + " desconhecido: " + raw
                    + " (esperado MEASURED, FLOW_TIME ou FIXED_BY_SERVICE_TYPE)", e);
        }
    }
}
