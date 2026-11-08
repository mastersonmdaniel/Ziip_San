package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Conexões funcionais (SG-852): a ligação funcional é o que amarra a
 * rede ao imóvel — sem ela, o isolamento não enxerga o imóvel e o
 * aviso não chega. As regras abaixo são as de QUALIDADE desse
 * vínculo sobre o retrato (a existência básica — arco/nó, ligação,
 * imóvel — já é invariante do record em {@link ManeuverModel}):
 *
 * <ul>
 *   <li>conexão sobre arco FORA DE OPERAÇÃO ou sobre nó sem arco em
 *       operação: não funciona (a rede não a alimenta);</li>
 *   <li>conexão sem nenhuma unidade consumidora E sem economia:
 *       vínculo vazio — provável erro de carga a revisar;</li>
 *   <li>duas conexões funcionais para a MESMA ligação no mesmo
 *       arco/nó: duplicidade a reconciliar (a GIS-05 admite várias
 *       ligações por imóvel, mas não a mesma ligação duas vezes no
 *       mesmo ponto de derivação).</li>
 * </ul>
 * Severidade coerente com o §5.4 da GIS-03: problema de vínculo é
 * WARNING no painel (a rede existe; o vínculo é que está suspeito) —
 * ERROR fica para as regras estruturais da GIS-03.
 */
public final class FunctionalConnectionRules {

    public enum Severity { WARNING }

    public record ConnectionIssue(String ruleCode, Severity severity, String connectionCode, String message) {
    }

    private FunctionalConnectionRules() {
    }

    public static List<ConnectionIssue> validate(NetworkSnapshot snapshot) {
        List<ConnectionIssue> out = new ArrayList<>();
        var arcsById = snapshot.arcsById();
        ManeuverGraph graph = new ManeuverGraph(snapshot);
        for (FunctionalConnection c : snapshot.connections()) {
            if (c.arcId() != null && !arcsById.get(c.arcId()).inService()) {
                out.add(new ConnectionIssue("FUNCTIONAL_CONNECTION_ON_RETIRED_ARC", Severity.WARNING, c.code(),
                        "conexao funcional sobre arco fora de operacao"));
            }
            if (c.tapNodeId() != null && graph.arcsAt(c.tapNodeId()).isEmpty()) {
                out.add(new ConnectionIssue("FUNCTIONAL_CONNECTION_ON_ISOLATED_NODE", Severity.WARNING, c.code(),
                        "conexao funcional sobre no sem arco em operacao"));
            }
            if (c.consumerUnitIds().isEmpty() && c.economies() == 0) {
                out.add(new ConnectionIssue("FUNCTIONAL_CONNECTION_EMPTY", Severity.WARNING, c.code(),
                        "conexao funcional sem unidade consumidora e sem economia"));
            }
        }
        var seen = new java.util.HashSet<String>();
        for (FunctionalConnection c : snapshot.connections()) {
            String key = c.serviceConnectionId() + "@" + (c.arcId() != null ? c.arcId() : c.tapNodeId());
            if (!seen.add(key)) {
                out.add(new ConnectionIssue("FUNCTIONAL_CONNECTION_DUPLICATED", Severity.WARNING, c.code(),
                        "mesma ligacao ligada duas vezes no mesmo ponto de derivacao"));
            }
        }
        return List.copyOf(out);
    }
}
