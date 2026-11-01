package br.com.ziipsan.intelligence.gis.epanet;

import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Elemento da rede, já reduzido ao que o EPANET entende e desligado do JPA.
 *
 * <p>Existe para que a construção da topologia seja testável sem banco. O serviço converte
 * {@code GisNetworkElement} em {@code ElementSpec}.
 *
 * @param code       código do elemento; vira o ID no .inp (sem espaço nem ';', até 31 caracteres)
 * @param kind       papel hidráulico
 * @param geometry   ponto para nós e elementos pontuais; linha para tubulações
 * @param diameterMm diâmetro em milímetros (tubulação, válvula)
 * @param lengthM    comprimento informado em metros; se nulo, é calculado da geometria
 * @param numbers    atributos numéricos opcionais: {@code elevation_m}, {@code head_m},
 *                   {@code demand_lps}, {@code power_kw}, {@code setting}, {@code diameter_m},
 *                   {@code min_level_m}, {@code max_level_m}, {@code init_level_m},
 *                   {@code roughness}
 * @param material   material da tubulação, para a rugosidade
 * @param valveType  tipo da válvula (PRV, PSV, PBV, FCV, TCV); nulo para as demais
 */
public record ElementSpec(
        String code,
        String name,
        Kind kind,
        Geometry geometry,
        Integer diameterMm,
        BigDecimal lengthM,
        String material,
        EpanetValve.ValveType valveType,
        Map<String, BigDecimal> numbers) {

    public enum Kind {
        JUNCTION, TANK, RESERVOIR, PIPE, VALVE, PUMP
    }

    public BigDecimal number(String key) {
        return numbers == null ? null : numbers.get(key);
    }
}
