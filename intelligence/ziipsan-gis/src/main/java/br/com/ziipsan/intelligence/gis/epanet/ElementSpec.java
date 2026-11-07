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
 * @param node1Code  código do nó da ponta inicial, da topologia persistida ({@code node_1_code}
 *                   da view de compatibilidade); só tem sentido para {@code PIPE} e é nulo nos
 *                   demais papéis. Sem ele a tubulação não entra na rede (GIS-03, passo M4):
 *                   não há mais resolução por proximidade
 * @param node2Code  código do nó da ponta final, idem
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
        Map<String, BigDecimal> numbers,
        String node1Code,
        String node2Code) {

    /**
     * Construtor sem topologia, mantido para os chamadores anteriores ao M4 (testes e código
     * que monta nós e peças, que não têm pontas). Tubulação montada por aqui nasce sem
     * {@code node1Code}/{@code node2Code} e o construtor de topologia a recusa com problema
     * registrado — nunca a liga por proximidade.
     */
    public ElementSpec(String code, String name, Kind kind, Geometry geometry, Integer diameterMm,
                       BigDecimal lengthM, String material, EpanetValve.ValveType valveType,
                       Map<String, BigDecimal> numbers) {
        this(code, name, kind, geometry, diameterMm, lengthM, material, valveType, numbers, null, null);
    }

    public enum Kind {
        JUNCTION, TANK, RESERVOIR, PIPE, VALVE, PUMP
    }

    public BigDecimal number(String key) {
        return numbers == null ? null : numbers.get(key);
    }
}
