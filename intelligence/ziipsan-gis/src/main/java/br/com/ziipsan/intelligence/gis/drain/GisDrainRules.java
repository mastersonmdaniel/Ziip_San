package br.com.ziipsan.intelligence.gis.drain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static br.com.ziipsan.intelligence.gis.drain.GisDrainCatalog.DrainType;

/**
 * Regras puras da GIS-13 (§4.4, §5.3 e §5.4), espelho legível das
 * guardas do banco no SQL pendente da ED-64 (attributes_schema por
 * tipo + CHECKs das tabelas operacionais) — o banco é a autoridade;
 * esta classe existe para a api falhar cedo, com o erro certo, e para
 * as regras serem testáveis sem banco.
 *
 * <p>Declividade e sentido (§5.4, domínio drenagem por gravidade):
 * a coluna gerada {@code slope} da V13_2 é
 * {@code (jusante − montante) / comprimento} — portanto NEGATIVA no
 * escoamento correto. Esta classe trabalha com a DECLIVIDADE do
 * escoamento, {@code (montante − jusante) / comprimento} (positiva no
 * sentido certo), e classifica:
 * <ul>
 *   <li>declividade &lt; 0 (jusante acima de montante): erro
 *       {@code STORM_SLOPE_ADVERSE} — "declividade negativa / sentido
 *       contrário às cotas" do §5.4;</li>
 *   <li>declividade = 0: erro {@code STORM_SLOPE_FLAT} — sem queda não
 *       há escoamento por gravidade;</li>
 *   <li>0 &lt; declividade &lt; mínima do tenant: aviso
 *       {@code STORM_SLOPE_BELOW_MIN} — a mínima NÃO tem valor de
 *       norma no §4.4/§5.4 ("não há NBR de microdrenagem"), logo é
 *       parâmetro do tenant ({@code drain.gravity_min_slope}); sem
 *       parâmetro, nenhum aviso é emitido (nunca um piso inventado);</li>
 *   <li>sentido ({@code flow_direction}) contrário às cotas: erro
 *       {@code STORM_FLOW_AGAINST_INVERTS}.</li>
 * </ul>
 *
 * <p>Nenhum outro valor de negócio é presumido: DN e seções do §4.4
 * são referência a validar (fora da série = AVISO, nunca recusa, como
 * os volumes da ED-65).
 */
public final class GisDrainRules {

    /** Severidade do problema (§5.4/§5.5: ERROR bloqueia, WARNING aceitável). */
    public enum Severity { ERROR, WARNING }

    /** Um problema de validação da drenagem (espelho de gis_validation_issue). */
    public record DrainIssue(String ruleCode, Severity severity, String message) {
    }

    private GisDrainRules() {
    }

    // ---------------------------------------------------------- atributos

    /**
     * Valida os atributos de uma feição de drenagem contra o tipo do
     * §4.4. A validação de formato fino (tipos JSON) é do
     * attributes_schema no banco; aqui ficam obrigatórios, listas e
     * faixas. Devolve os AVISOS de referência (DN fora da série do
     * §4.4); os erros lançam IllegalArgumentException.
     */
    public static List<DrainIssue> ensureFeatureAttributes(
            DrainType type, Map<String, Object> attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("atributos da feicao de drenagem ausentes");
        }
        for (String required : type.requiredAttributes()) {
            Object value = attributes.get(required);
            if (value == null || (value instanceof String s && s.isBlank())
                    || (value instanceof List<?> l && l.isEmpty())) {
                throw new IllegalArgumentException(
                        "atributo obrigatorio do tipo " + type.code() + " ausente: " + required);
            }
        }
        List<DrainIssue> warnings = new ArrayList<>();
        switch (type.code()) {
            case "BOX_CULVERT" -> {
                int cells = asInt(attributes.get("cell_count"), "cell_count");
                if (cells < 1) {
                    throw new IllegalArgumentException("galeria celular exige cell_count >= 1");
                }
            }
            case "OPEN_CHANNEL" -> {
                requireIn(attributes.get("channel_section"),
                        GisDrainCatalog.CHANNEL_SECTIONS, "secao do canal");
                requireIn(attributes.get("channel_lining"),
                        GisDrainCatalog.CHANNEL_LININGS, "revestimento do canal");
            }
            case "CULVERT" -> requireIn(attributes.get("culvert_shape"),
                    GisDrainCatalog.CULVERT_SHAPES, "forma do bueiro");
            case "INLET" -> requireIn(attributes.get("inlet_kind"),
                    GisDrainCatalog.INLET_KINDS, "tipo da boca de lobo");
            case "GRATE_INLET" -> requireIn(attributes.get("load_class"),
                    GisDrainCatalog.LOAD_CLASSES, "classe de carga da grelha");
            case "DROP_SHAFT" -> {
                double h = asDouble(attributes.get("drop_height_m"), "drop_height_m");
                if (h <= 0) {
                    throw new IllegalArgumentException("altura de queda deve ser positiva");
                }
            }
            case "ENERGY_DISSIPATOR" -> requireIn(attributes.get("dissipator_kind"),
                    GisDrainCatalog.DISSIPATOR_KINDS, "tipo do dissipador");
            case "DETENTION_BASIN", "RETENTION_BASIN" -> {
                double v = asDouble(attributes.get("volume_m3"), "volume_m3");
                if (v <= 0) {
                    throw new IllegalArgumentException("volume da bacia deve ser positivo");
                }
            }
            case "STORM_SUBCATCHMENT" -> ensureSubcatchmentAttributes(attributes);
            default -> {
                // Tipos sem regra própria além dos obrigatórios.
            }
        }
        // DN fora da série de referência do §4.4: aviso, nunca recusa.
        Object dn = attributes.get("dn_mm");
        if (dn != null && (type.code().equals("STORM_PIPE") || type.code().equals("INLET_LEAD"))) {
            int dnMm = asInt(dn, "dn_mm");
            if (!GisDrainCatalog.isReferenceDn(type.code(), dnMm)) {
                warnings.add(new DrainIssue("STORM_DN_OUTSIDE_REFERENCE", Severity.WARNING,
                        "DN " + dnMm + " fora da serie de referencia do §4.4 para "
                                + type.code() + " (valor de referencia a validar)"));
            }
        }
        Object condition = attributes.get("condition_rating");
        if (condition != null) {
            requireIn(condition, GisDrainCatalog.CONDITION_RATINGS, "estado de conservacao");
        }
        Object obstruction = attributes.get("obstruction_pct");
        if (obstruction != null) {
            ensureObstructionPct(asDouble(obstruction, "obstruction_pct"));
        }
        return List.copyOf(warnings);
    }

    /** Obstrução/assoreamento (%): faixa fechada 0..100 (§4.4). */
    public static void ensureObstructionPct(double pct) {
        if (pct < 0 || pct > 100) {
            throw new IllegalArgumentException("obstrucao fora da faixa 0..100 (%): " + pct);
        }
    }

    /**
     * Atributos da sub-bacia com os atributos do SWMM (§4.4: área,
     * impermeabilidade %, declividade, largura, parâmetros de
     * infiltração e exutório). Os parâmetros exigidos por modelo são
     * os do SWMM 5.2: HORTON (taxas máxima/mínima e decaimento),
     * GREEN_AMPT (sucção, condutividade, déficit inicial) e
     * CURVE_NUMBER (número de curva). Nenhuma faixa de CN é imposta
     * além de 0..100 (a tabela de CN por uso do solo é do projeto do
     * tenant, não desta entrega).
     */
    public static void ensureSubcatchmentAttributes(Map<String, Object> attributes) {
        double area = asDouble(attributes.get("area_ha"), "area_ha");
        if (area <= 0) {
            throw new IllegalArgumentException("area da sub-bacia deve ser positiva (ha)");
        }
        double impervious = asDouble(attributes.get("impervious_pct"), "impervious_pct");
        if (impervious < 0 || impervious > 100) {
            throw new IllegalArgumentException("impermeabilidade fora da faixa 0..100 (%)");
        }
        double slope = asDouble(attributes.get("slope_pct"), "slope_pct");
        if (slope < 0) {
            throw new IllegalArgumentException("declividade da sub-bacia nao pode ser negativa (%)");
        }
        double width = asDouble(attributes.get("width_m"), "width_m");
        if (width <= 0) {
            throw new IllegalArgumentException("largura da sub-bacia deve ser positiva (m)");
        }
        String model = asString(attributes.get("infiltration_model"));
        requireIn(model, GisDrainCatalog.INFILTRATION_MODELS, "modelo de infiltracao (SWMM)");
        switch (model) {
            case "HORTON" -> {
                requirePositive(attributes, "infiltration_max_rate_mm_h");
                requirePositive(attributes, "infiltration_min_rate_mm_h");
                requirePositive(attributes, "infiltration_decay_per_h");
            }
            case "GREEN_AMPT" -> {
                requirePositive(attributes, "suction_head_mm");
                requirePositive(attributes, "hydraulic_conductivity_mm_h");
                Object deficit = attributes.get("initial_deficit");
                if (deficit == null) {
                    throw new IllegalArgumentException(
                            "atributo obrigatorio do modelo GREEN_AMPT: initial_deficit");
                }
                double d = asDouble(deficit, "initial_deficit");
                if (d < 0 || d > 1) {
                    throw new IllegalArgumentException("initial_deficit fora da faixa 0..1");
                }
            }
            case "CURVE_NUMBER" -> {
                double cn = asDouble(attributes.get("curve_number"), "curve_number");
                if (cn <= 0 || cn > 100) {
                    throw new IllegalArgumentException("curve_number fora da faixa (0..100]");
                }
            }
            default -> throw new IllegalArgumentException(
                    "modelo de infiltracao fora da lista (SWMM): " + model);
        }
    }

    // ------------------------------------------------- declividade/sentido

    /** Declividade do escoamento: (montante − jusante) / comprimento. */
    public static double declivity(double upstreamInvertM, double downstreamInvertM,
                                   double lengthM) {
        if (lengthM <= 0) {
            throw new IllegalArgumentException("comprimento do arco deve ser positivo");
        }
        return (upstreamInvertM - downstreamInvertM) / lengthM;
    }

    /**
     * Classifica a declividade de um arco de gravidade (§5.4).
     * {@code tenantMinSlope} é o parâmetro drain.gravity_min_slope e
     * pode ser null (sem parâmetro, sem aviso de mínima).
     */
    public static List<DrainIssue> checkSlope(double upstreamInvertM, double downstreamInvertM,
                                              double lengthM, Double tenantMinSlope) {
        double d = declivity(upstreamInvertM, downstreamInvertM, lengthM);
        if (d < 0) {
            return List.of(new DrainIssue("STORM_SLOPE_ADVERSE", Severity.ERROR,
                    "declividade negativa: cota de jusante acima da de montante (§5.4)"));
        }
        if (d == 0) {
            return List.of(new DrainIssue("STORM_SLOPE_FLAT", Severity.ERROR,
                    "declividade nula: sem queda nao ha escoamento por gravidade (§5.4)"));
        }
        if (tenantMinSlope != null && d < tenantMinSlope) {
            return List.of(new DrainIssue("STORM_SLOPE_BELOW_MIN", Severity.WARNING,
                    "declividade " + d + " abaixo da minima do tenant " + tenantMinSlope
                            + " (parametro drain.gravity_min_slope)"));
        }
        return List.of();
    }

    /**
     * Sentido do arco contra as cotas (§5.4): WITH_GEOMETRY exige a
     * geometria desenhada de montante (nó 1) para jusante (nó 2);
     * AGAINST_GEOMETRY, o inverso; BOTH/UNKNOWN não geram problema
     * de sentido (o traçado da GIS-11/ED-54 decide pelo contexto).
     */
    public static List<DrainIssue> checkFlowDirection(String flowDirection,
                                                      double invertAtNode1M,
                                                      double invertAtNode2M) {
        if (flowDirection == null || flowDirection.equals("BOTH")
                || flowDirection.equals("UNKNOWN")) {
            return List.of();
        }
        if (!flowDirection.equals("WITH_GEOMETRY") && !flowDirection.equals("AGAINST_GEOMETRY")) {
            throw new IllegalArgumentException("flow_direction fora da lista da V13_2: "
                    + flowDirection);
        }
        boolean dropsAlongGeometry = invertAtNode1M > invertAtNode2M;
        boolean dropsAgainstGeometry = invertAtNode2M > invertAtNode1M;
        if (flowDirection.equals("WITH_GEOMETRY") && dropsAgainstGeometry) {
            return List.of(new DrainIssue("STORM_FLOW_AGAINST_INVERTS", Severity.ERROR,
                    "sentido WITH_GEOMETRY contrario as cotas: o fundo sobe de no 1 para no 2"));
        }
        if (flowDirection.equals("AGAINST_GEOMETRY") && dropsAlongGeometry) {
            return List.of(new DrainIssue("STORM_FLOW_AGAINST_INVERTS", Severity.ERROR,
                    "sentido AGAINST_GEOMETRY contrario as cotas: o fundo sobe de no 2 para no 1"));
        }
        return List.of();
    }

    // ------------------------------------------------------------ inspeção

    /**
     * Uma inspeção de drenagem (NR 12/2025 via §4.4): data obrigatória,
     * método da lista, conservação e obstrução válidas quando
     * informados. {@code videoRef} é o marcador de reaproveitamento
     * da inspeção por vídeo da ED-56 (OPE-12): quando presente, o
     * método é CCTV_EXTERNAL_REF e a referência é o arquivo/ensaio da
     * ED-56 — esta entrega não executa nem importa vídeo.
     */
    public static void ensureInspection(LocalDate inspectedOn, String method,
                                        String conditionRating, Double obstructionPct,
                                        String videoRef) {
        if (inspectedOn == null) {
            throw new IllegalArgumentException("data da inspecao e obrigatoria (§4.4)");
        }
        requireIn(method, GisDrainCatalog.INSPECTION_METHODS, "metodo de inspecao");
        if (conditionRating != null) {
            requireIn(conditionRating, GisDrainCatalog.CONDITION_RATINGS,
                    "estado de conservacao");
        }
        if (obstructionPct != null) {
            ensureObstructionPct(obstructionPct);
        }
        if (videoRef != null && !method.equals("CCTV_EXTERNAL_REF")) {
            throw new IllegalArgumentException(
                    "referencia de video exige metodo CCTV_EXTERNAL_REF (ED-56)");
        }
        if (method.equals("CCTV_EXTERNAL_REF") && (videoRef == null || videoRef.isBlank())) {
            throw new IllegalArgumentException(
                    "metodo CCTV_EXTERNAL_REF exige a referencia do arquivo da ED-56");
        }
    }

    // ------------------------------------------------------------- helpers

    private static void requirePositive(Map<String, Object> attributes, String key) {
        Object v = attributes.get(key);
        if (v == null) {
            throw new IllegalArgumentException("atributo obrigatorio do modelo de infiltracao "
                    + "ausente: " + key);
        }
        if (asDouble(v, key) <= 0) {
            throw new IllegalArgumentException("atributo deve ser positivo: " + key);
        }
    }

    private static void requireIn(Object value, java.util.Set<String> allowed, String what) {
        String s = asString(value);
        if (s == null || !allowed.contains(s)) {
            throw new IllegalArgumentException(what + " fora da lista (§4.4): " + value);
        }
    }

    static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    static double asDouble(Object value, String key) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("atributo numerico esperado: " + key);
    }

    static int asInt(Object value, String key) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException("atributo inteiro esperado: " + key);
    }
}
