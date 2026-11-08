package br.com.ziipsan.core.stationops;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Parametros da ED-87, lidos do tenant — nenhum valor de fabrica alem
 * do teto COM FONTE declarado:
 * <pre>
 *   ops.round_interval_by_unit  MAPA unidade -> intervalo maximo entre
 *       rondas, em minutos. A norma fixa a frequencia MINIMA (logo, um
 *       intervalo MAXIMO normativo) e o tenant so REDUZ o intervalo:
 *       esta classe recusa intervalo maior que o teto normativo
 *       entregue pela fiacao depois da conferencia da norma aplicavel
 *       a unidade. O NUMERO do teto NAO consta das fontes do projeto
 *       para rondas em geral — [SEM FONTE] como constante; por isso o
 *       teto e ENTRADA (mapa de tetos), nunca literal aqui.
 *   ops.shift_pattern           LISTA de turnos (codigo, inicio, fim)
 *       do prestador. Sem padrao de fabrica: prestador sem o parametro
 *       nao abre diario (erro explicito).
 *   TURBIDEZ — teto COM FONTE: 120 minutos entre registros por unidade
 *       filtrante com filtracao rapida (Portaria GM/MS 888/2021,
 *       art. 28, §3o, conforme o aceite da ED-87/OPE-02: "no minimo a
 *       cada 2 h"). Este e o unico numero normativo desta entrega.
 * </pre>
 * O chamador (fiacao) le o JSON com a classe {@code Json} do core e
 * entrega os valores tipados; esta classe valida.
 */
public final class StationOpsParameters {

    public static final String ROUND_INTERVAL_BY_UNIT = "ops.round_interval_by_unit";
    public static final String SHIFT_PATTERN = "ops.shift_pattern";

    /** Teto da Portaria GM/MS 888/2021, art. 28, §3o (aceite ED-87): registro de turbidez a cada 2 h no maximo. */
    public static final int TURBIDITY_MAX_INTERVAL_MINUTES = 120;

    private StationOpsParameters() {}

    /** Um turno do padrao do prestador ({@code ops.shift_pattern}). */
    public record ShiftDefinition(String code, LocalTime start, LocalTime end) {
        public ShiftDefinition {
            if (code == null || code.isBlank())
                throw new IllegalArgumentException("codigo do turno e obrigatorio em " + SHIFT_PATTERN);
            if (start == null || end == null)
                throw new IllegalArgumentException("inicio e fim do turno " + code + " sao obrigatorios");
            if (start.equals(end))
                throw new IllegalArgumentException("turno " + code + " com inicio igual ao fim nao e turno");
        }
        /** Turno que cruza a meia-noite (ex.: 22h-06h) e valido: fim menor que o inicio. */
        public boolean crossesMidnight() { return end.isBefore(start); }
    }

    /** Valida o padrao de turnos: presente, nao vazio, codigos unicos. Ausencia = erro explicito. */
    public static List<ShiftDefinition> shiftPattern(List<ShiftDefinition> raw) {
        if (raw == null || raw.isEmpty()) throw new MissingParameterException(SHIFT_PATTERN,
                "padrao de turnos nao configurado para o tenant");
        long distinct = raw.stream().map(ShiftDefinition::code).distinct().count();
        if (distinct != raw.size())
            throw new IllegalArgumentException(SHIFT_PATTERN + " com codigo de turno duplicado");
        return List.copyOf(raw);
    }

    public static ShiftDefinition requireShift(List<ShiftDefinition> pattern, String code) {
        return shiftPattern(pattern).stream().filter(s -> s.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "turno '" + code + "' nao existe no " + SHIFT_PATTERN + " do tenant"));
    }

    /**
     * Intervalo de ronda vigente de UMA unidade: o valor do tenant,
     * validado contra o teto normativo daquela unidade.
     *
     * @param rawByUnit      mapa de {@code ops.round_interval_by_unit} (minutos)
     * @param ceilingByUnit  tetos normativos por unidade, conferidos com a
     *                       norma aplicavel na implantacao [SEM FONTE como
     *                       constante do produto — nenhum numero aqui]
     */
    public static int roundIntervalMinutes(String unitCode, Map<String, Integer> rawByUnit,
                                           Map<String, Integer> ceilingByUnit) {
        if (unitCode == null || unitCode.isBlank())
            throw new IllegalArgumentException("unitCode e obrigatorio");
        if (rawByUnit == null || rawByUnit.get(unitCode) == null)
            throw new MissingParameterException(ROUND_INTERVAL_BY_UNIT,
                    "intervalo de ronda nao configurado para a unidade " + unitCode);
        int value = rawByUnit.get(unitCode);
        if (value <= 0) throw new IllegalArgumentException(
                ROUND_INTERVAL_BY_UNIT + " deve ser maior que zero para " + unitCode + ": " + value);
        if (ceilingByUnit == null || ceilingByUnit.get(unitCode) == null)
            throw new MissingParameterException(ROUND_INTERVAL_BY_UNIT + " (teto normativo)",
                    "teto normativo da frequencia minima de ronda nao conferido para a unidade "
                            + unitCode + " [SEM FONTE]");
        int ceiling = ceilingByUnit.get(unitCode);
        if (ceiling <= 0) throw new IllegalArgumentException(
                "teto normativo de ronda invalido para " + unitCode + ": " + ceiling);
        if (value > ceiling) throw new IllegalArgumentException(
                ROUND_INTERVAL_BY_UNIT + " de " + unitCode + " (" + value + " min) excede a frequencia"
                        + " minima da norma (" + ceiling + " min): o tenant so reduz o intervalo");
        return value;
    }
}
