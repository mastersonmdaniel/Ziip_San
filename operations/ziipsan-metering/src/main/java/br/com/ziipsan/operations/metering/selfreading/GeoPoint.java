package br.com.ziipsan.operations.metering.selfreading;

/**
 * Uma posicao geografica em graus decimais (II-7). Tipo proprio do pacote
 * de autoleitura: a validacao espelha a da ED-15 ({@code GeoPoint} da
 * {@code PositionDeviationRule}) -- coordenada nao finita, latitude fora
 * de -90..90 ou longitude fora de -180..180 sao recusadas -- mas o tipo e
 * independente, para o nucleo da II-7 compilar e provar-se sozinho; a
 * fiacao aos tipos da ED-15/ED-18 faz-se na integracao (P-II7-FIACAO).
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException(
                    "coordenada nao finita (NaN ou infinita) nao e posicao");
        }
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude fora de -90..90: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude fora de -180..180: " + longitude);
        }
    }
}
