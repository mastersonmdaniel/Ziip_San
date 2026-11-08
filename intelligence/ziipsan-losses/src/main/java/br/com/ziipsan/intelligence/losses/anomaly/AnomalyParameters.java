package br.com.ziipsan.intelligence.losses.anomaly;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Parametros da deteccao de anomalia explicavel (ED-94; OPE-36;
 * ADR-0042), SEMPRE do tenant — o aceite nomeia DUAS chaves e nao fixa
 * valor para nenhuma delas, logo NENHUMA tem padrao de fabrica neste
 * codigo (ausencia = {@link MissingParameterException}, o padrao do
 * projeto desde a ED-85/ED-17):
 * <pre>
 *   anomaly.rules_enabled   regras ligadas, separadas por virgula, pelos
 *                           codigos de {@link AnomalyRule} (ou ALL).
 *                           Ex.: "SAME_HOUR_WEEKDAY_DEVIATION,CONTINUOUS_CONSUMPTION".
 *   anomaly.sensitivity     sensibilidade: UM UNICO parametro do
 *                           aceite, cujo VALOR e um documento
 *                           "chave=valor;..." que se DECOMPOE nos 5
 *                           limiares nomeados abaixo — a decomposicao
 *                           e desta entrega (o aceite nomeia a chave,
 *                           nao os campos), formato PROPOSTO e marcado
 *                           [A DEFINIR] para confirmacao na integracao
 *                           (pendencia P-ED94-03a). Cada componente tem
 *                           semantica e faixa proprias, validadas no
 *                           record {@code Sensitivity}: fora da faixa
 *                           e RECUSA explicita nomeando o campo — nada
 *                           e aceito em silencio nem clampado:
 *                             deviationK=&lt;k&gt;              desvios robustos (MAD)
 *                                                           que disparam o desvio;
 *                             minBaselineSamples=&lt;n&gt;    minimo de amostras
 *                                                           historicas no mesmo
 *                                                           slot (hora + dia da
 *                                                           semana);
 *                             continuousHours=&lt;n&gt;       horas seguidas com
 *                                                           consumo &gt; 0;
 *                             zeroHours=&lt;n&gt;             horas seguidas com
 *                                                           consumo = 0;
 *                             pressureDropPercent=&lt;p&gt;   queda percentual
 *                                                           subita de pressao.
 *                           Nenhum campo tem valor presumido: campo
 *                           ausente, desconhecido ou invalido e erro
 *                           explicito nomeando o campo — nenhum limiar
 *                           nasce como "verdade de dominio" no codigo.
 * </pre>
 */
public final class AnomalyParameters {

    public static final String RULES_ENABLED = "anomaly.rules_enabled";
    public static final String SENSITIVITY = "anomaly.sensitivity";

    private AnomalyParameters() {}

    /**
     * Limiares das regras — a DECOMPOSICAO documentada do parametro
     * unico {@code anomaly.sensitivity} (correcao da revisao cruzada:
     * decomposicao apresentada como tal, com teto validado por campo).
     * Semantica e faixa de cada componente:
     * <ul>
     *   <li>{@code deviationK} — quantos sigmas robustos (MAD) de
     *       distancia da mediana disparam o desvio. Faixa (0, 20]:
     *       acima de 20 sigmas a regra esta desligada na pratica —
     *       quem quer a regra desligada usa
     *       {@code anomaly.rules_enabled}, nao um limiar infinito.</li>
     *   <li>{@code minBaselineSamples} — minimo de amostras anteriores
     *       no mesmo slot para julgar um ponto. Faixa [2, 520]:
     *       520 slots semanais = 10 anos de historico.</li>
     *   <li>{@code continuousHours} — horas contiguas com consumo &gt; 0
     *       que disparam a corrida continua. Faixa [2, 8760]:
     *       8760h = 1 ano de amostras horarias.</li>
     *   <li>{@code zeroHours} — idem, com consumo = 0. Faixa
     *       [2, 8760].</li>
     *   <li>{@code pressureDropPercent} — queda relativa percentual
     *       entre amostras adjacentes (passo de 1h) que dispara a
     *       queda subita. Faixa (0, 100]: e um percentual — acima de
     *       100 nada significa. A fracao equivalente vive em
     *       (0, 1] ({@link #pressureDropFraction()}).</li>
     * </ul>
     * Os intervalos sao SANIDADE aritmetica/operacional (positivos,
     * finitos e com teto), nao valores de dominio: nenhum numero
     * concreto de limiar nasce neste codigo. Fora da faixa, a recusa
     * e {@link IllegalArgumentException} nomeando campo, valor e
     * faixa — nunca aceite em silencio, nunca clamp.
     */
    public record Sensitivity(double deviationK, int minBaselineSamples,
                              int continuousHours, int zeroHours,
                              double pressureDropPercent) {

        /** Teto de sanidade de {@code deviationK} (sigmas robustos). */
        public static final double MAX_DEVIATION_K = 20.0;
        /** Teto de sanidade de {@code minBaselineSamples} (520 semanas = 10 anos). */
        public static final int MAX_BASELINE_SAMPLES = 520;
        /** Teto de sanidade das corridas em horas (8760h = 1 ano). */
        public static final int MAX_RUN_HOURS = 8760;

        public Sensitivity {
            if (Double.isNaN(deviationK) || Double.isInfinite(deviationK)
                    || deviationK <= 0 || deviationK > MAX_DEVIATION_K)
                throw new IllegalArgumentException(
                        "deviationK deve estar em (0, " + MAX_DEVIATION_K + "]: " + deviationK);
            if (minBaselineSamples < 2 || minBaselineSamples > MAX_BASELINE_SAMPLES)
                throw new IllegalArgumentException(
                        "minBaselineSamples deve estar em [2, " + MAX_BASELINE_SAMPLES
                                + "] (mediana/MAD sem historico nao e base): "
                                + minBaselineSamples);
            if (continuousHours < 2 || continuousHours > MAX_RUN_HOURS)
                throw new IllegalArgumentException(
                        "continuousHours deve estar em [2, " + MAX_RUN_HOURS + "]: "
                                + continuousHours);
            if (zeroHours < 2 || zeroHours > MAX_RUN_HOURS)
                throw new IllegalArgumentException(
                        "zeroHours deve estar em [2, " + MAX_RUN_HOURS + "]: " + zeroHours);
            if (Double.isNaN(pressureDropPercent) || Double.isInfinite(pressureDropPercent)
                    || pressureDropPercent <= 0 || pressureDropPercent > 100)
                throw new IllegalArgumentException(
                        "pressureDropPercent deve estar em (0, 100]: " + pressureDropPercent);
        }

        /** Fracao (0..1] equivalente ao percentual de queda de pressao. */
        public double pressureDropFraction() { return pressureDropPercent / 100.0; }
    }

    /** Regras ligadas: ausente/vazio e erro explicito; codigo desconhecido tambem. */
    public static Set<AnomalyRule> rulesEnabled(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(RULES_ENABLED,
                "nenhuma regra de anomalia configurada para o tenant; o aceite nao fixa padrao");
        String value = raw.trim();
        if (value.equalsIgnoreCase("ALL")) return EnumSet.allOf(AnomalyRule.class);
        EnumSet<AnomalyRule> enabled = EnumSet.noneOf(AnomalyRule.class);
        for (String token : value.split(",")) {
            String code = token.trim();
            if (code.isEmpty()) continue;
            try {
                enabled.add(AnomalyRule.valueOf(code));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(RULES_ENABLED + " com regra desconhecida: " + code
                        + " (esperado codigo de AnomalyRule ou ALL)", e);
            }
        }
        if (enabled.isEmpty()) throw new MissingParameterException(RULES_ENABLED,
                "lista de regras vazia apos a leitura de: " + raw);
        return Set.copyOf(enabled);
    }

    /** Sensibilidade: ausente e erro explicito; o documento exige os 5 campos nomeados. */
    public static Sensitivity sensitivity(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(SENSITIVITY,
                "sensibilidade nao configurada para o tenant; nenhum limiar e presumido");
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : raw.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException(
                    SENSITIVITY + " exige pares chave=valor separados por ';': trecho '" + p + "'");
            String key = p.substring(0, eq).trim();
            String val = p.substring(eq + 1).trim();
            if (fields.put(key, val) != null) throw new IllegalArgumentException(
                    SENSITIVITY + " com campo repetido: " + key);
        }
        Set<String> expected = Set.of("deviationK", "minBaselineSamples",
                "continuousHours", "zeroHours", "pressureDropPercent");
        for (String key : fields.keySet())
            if (!expected.contains(key)) throw new IllegalArgumentException(
                    SENSITIVITY + " com campo desconhecido: " + key
                            + " (esperados: deviationK, minBaselineSamples, continuousHours, zeroHours, pressureDropPercent)");
        return new Sensitivity(
                parseDouble(fields, "deviationK"),
                parseInt(fields, "minBaselineSamples"),
                parseInt(fields, "continuousHours"),
                parseInt(fields, "zeroHours"),
                parseDouble(fields, "pressureDropPercent"));
    }

    private static String required(Map<String, String> fields, String key) {
        String v = fields.get(key);
        if (v == null || v.isBlank()) throw new MissingParameterException(SENSITIVITY,
                "campo obrigatorio da sensibilidade ausente: " + key
                        + " (nenhum limiar e presumido; configure os 5 campos)");
        return v;
    }

    private static double parseDouble(Map<String, String> fields, String key) {
        String v = required(fields, key);
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(SENSITIVITY + ": campo " + key + " nao e numero: " + v, e);
        }
    }

    private static int parseInt(Map<String, String> fields, String key) {
        String v = required(fields, key);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(SENSITIVITY + ": campo " + key + " nao e inteiro: " + v, e);
        }
    }

    /** Porta de leitura dos parametros do tenant (a fiacao liga ao catalogo de parametros). */
    public interface AnomalyParameterSource {
        String rulesEnabledRaw();
        String sensitivityRaw();
    }

    /** Configuracao resolvida e imutavel de uma avaliacao (snapshot auditavel do alerta). */
    public record AnomalyConfig(Set<AnomalyRule> rulesEnabled, Sensitivity sensitivity) {
        public AnomalyConfig {
            Objects.requireNonNull(rulesEnabled, "regras ligadas obrigatorias");
            Objects.requireNonNull(sensitivity, "sensibilidade obrigatoria");
            rulesEnabled = Set.copyOf(rulesEnabled);
        }

        public static AnomalyConfig from(AnomalyParameterSource source) {
            Objects.requireNonNull(source, "fonte de parametros obrigatoria");
            return new AnomalyConfig(
                    AnomalyParameters.rulesEnabled(source.rulesEnabledRaw()),
                    AnomalyParameters.sensitivity(source.sensitivityRaw()));
        }

        public boolean isEnabled(AnomalyRule rule) { return rulesEnabled.contains(rule); }

        /** Representacao textual estavel do snapshot (vai gravada no alerta para auditoria). */
        public String snapshot() {
            return "rules=" + rulesEnabled.stream().map(Enum::name).sorted().toList()
                    + ";deviationK=" + sensitivity.deviationK()
                    + ";minBaselineSamples=" + sensitivity.minBaselineSamples()
                    + ";continuousHours=" + sensitivity.continuousHours()
                    + ";zeroHours=" + sensitivity.zeroHours()
                    + ";pressureDropPercent=" + sensitivity.pressureDropPercent();
        }
    }
}
