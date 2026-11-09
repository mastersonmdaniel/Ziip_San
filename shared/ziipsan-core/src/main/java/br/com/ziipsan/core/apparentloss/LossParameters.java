package br.com.ziipsan.core.apparentloss;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parametros da ED-95 (perdas aparentes), SEMPRE lidos do tenant —
 * nenhum limiar, prazo ou politica e constante de negocio nesta classe:
 * <pre>
 *   registry.property_registry_sync       modo de sincronizacao do cadastro
 *                                         imobiliario municipal declarado
 *                                         pelo tenant/integracao (CAD-103);
 *                                         o aceite nomeia a chave, mas
 *                                         nenhuma fonte enumera o dominio
 *                                         de valores: o valor declarado e
 *                                         devolvido verbatim, sem dominio
 *                                         fechado presumido [A DEFINIR].
 *   audit.unregistered_building_review_days
 *                                         prazo, em dias, da fila de revisao
 *                                         dos cruzamentos (OPE-37); inteiro
 *                                         maior que zero, obrigatorio.
 *   fraud.rules                           documento JSON (objeto plano) com
 *                                         habilitacao e limiares das regras
 *                                         de suspeita por tenant (GES-25);
 *                                         SEM valor de fabrica: regra sem
 *                                         habilitacao declarada nao dispara;
 *                                         regra habilitada sem o limiar dela
 *                                         e erro explicito, nunca limiar
 *                                         assumido.
 *   fraud.self_report_fine_waiver_enabled [CHAVE PROPOSTA — o aceite pede o
 *                                         programa de autodenuncia com isencao
 *                                         de multa "como opcao do tenant" sem
 *                                         nomear a chave] fato declarado
 *                                         true/false; ausencia = programa nao
 *                                         declarado (autodenuncia recusada).
 * </pre>
 * Ausencia de parametro obrigatorio e erro explicito
 * ({@link MissingParameterException}), nunca padrao assumido em silencio —
 * principio 9 do PARAMETROS-DO-TENANT, mesmo desenho de
 * {@code InspectionParameters} da ED-24 e de {@code ContractParameters}
 * da ED-96.
 */
public final class LossParameters {

    public static final String PROPERTY_REGISTRY_SYNC = "registry.property_registry_sync";
    public static final String UNREGISTERED_BUILDING_REVIEW_DAYS = "audit.unregistered_building_review_days";
    public static final String FRAUD_RULES = "fraud.rules";
    /** [CHAVE PROPOSTA] ver javadoc da classe. */
    public static final String SELF_REPORT_FINE_WAIVER_ENABLED = "fraud.self_report_fine_waiver_enabled";

    private LossParameters() {
    }

    /**
     * Modo de sincronizacao declarado; obrigatorio e nao-branco.
     * [A DEFINIR] dominio fechado de valores: nenhuma fonte o enumera,
     * por isso o valor e devolvido verbatim (trim) sem validacao de
     * dominio — fechar o dominio e ato da integracao com fonte.
     */
    public static String propertyRegistrySyncMode(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(PROPERTY_REGISTRY_SYNC,
                "modo de sincronizacao do cadastro imobiliario municipal nao configurado para o tenant");
        return raw.trim();
    }

    /** Prazo da fila de revisao dos cruzamentos (OPE-37), em dias. */
    public static int unregisteredBuildingReviewDays(Integer raw) {
        if (raw == null) throw new MissingParameterException(UNREGISTERED_BUILDING_REVIEW_DAYS,
                "prazo da fila de revisao de edificacoes nao configurado para o tenant");
        if (raw <= 0) throw new IllegalArgumentException(
                UNREGISTERED_BUILDING_REVIEW_DAYS + " deve ser maior que zero: " + raw);
        return raw;
    }

    /**
     * Programa de autodenuncia com isencao de multa como fato declarado:
     * {@code null} quando ausente/em branco (nao declarado). Aceita
     * apenas {@code true}/{@code false} explicitos.
     */
    public static Boolean declaredSelfReportFineWaiverEnabled(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true")) return Boolean.TRUE;
        if (v.equals("false")) return Boolean.FALSE;
        throw new IllegalArgumentException(
                SELF_REPORT_FINE_WAIVER_ENABLED + " deve ser true ou false declarado: " + raw);
    }

    /**
     * Limiares das regras de suspeita (GES-25) resolvidos de
     * {@code fraud.rules}. Cada regra tem habilitacao propria; os
     * campos de limiar so sao exigidos da regra HABILITADA (ver
     * {@link #requireThresholds()}). Nenhum campo tem valor de fabrica.
     */
    public record FraudRuleThresholds(
            boolean zeroConsumptionEnabled, Integer zeroMinConsecutiveCycles,
            boolean abruptDropEnabled, BigDecimal abruptDropMinPercent,
            boolean perEconomyEnabled, BigDecimal perEconomyMaxM3,
            boolean perAreaEnabled, BigDecimal perAreaMaxM3PerSquareMeter,
            boolean stalledMeterEnabled, Integer stalledMinIdenticalReadings,
            boolean undersizedMeterEnabled, BigDecimal undersizedMinPeakPercent,
            boolean meterReplacementEnabled, Integer replacementMaxCyclesAgo,
            BigDecimal replacementMinDropPercent,
            boolean repeatedReadingEnabled, Integer repeatedMinConsecutiveReadings) {

        /** Chaves conhecidas do documento fraud.rules (objeto plano). */
        private static final Map<String, String> KNOWN_KEYS = new LinkedHashMap<>();

        static {
            KNOWN_KEYS.put("zeroConsumption.enabled", "bool");
            KNOWN_KEYS.put("zeroConsumption.minConsecutiveZeroCycles", "int");
            KNOWN_KEYS.put("abruptDrop.enabled", "bool");
            KNOWN_KEYS.put("abruptDrop.minDropPercent", "decimal");
            KNOWN_KEYS.put("perEconomy.enabled", "bool");
            KNOWN_KEYS.put("perEconomy.maxM3PerEconomy", "decimal");
            KNOWN_KEYS.put("perArea.enabled", "bool");
            KNOWN_KEYS.put("perArea.maxM3PerSquareMeter", "decimal");
            KNOWN_KEYS.put("stalledMeter.enabled", "bool");
            KNOWN_KEYS.put("stalledMeter.minConsecutiveIdenticalReadings", "int");
            KNOWN_KEYS.put("undersizedMeter.enabled", "bool");
            KNOWN_KEYS.put("undersizedMeter.minPeakToNominalPercent", "decimal");
            KNOWN_KEYS.put("meterReplacement.enabled", "bool");
            KNOWN_KEYS.put("meterReplacement.maxCyclesAfterReplacement", "int");
            KNOWN_KEYS.put("meterReplacement.minDropPercentAfterReplacement", "decimal");
            KNOWN_KEYS.put("repeatedReading.enabled", "bool");
            KNOWN_KEYS.put("repeatedReading.minConsecutiveRepeatedReadings", "int");
        }

        /**
         * Parse estrito do documento {@code fraud.rules}: objeto JSON
         * plano, chaves conhecidas apenas, valores booleanos ou
         * numericos. Documento ausente/em branco =
         * {@link MissingParameterException} (o tenant nao configurou a
         * biblioteca de regras); chave desconhecida ou valor malformado
         * = recusa nomeada, nunca interpretacao silenciosa.
         */
        public static FraudRuleThresholds parse(String raw) {
            if (raw == null || raw.isBlank()) throw new MissingParameterException(FRAUD_RULES,
                    "limiares das regras de suspeita nao configurados para o tenant (nenhum limiar tem valor de fabrica)");
            Map<String, String> flat = parseFlatJsonObject(raw);
            for (String key : flat.keySet()) {
                if (!KNOWN_KEYS.containsKey(key))
                    throw new IllegalArgumentException(FRAUD_RULES + ": chave desconhecida '" + key + "'");
            }
            return new FraudRuleThresholds(
                    bool(flat, "zeroConsumption.enabled"), integer(flat, "zeroConsumption.minConsecutiveZeroCycles"),
                    bool(flat, "abruptDrop.enabled"), decimal(flat, "abruptDrop.minDropPercent"),
                    bool(flat, "perEconomy.enabled"), decimal(flat, "perEconomy.maxM3PerEconomy"),
                    bool(flat, "perArea.enabled"), decimal(flat, "perArea.maxM3PerSquareMeter"),
                    bool(flat, "stalledMeter.enabled"), integer(flat, "stalledMeter.minConsecutiveIdenticalReadings"),
                    bool(flat, "undersizedMeter.enabled"), decimal(flat, "undersizedMeter.minPeakToNominalPercent"),
                    bool(flat, "meterReplacement.enabled"), integer(flat, "meterReplacement.maxCyclesAfterReplacement"),
                    decimal(flat, "meterReplacement.minDropPercentAfterReplacement"),
                    bool(flat, "repeatedReading.enabled"), integer(flat, "repeatedReading.minConsecutiveRepeatedReadings"));
        }

        /**
         * Regra habilitada sem o limiar dela e configuracao incompleta:
         * erro explicito nomeando a chave de {@code fraud.rules} — o
         * motor nunca substitui limiar ausente por numero presumido.
         */
        public void requireThresholds() {
            if (zeroConsumptionEnabled) requireInt("zeroConsumption.minConsecutiveZeroCycles", zeroMinConsecutiveCycles);
            if (abruptDropEnabled) requireDec("abruptDrop.minDropPercent", abruptDropMinPercent);
            if (perEconomyEnabled) requireDec("perEconomy.maxM3PerEconomy", perEconomyMaxM3);
            if (perAreaEnabled) requireDec("perArea.maxM3PerSquareMeter", perAreaMaxM3PerSquareMeter);
            if (stalledMeterEnabled) requireInt("stalledMeter.minConsecutiveIdenticalReadings", stalledMinIdenticalReadings);
            if (undersizedMeterEnabled) requireDec("undersizedMeter.minPeakToNominalPercent", undersizedMinPeakPercent);
            if (meterReplacementEnabled) {
                requireInt("meterReplacement.maxCyclesAfterReplacement", replacementMaxCyclesAgo);
                requireDec("meterReplacement.minDropPercentAfterReplacement", replacementMinDropPercent);
            }
            if (repeatedReadingEnabled) requireInt("repeatedReading.minConsecutiveRepeatedReadings", repeatedMinConsecutiveReadings);
        }

        private static void requireInt(String key, Integer v) {
            if (v == null) throw new MissingParameterException(FRAUD_RULES,
                    "regra habilitada sem limiar '" + key + "' no documento do tenant");
            if (v <= 0) throw new IllegalArgumentException(FRAUD_RULES + ": '" + key + "' deve ser maior que zero: " + v);
        }

        private static void requireDec(String key, BigDecimal v) {
            if (v == null) throw new MissingParameterException(FRAUD_RULES,
                    "regra habilitada sem limiar '" + key + "' no documento do tenant");
            if (v.signum() < 0) throw new IllegalArgumentException(FRAUD_RULES + ": '" + key + "' nao pode ser negativo: " + v);
        }

        private static boolean bool(Map<String, String> flat, String key) {
            String v = flat.get(key);
            if (v == null) return false;
            if (v.equals("true")) return true;
            if (v.equals("false")) return false;
            throw new IllegalArgumentException(FRAUD_RULES + ": '" + key + "' deve ser true ou false: " + v);
        }

        private static Integer integer(Map<String, String> flat, String key) {
            String v = flat.get(key);
            if (v == null) return null;
            try {
                return Integer.valueOf(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(FRAUD_RULES + ": '" + key + "' deve ser inteiro: " + v);
            }
        }

        private static BigDecimal decimal(Map<String, String> flat, String key) {
            String v = flat.get(key);
            if (v == null) return null;
            try {
                return new BigDecimal(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(FRAUD_RULES + ": '" + key + "' deve ser numerico: " + v);
            }
        }

        /**
         * Parser minimo e estrito de objeto JSON plano
         * ({@code {"chave": valor, ...}} com valores booleanos ou
         * numericos, sem aninhamento): o documento fraud.rules e, por
         * desenho do catalogo, um mapa plano de limiares; qualquer
         * estrutura alem disso e recusada em vez de interpretada.
         */
        private static Map<String, String> parseFlatJsonObject(String raw) {
            String s = raw.trim();
            if (!s.startsWith("{") || !s.endsWith("}"))
                throw new IllegalArgumentException(FRAUD_RULES + ": documento deve ser um objeto JSON plano");
            String body = s.substring(1, s.length() - 1).trim();
            Map<String, String> out = new LinkedHashMap<>();
            if (body.isEmpty()) return out;
            int i = 0;
            int n = body.length();
            while (i < n) {
                if (body.charAt(i) != '"')
                    throw new IllegalArgumentException(FRAUD_RULES + ": chave esperada na posicao " + i);
                int endKey = body.indexOf('"', i + 1);
                if (endKey < 0) throw new IllegalArgumentException(FRAUD_RULES + ": chave nao fechada");
                String key = body.substring(i + 1, endKey);
                int colon = body.indexOf(':', endKey);
                if (colon < 0) throw new IllegalArgumentException(FRAUD_RULES + ": ':' esperado apos '" + key + "'");
                int comma = body.indexOf(',', colon);
                String value = (comma < 0 ? body.substring(colon + 1) : body.substring(colon + 1, comma)).trim();
                if (value.isEmpty() || value.startsWith("{") || value.startsWith("[") || value.startsWith("\""))
                    throw new IllegalArgumentException(FRAUD_RULES + ": valor de '" + key
                            + "' deve ser booleano ou numerico (objeto plano, sem aninhamento nem texto)");
                if (out.put(key, value) != null)
                    throw new IllegalArgumentException(FRAUD_RULES + ": chave duplicada '" + key + "'");
                i = (comma < 0 ? n : comma + 1);
                while (i < n && Character.isWhitespace(body.charAt(i))) i++;
                if (comma >= 0 && i >= n)
                    throw new IllegalArgumentException(FRAUD_RULES + ": virgula final sem chave seguinte");
            }
            return out;
        }
    }
}
