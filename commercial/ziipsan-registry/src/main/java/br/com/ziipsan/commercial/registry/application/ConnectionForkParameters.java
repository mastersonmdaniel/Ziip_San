package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Bifurcações normativas da ligação (ED-13; ADR-0038: toda bifurcação
 * normativa vira parâmetro do tenant, com padrão de fábrica MAIS
 * PROTETIVO ao usuário), no padrão de {@code CutoffParameters}/
 * {@code ServiceOrderParameters}: esta classe só interpreta os valores
 * aceitos de {@code parameter_versions} (chave -> JSON canônico).
 *
 * <p>As chaves são AS DO CATÁLOGO ({@code PARAMETROS-DO-TENANT.md},
 * seção de bifurcações do ADR-0038, que atribui as três à ED-13):
 * <ul>
 *   <li>{@code connection.voluntary_suppression_with_debt} — supressão
 *     voluntária (a pedido) de ligação de QUALQUER tipo com débito em
 *     aberto: {@code ALLOW} (padrão, mais protetivo: permitida MANTENDO
 *     o débito em cobrança — é o padrão protetivo literal do ADR-0038),
 *     {@code ALLOW_WITH_PERMISSION} (com débito, só com permissão
 *     registrada na OS) ou {@code BLOCK} (com débito, recusada). Sem
 *     débito em aberto, as três opções permitem.</li>
 *   <li>{@code sewer.out_of_use_on_water_cut} — efeito na ligação de
 *     esgoto quando a água é cortada ou suprimida: {@code AUTOMATIC}
 *     (padrão: o esgoto vai a 'fora de uso' na MESMA OS),
 *     {@code SERVICE_ORDER_ONLY} (só por OS própria do esgoto) ou
 *     {@code NONE} (sem efeito).</li>
 *   <li>{@code sewer.alternative_pct_enabled} (FAT-70) — liga/desliga o
 *     percentual de esgoto sobre fonte alternativa. Lida e exposta
 *     aqui; a APLICAÇÃO do percentual é do cálculo tarifário (o valor
 *     do percentual não tem chave no catálogo — vem da tarifa/ERI), por
 *     isso esta classe não altera a regra de volumes do ciclo (ver a
 *     justificativa formal no RELATORIO-ED-13). Ausência de valor
 *     aceito = {@code null} ("ligado onde a ERI previr": sem o perfil
 *     da ERI entregue a esta classe, nenhum dos dois estados é
 *     presumido).</li>
 * </ul>
 *
 * <p>Valor aceito desconhecido ou de tipo JSON errado é erro explícito,
 * nunca o padrão silencioso: padrão só vale na AUSÊNCIA de valor aceito.
 *
 * <p>PENDÊNCIA DE REGISTRO (ver RELATORIO-ED-13): as chaves acima
 * ainda não têm linha em {@code parameter_definitions}/
 * {@code parameter_legal_limits} (core V3, P0.19) nem slot de migration
 * reservado à ED-13 no mapa do plano (§3) — a entrega não cria
 * migration sem reserva; o registro das definições é o passo de
 * integração que destrava a leitura em produção.
 */
public final class ConnectionForkParameters {

    public static final String VOLUNTARY_SUPPRESSION_WITH_DEBT = "connection.voluntary_suppression_with_debt";
    public static final String SEWER_OUT_OF_USE_ON_WATER_CUT = "sewer.out_of_use_on_water_cut";
    public static final String SEWER_ALTERNATIVE_PCT_ENABLED = "sewer.alternative_pct_enabled";

    /** Supressão voluntária com débito; o padrão é a opção mais protetiva do ADR-0038. */
    public enum VoluntarySuppressionWithDebt {
        ALLOW,
        ALLOW_WITH_PERMISSION,
        BLOCK
    }

    /** Esgoto 'fora de uso' no corte/supressão da água; o padrão é AUTOMATIC. */
    public enum SewerOutOfUseOnWaterCut {
        AUTOMATIC,
        SERVICE_ORDER_ONLY,
        NONE
    }

    /** O que o corte/supressão da água faz com a ligação de esgoto, conforme a bifurcação. */
    public enum SewerCascadeDecision {
        /** AUTOMATIC: aplicar o efeito na mesma OS. */
        APPLY_IN_SAME_ORDER,
        /** SERVICE_ORDER_ONLY: nenhuma cascata; o esgoto muda por OS própria. */
        SEPARATE_ORDER_REQUIRED,
        /** NONE: nenhum efeito sobre o esgoto. */
        NO_EFFECT
    }

    /** Padrão de fábrica mais protetivo (ADR-0038): permitida mantendo o débito em cobrança. */
    public static final VoluntarySuppressionWithDebt SUPPRESSION_DEFAULT =
            VoluntarySuppressionWithDebt.ALLOW;
    /** Padrão de fábrica do catálogo. */
    public static final SewerOutOfUseOnWaterCut OUT_OF_USE_DEFAULT =
            SewerOutOfUseOnWaterCut.AUTOMATIC;

    private ConnectionForkParameters() {
    }

    public record Values(VoluntarySuppressionWithDebt suppressionWithDebt,
                         SewerOutOfUseOnWaterCut sewerOutOfUseOnWaterCut,
                         Boolean alternativePctEnabled) {

        /**
         * A supressão voluntária pode ocorrer? Sem débito em aberto,
         * sempre; com débito, conforme a bifurcação (ALLOW: sim, e o
         * débito PERMANECE em cobrança; ALLOW_WITH_PERMISSION: só com a
         * permissão registrada; BLOCK: não).
         */
        public boolean suppressionAllowed(boolean hasOpenDebt, boolean permissionGranted) {
            if (!hasOpenDebt) {
                return true;
            }
            return switch (suppressionWithDebt) {
                case ALLOW -> true;
                case ALLOW_WITH_PERMISSION -> permissionGranted;
                case BLOCK -> false;
            };
        }

        /** Com débito e supressão permitida, o débito continua exigível (padrão protetivo do ADR-0038). */
        public boolean debtRemainsCollectible(boolean hasOpenDebt) {
            return hasOpenDebt && suppressionWithDebt != VoluntarySuppressionWithDebt.BLOCK;
        }

        /** Efeito sobre o esgoto quando a água é cortada ou suprimida. */
        public SewerCascadeDecision sewerCascadeOnWaterCutOrSuppression() {
            return switch (sewerOutOfUseOnWaterCut) {
                case AUTOMATIC -> SewerCascadeDecision.APPLY_IN_SAME_ORDER;
                case SERVICE_ORDER_ONLY -> SewerCascadeDecision.SEPARATE_ORDER_REQUIRED;
                case NONE -> SewerCascadeDecision.NO_EFFECT;
            };
        }

        /** O percentual de esgoto sobre fonte alternativa está ligado por valor aceito explícito? */
        public boolean alternativePctExplicitlyEnabled() {
            return Boolean.TRUE.equals(alternativePctEnabled);
        }
    }

    /** Interpreta os valores aceitos por chave (chave ausente = padrão do catálogo). */
    public static Values parse(Map<String, String> rawByKey) {
        Map<String, String> raw = rawByKey == null ? Map.of() : rawByKey;
        return new Values(
                parseEnum(raw.get(VOLUNTARY_SUPPRESSION_WITH_DEBT), VOLUNTARY_SUPPRESSION_WITH_DEBT,
                        VoluntarySuppressionWithDebt.class, SUPPRESSION_DEFAULT),
                parseEnum(raw.get(SEWER_OUT_OF_USE_ON_WATER_CUT), SEWER_OUT_OF_USE_ON_WATER_CUT,
                        SewerOutOfUseOnWaterCut.class, OUT_OF_USE_DEFAULT),
                parseBooleanOrNull(raw.get(SEWER_ALTERNATIVE_PCT_ENABLED),
                        SEWER_ALTERNATIVE_PCT_ENABLED));
    }

    /**
     * Portão da supressão voluntária, CONSUMIDO pelo handler de efeitos:
     * recusa explícita quando a bifurcação vigente não admite a supressão
     * diante do débito consultado. A taxa da supressão, quando houver, é
     * da ED-34 — aqui só a DECISÃO (ED-13).
     */
    public static void requireSuppressionAllowed(Values values, boolean hasOpenDebt,
                                                 boolean permissionGranted) {
        if (values == null) {
            throw new IllegalArgumentException("parametros de bifurcacao da ligacao sao obrigatorios");
        }
        if (values.suppressionAllowed(hasOpenDebt, permissionGranted)) {
            return;
        }
        if (hasOpenDebt && values.suppressionWithDebt() == VoluntarySuppressionWithDebt.BLOCK) {
            throw new ApiException.Conflict("supressao-bloqueada-por-debito",
                    "O tenant nao permite supressao voluntaria com debito em aberto ("
                            + VOLUNTARY_SUPPRESSION_WITH_DEBT + "=BLOCK).");
        }
        throw new ApiException.Conflict("supressao-exige-permissao",
                "O tenant exige permissao registrada na OS para supressao voluntaria "
                        + "com debito em aberto (" + VOLUNTARY_SUPPRESSION_WITH_DEBT
                        + "=ALLOW_WITH_PERMISSION).");
    }

    private static <E extends Enum<E>> E parseEnum(String json, String key, Class<E> type, E fallback) {
        if (json == null) {
            return fallback;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isTextual()) {
            throw new IllegalStateException("Valor de '" + key + "' nao e texto JSON: " + json);
        }
        try {
            return Enum.valueOf(type, node.asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Valor desconhecido de '" + key + "': " + json);
        }
    }

    private static Boolean parseBooleanOrNull(String json, String key) {
        if (json == null) {
            return null;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isBoolean()) {
            throw new IllegalStateException("Valor de '" + key + "' nao e booleano JSON: " + json);
        }
        return node.booleanValue();
    }
}
