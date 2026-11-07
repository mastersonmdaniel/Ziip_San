package br.com.ziipsan.intelligence.gis.publication;

/**
 * Conversão UTM ↔ geográfica no elipsoide GRS80 (SIRGAS 2000, §3.9)
 * para a pesquisa por coordenada do mapa web (§10.3). Fórmulas da
 * projeção Transversa de Mercator (séries de Krüger/NGA), sem
 * dependência nova: o frontend tem o espelho em
 * {@code frontend/src/utils/utm.ts} e os dois lados são testados com
 * os mesmos pontos de conferência.
 *
 * <p>Precisão esperada dentro do fuso: milimétrica para o uso da
 * pesquisa (centrar o mapa e buscar num raio) — a busca em si roda
 * no PostGIS sobre a geometria oficial em 4674; esta conversão só
 * transforma a coordenada DIGITADA pelo usuário em ponto de busca.
 */
public final class Utm {

    /** GRS80: semi-eixo maior e achatamento (SIRGAS 2000). */
    private static final double A = 6_378_137.0;
    private static final double F = 1.0 / 298.257222101;
    private static final double K0 = 0.9996;
    private static final double FALSE_EASTING = 500_000.0;
    private static final double FALSE_NORTHING_SOUTH = 10_000_000.0;

    private Utm() {
    }

    public record LonLat(double lon, double lat) {
    }

    /** Meridiano central do fuso (1..60). */
    public static double centralMeridianDeg(int zone) {
        if (zone < 1 || zone > 60) {
            throw new IllegalArgumentException("fuso UTM fora de 1..60: " + zone);
        }
        return (zone - 1) * 6 - 180 + 3;
    }

    /** UTM (metros) → lon/lat (graus). */
    public static LonLat toLonLat(double easting, double northing, int zone,
                                  boolean southernHemisphere) {
        double lon0 = Math.toRadians(centralMeridianDeg(zone));
        double e2 = F * (2 - F);
        double e = Math.sqrt(e2);
        double x = easting - FALSE_EASTING;
        double y = northing - (southernHemisphere ? FALSE_NORTHING_SOUTH : 0.0);
        double m = y / K0;
        double mu = m / (A * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256));
        double e1 = (1 - Math.sqrt(1 - e2)) / (1 + Math.sqrt(1 - e2));
        double j1 = 3 * e1 / 2 - 27 * e1 * e1 * e1 / 32;
        double j2 = 21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32;
        double j3 = 151 * e1 * e1 * e1 / 96;
        double j4 = 1097 * e1 * e1 * e1 * e1 / 512;
        double fp = mu + j1 * Math.sin(2 * mu) + j2 * Math.sin(4 * mu)
                + j3 * Math.sin(6 * mu) + j4 * Math.sin(8 * mu);
        double sinFp = Math.sin(fp);
        double cosFp = Math.cos(fp);
        double tanFp = Math.tan(fp);
        double n = A / Math.sqrt(1 - e2 * sinFp * sinFp);
        double r = A * (1 - e2) / Math.pow(1 - e2 * sinFp * sinFp, 1.5);
        double t = tanFp * tanFp;
        double eta2 = e2 * cosFp * cosFp / (1 - e2);
        double d = x / (n * K0);
        double lat = fp - (n * tanFp / r) * (d * d / 2
                - (5 + 3 * t + 10 * eta2 - 4 * eta2 * eta2 - 9 * e2) * d * d * d * d / 24
                + (61 + 90 * t + 298 * eta2 + 45 * t * t - 252 * e2 - 3 * eta2 * eta2)
                * Math.pow(d, 6) / 720);
        double lon = lon0 + (d - (1 + 2 * t + eta2) * d * d * d / 6
                + (5 - 2 * eta2 + 28 * t - 8 * eta2 * eta2 + 8 * e2 + 24 * t * t)
                * Math.pow(d, 5) / 120) / cosFp;
        return new LonLat(Math.toDegrees(lon), Math.toDegrees(lat));
    }

    public record UtmPoint(double easting, double northing, int zone,
                           boolean southernHemisphere) {
    }

    /** lon/lat (graus) → UTM (metros); fuso derivado da longitude. */
    public static UtmPoint fromLonLat(double lon, double lat) {
        int zone = (int) Math.floor((lon + 180) / 6) + 1;
        boolean south = lat < 0;
        double lon0 = Math.toRadians(centralMeridianDeg(zone));
        double e2 = F * (2 - F);
        double ep2 = e2 / (1 - e2);
        double latR = Math.toRadians(lat);
        double lonR = Math.toRadians(lon);
        double sinLat = Math.sin(latR);
        double cosLat = Math.cos(latR);
        double tanLat = Math.tan(latR);
        double n = A / Math.sqrt(1 - e2 * sinLat * sinLat);
        double t = tanLat * tanLat;
        double c = ep2 * cosLat * cosLat;
        double a = cosLat * (lonR - lon0);
        double m = A * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * latR
                - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * Math.sin(2 * latR)
                + (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * Math.sin(4 * latR)
                - (35 * e2 * e2 * e2 / 3072) * Math.sin(6 * latR));
        double easting = K0 * n * (a + (1 - t + c) * a * a * a / 6
                + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * Math.pow(a, 5) / 120)
                + FALSE_EASTING;
        double northing = K0 * (m + n * tanLat * (a * a / 2
                + (5 - t + 9 * c + 4 * c * c) * Math.pow(a, 4) / 24
                + (61 - 58 * t + t * t + 600 * c - 330 * ep2) * Math.pow(a, 6) / 720))
                + (south ? FALSE_NORTHING_SOUTH : 0.0);
        return new UtmPoint(easting, northing, zone, south);
    }
}
