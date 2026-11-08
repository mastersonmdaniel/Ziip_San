package br.com.ziipsan.intelligence.gis.maneuver;

import java.util.List;

/**
 * Cobertura nomeada da GIS-11 (ED-54) — registro, não ensaio: os
 * códigos abaixo são os citados no aceite. O ENSAIO (SAMAE 2.27 com
 * roteiro real, TR com massa do órgão) é externo e fica como
 * pendência nomeada no relatório — nenhum código foi inventado
 * além dos citados no aceite da tarefa.
 *
 * <p>Fora desta cobertura, por remessa expressa do aceite:
 * programa de exercício de válvulas = ED-56 (OPE-09); rodízio e
 * crise hídrica = ED-91 (OPE-10); ocorrência de rede = ED-92
 * (OPE-16).
 */
public final class ManeuverCoverage {

    public record CoverageItem(String code, String requirement, String component) {
    }

    public static final List<CoverageItem> ITEMS = List.of(
            new CoverageItem("SG-852", "conexoes funcionais (ramal x ligacao x imovel)",
                    "ManeuverModel.FunctionalConnection + FunctionalConnectionRules"),
            new CoverageItem("SG-887", "areas de influencia",
                    "InfluenceBasinService.influenceOf"),
            new CoverageItem("SG-889", "bacias e poligonos de roteamento",
                    "InfluenceBasinService.basinOf/basinsBySource + poligono convexo da area"),
            new CoverageItem("TR-1064", "tracado montante/jusante sobre a topologia persistida",
                    "ManeuverGraph.upstream*/downstream*"),
            new CoverageItem("TR-1065", "isolamento e setores de manobra",
                    "IsolationEngine + ManeuverSectors.precompute"),
            new CoverageItem("SAMAE-2.27", "itens de manobra: isolamento, aviso e OS em lote",
                    "ManeuverPlanService (aviso ED-41, lotes ED-36, baixa -> estado da valvula e periodo por imovel)"));

    private ManeuverCoverage() {
    }
}
