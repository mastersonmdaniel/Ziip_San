package br.com.ziipsan.intelligence.gis.field;

import java.util.Objects;

/**
 * Conversao de altitude do GNSS (ED-66 / GIS-15, GIS-CADASTRO-TECNICO
 * §3.9): a altitude ELIPSOIDAL (h) do receptor so existe EM TRANSITO —
 * a altitude gravada na feicao e a altitude NORMAL (H) no datum do
 * tenant (Imbituba; Santana no Amapa), convertida NO SERVIDOR pelo
 * modelo geoidal hgeoHNOR2020 do IBGE: H = h - N, com N = ondulacao
 * geoidal do modelo e a altura da antena descontada de h antes.
 *
 * <p>Este nucleo NAO embute grade nem valor de ondulacao: a ondulacao
 * vem da {@link GeoidPort} (a grade oficial do IBGE carregada na
 * integracao — pendencia P-ED66-04). Sem a porta, a conversao RECUSA:
 * gravar a elipsoidal como se fosse normal e o erro que esta regra
 * existe para impedir.
 */
public final class AltitudeConversion {

    /** Datum vertical do tenant (lista da V13_2 para a feicao convertida). */
    public enum VerticalDatum { IMBITUBA, SANTANA }

    /** Porta da grade hgeoHNOR2020: devolve a ondulacao geoidal N (m) no ponto. */
    public interface GeoidPort {
        double undulationMeters(double latitude, double longitude);
    }

    /** Resultado da conversao: altitude normal e o datum em que ela esta. */
    public record NormalHeight(double heightM, VerticalDatum datum, double undulationUsedM) {}

    private AltitudeConversion() {}

    /**
     * Converte a leitura do receptor em altitude normal.
     *
     * @param ellipsoidalHeightM altitude elipsoidal bruta do fix (h)
     * @param antennaHeightM     altura da antena sobre o ponto (descontada de h)
     * @param geoid              porta da grade oficial; nula = recusa nomeada
     */
    public static NormalHeight toNormalHeight(double latitude, double longitude,
                                              double ellipsoidalHeightM, double antennaHeightM,
                                              VerticalDatum tenantDatum, GeoidPort geoid) {
        Objects.requireNonNull(tenantDatum, "datum vertical do tenant e obrigatorio");
        if (geoid == null) throw new IllegalStateException(
                "conversao de altitude exige a grade hgeoHNOR2020 (IBGE) no servidor: "
                        + "altitude elipsoidal nunca e gravada como altitude normal");
        if (latitude < -90 || latitude > 90) throw new IllegalArgumentException("latitude fora da faixa");
        if (longitude < -180 || longitude > 180) throw new IllegalArgumentException("longitude fora da faixa");
        if (antennaHeightM < 0) throw new IllegalArgumentException("altura da antena nao pode ser negativa");
        double n = geoid.undulationMeters(latitude, longitude);
        double pointEllipsoidal = ellipsoidalHeightM - antennaHeightM;
        return new NormalHeight(pointEllipsoidal - n, tenantDatum, n);
    }
}
