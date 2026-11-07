package br.com.ziipsan.intelligence.gis.publication;

import br.com.ziipsan.core.json.Json;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Regras puras da simbologia da GIS-06 (§10.2): o padrão Ziip por
 * estado e a resolução do estilo efetivo a partir das linhas de
 * {@code gis_style} (fábrica + override do tenant), CAMPO A CAMPO —
 * uma linha só sobrescreve os campos que declara (não nulos), então
 * o tenant pode trocar só a cor de um tipo sem redefinir traço,
 * ícone e rótulo, e "restaurar o padrão" é apagar as linhas TENANT.
 *
 * <p>Ordem de especificidade (da maior para a menor), por campo:
 * TENANT tipo+accuracy, TENANT tipo, FACTORY tipo+accuracy, FACTORY
 * tipo, TENANT domínio+accuracy, TENANT domínio, FACTORY
 * domínio+accuracy, FACTORY domínio, TENANT genérica, FACTORY
 * genérica — e, se nem a genérica declarar o campo, o padrão Ziip
 * embutido desta classe (que espelha as sementes da V13_12).
 *
 * <p>Padrão Ziip por estado (§10.2, decisão 6): em operação, traço
 * contínuo na cor do domínio; projetado (PLANNED), tracejado; em
 * obra (UNDER_CONSTRUCTION), traço e ponto; abandonado, cinza
 * tracejado fino. Estado NUNCA é comunicado só por cor: o padrão de
 * traço muda junto (acessibilidade). Fora da meta de precisão
 * (OUT_OF_TARGET): halo. As cores são TOKENS {@code map-*} do
 * ADR-0017 — os hex por tema (claro/escuro) vivem na paleta do
 * frontend e são resolvidos no navegador; aqui trafega o nome.
 */
public final class GisStyleRules {

    public static final List<String> LIFECYCLE_STATUSES =
            List.of("PLANNED", "UNDER_CONSTRUCTION", "IN_SERVICE", "ABANDONED");
    public static final List<String> ACCURACY_STATUSES =
            List.of("WITHIN_TARGET", "OUT_OF_TARGET", "APPROXIMATE", "UNKNOWN");
    /** Token do abandonado (§10.2: cinza) — a registrar na paleta. */
    public static final String ABANDONED_TOKEN = "map-abandoned";
    public static final String FALLBACK_TOKEN = "map-context";

    private GisStyleRules() {
    }

    /** Uma linha de gis_style (campos anuláveis = "não declara"). */
    public record StyleRow(String origin, String domainKey, UUID featureTypeId,
                           String lifecycleStatus, String accuracyStatus,
                           String colorToken, Double lineWidth, String lineDashJson,
                           Boolean halo, String iconKey, String labelField,
                           Integer labelMinZoom) {
    }

    /** Estilo resolvido para um (tipo, estado) — sem nenhum nulo. */
    public record ResolvedStyle(String colorToken, double lineWidth,
                                List<Double> lineDash, boolean halo,
                                String iconKey, String labelField, int labelMinZoom) {
    }

    /** Padrão de traço por estado (§10.2). Lista vazia = contínuo. */
    public static List<Double> dashFor(String lifecycleStatus) {
        return switch (lifecycleStatus) {
            case "IN_SERVICE" -> List.of();
            case "PLANNED" -> List.of(3.0, 2.0);
            case "UNDER_CONSTRUCTION" -> List.of(4.0, 2.0, 1.0, 2.0);
            case "ABANDONED" -> List.of(2.0, 2.0);
            default -> throw new IllegalArgumentException(
                    "lifecycle_status desconhecido: " + lifecycleStatus);
        };
    }

    public static double widthFor(String lifecycleStatus) {
        return "ABANDONED".equals(lifecycleStatus) ? 1.0 : 2.0;
    }

    static int specificity(StyleRow row, String domainKey, UUID featureTypeId,
                           String accuracyStatus) {
        boolean typeMatch = row.featureTypeId() != null
                && row.featureTypeId().equals(featureTypeId);
        boolean domainMatch = row.domainKey() != null
                && row.domainKey().equals(domainKey);
        boolean generic = row.featureTypeId() == null && row.domainKey() == null;
        if (!typeMatch && !domainMatch && !generic) {
            return -1;
        }
        boolean accuracyMatch = row.accuracyStatus() != null
                && row.accuracyStatus().equals(accuracyStatus);
        if (row.accuracyStatus() != null && !accuracyMatch) {
            return -1;
        }
        // Faixas: tipo (200) > domínio (100) > genérica (0); dentro
        // da faixa, TENANT (+50) vence FACTORY; accuracy exata (+5)
        // vence a linha sem accuracy da mesma faixa/origem.
        int band = typeMatch ? 200 : domainMatch ? 100 : 0;
        int originBonus = "TENANT".equals(row.origin()) ? 50 : 0;
        return band + originBonus + (accuracyMatch ? 5 : 0);
    }

    /**
     * Resolve o estilo efetivo de um (domínio, tipo, ciclo de vida,
     * accuracy). {@code domainColorToken} é o token do domínio
     * (gis_domain.map_color_token), usado no padrão embutido.
     */
    public static ResolvedStyle resolve(List<StyleRow> rows, String domainKey,
                                        UUID featureTypeId, String lifecycleStatus,
                                        String accuracyStatus, String domainColorToken) {
        List<StyleRow> applicable = rows.stream()
                .filter(r -> lifecycleStatus.equals(r.lifecycleStatus()))
                .filter(r -> specificity(r, domainKey, featureTypeId, accuracyStatus) >= 0)
                .sorted(Comparator.comparingInt(
                        (StyleRow r) -> specificity(r, domainKey, featureTypeId, accuracyStatus))
                        .reversed())
                .toList();

        String colorToken = null;
        Double lineWidth = null;
        List<Double> lineDash = null;
        Boolean halo = null;
        String iconKey = null;
        String labelField = null;
        Integer labelMinZoom = null;
        for (StyleRow row : applicable) {
            if (colorToken == null) {
                colorToken = row.colorToken();
            }
            if (lineWidth == null) {
                lineWidth = row.lineWidth();
            }
            if (lineDash == null && row.lineDashJson() != null) {
                lineDash = parseDash(row.lineDashJson());
            }
            if (halo == null) {
                halo = row.halo();
            }
            if (iconKey == null) {
                iconKey = row.iconKey();
            }
            if (labelField == null) {
                labelField = row.labelField();
            }
            if (labelMinZoom == null) {
                labelMinZoom = row.labelMinZoom();
            }
        }
        // Padrão Ziip embutido (espelho das sementes da V13_12).
        if (colorToken == null) {
            colorToken = "ABANDONED".equals(lifecycleStatus) ? ABANDONED_TOKEN
                    : domainColorToken != null ? domainColorToken : FALLBACK_TOKEN;
        }
        if (lineWidth == null) {
            lineWidth = widthFor(lifecycleStatus);
        }
        if (lineDash == null) {
            lineDash = dashFor(lifecycleStatus);
        }
        if (halo == null) {
            halo = "OUT_OF_TARGET".equals(accuracyStatus);
        }
        if (labelField == null) {
            labelField = "code";
        }
        if (labelMinZoom == null) {
            labelMinZoom = 16;
        }
        return new ResolvedStyle(colorToken, lineWidth, lineDash, halo,
                iconKey, labelField, labelMinZoom);
    }

    static List<Double> parseDash(String lineDashJson) {
        List<Double> out = new ArrayList<>();
        var node = Json.readTree(lineDashJson);
        if (node.isArray()) {
            node.forEach(n -> out.add(n.asDouble()));
        }
        return List.copyOf(out);
    }

    /**
     * Valida um override do tenant antes de gravar (sem código,
     * §10.2): estado conhecido, cor como token {@code map-*} e ícone
     * SOMENTE da biblioteca de símbolos (o tenant escolhe o símbolo,
     * nunca envia SVG — ADR-0036).
     */
    public static void validateOverride(String lifecycleStatus, String accuracyStatus,
                                        String colorToken, String iconKey,
                                        java.util.Set<String> librarySymbolKeys) {
        if (!LIFECYCLE_STATUSES.contains(lifecycleStatus)) {
            throw new IllegalArgumentException(
                    "lifecycle_status desconhecido: " + lifecycleStatus);
        }
        if (accuracyStatus != null && !ACCURACY_STATUSES.contains(accuracyStatus)) {
            throw new IllegalArgumentException(
                    "accuracy_status desconhecido: " + accuracyStatus);
        }
        if (colorToken != null && !colorToken.startsWith("map-")) {
            throw new IllegalArgumentException(
                    "cor deve ser um token map-* (ADR-0017): " + colorToken);
        }
        if (iconKey != null && !librarySymbolKeys.contains(iconKey)) {
            throw new IllegalArgumentException(
                    "símbolo fora da biblioteca da P1.30: " + iconKey);
        }
    }
}
