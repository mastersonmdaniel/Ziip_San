package br.com.ziipsan.intelligence.gis.connection;

/**
 * Regras puras do ramal automático (GIS-05, P2.23): distância geodésica
 * entre o ponto do imóvel e o arco candidato e a decisão de gerar ou
 * pular, com o motivo. A geração em si (achar o arco mais próximo e
 * gravar o ramal em pacote) é da função
 * {@code gis_generate_auto_service_lines} da V13_10, em PostGIS; esta
 * classe é a mesma decisão em Java, testável sem banco e usada pelo
 * relatório de geração do serviço.
 */
public final class AutoServiceLinePlanner {

    /** Motivos de não geração, os mesmos códigos devolvidos pela função da V13_10. */
    public static final String PROPERTY_WITHOUT_POINT = "PROPERTY_WITHOUT_POINT";
    public static final String NO_COMPATIBLE_ARC = "NO_COMPATIBLE_ARC";
    public static final String BEYOND_MAX_DISTANCE = "BEYOND_MAX_DISTANCE";

    private AutoServiceLinePlanner() {
    }

    /** Ponto geográfico em SIRGAS 2000 (EPSG 4674): longitude/latitude em graus. */
    public record GeoPoint(double longitude, double latitude) {
    }

    /** Decisão para uma ligação: gerar (com a distância) ou pular (com o motivo). */
    public record Decision(boolean generate, Double distanceM, String skipReason) {
    }

    /**
     * Distância geodésica aproximada (haversine, raio médio da Terra)
     * entre dois pontos — suficiente para a decisão de elegibilidade e
     * para o relatório; a distância oficial da geração é a do PostGIS
     * ({@code geography}) na V13_10.
     */
    public static double distanceMeters(GeoPoint a, GeoPoint b) {
        double earthRadiusM = 6_371_008.8;
        double dLat = Math.toRadians(b.latitude() - a.latitude());
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusM * Math.asin(Math.sqrt(h));
    }

    /**
     * Decide por uma ligação: sem ponto do imóvel, não gera; sem arco
     * compatível, não gera; com arco além de
     * {@code gis.service_line_max_distance_m}, não gera; senão gera.
     * {@code nearestArcDistanceM} é {@code null} quando não há arco
     * compatível.
     */
    public static Decision decide(GeoPoint propertyPoint, Double nearestArcDistanceM,
                                  double maxDistanceM) {
        if (maxDistanceM <= 0) {
            throw new IllegalArgumentException("distância máxima deve ser positiva: " + maxDistanceM);
        }
        if (propertyPoint == null) {
            return new Decision(false, null, PROPERTY_WITHOUT_POINT);
        }
        if (nearestArcDistanceM == null) {
            return new Decision(false, null, NO_COMPATIBLE_ARC);
        }
        if (nearestArcDistanceM > maxDistanceM) {
            return new Decision(false, nearestArcDistanceM, BEYOND_MAX_DISTANCE);
        }
        return new Decision(true, nearestArcDistanceM, null);
    }

    /** Função de rede esperada por tipo de ligação (V13_10; lista do §4.6). */
    public static String networkFunctionFor(String kind) {
        return switch (kind == null ? "" : kind) {
            case "WATER" -> "WATER_DISTRIBUTION";
            case "SEWER" -> "SEWER_NETWORK";
            default -> throw new IllegalArgumentException("tipo de ligação desconhecido: " + kind);
        };
    }

    /** Tipo de feição do ramal por tipo de ligação (catálogo de fábrica). */
    public static String featureTypeFor(String kind) {
        return switch (kind == null ? "" : kind) {
            case "WATER" -> "WATER_SERVICE_LINE";
            case "SEWER" -> "SEWER_SERVICE_LINE";
            default -> throw new IllegalArgumentException("tipo de ligação desconhecido: " + kind);
        };
    }
}
