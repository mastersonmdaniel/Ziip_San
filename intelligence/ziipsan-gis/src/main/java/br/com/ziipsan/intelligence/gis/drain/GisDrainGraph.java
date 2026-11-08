package br.com.ziipsan.intelligence.gis.drain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conectividade da drenagem sobre o grafo dirigido nó→arco→nó
 * (§5.3 e §5.4), em Java puro e testável — o mesmo espírito do
 * GisGraph da GIS-03 (P2.21): o traçado completo (montante/jusante,
 * isolamento) é da GIS-11/ED-54; aqui ficam as regras que o aceite
 * da GIS-13 nomeia para a DRENAGEM:
 *
 * <ul>
 *   <li>boca de lobo liga a PV pluvial ou galeria por INLET_LEAD
 *       (§5.3, erro);</li>
 *   <li>ciclo em rede por gravidade é erro (§5.4): o escoamento tem
 *       de ser um DAG até o exutório;</li>
 *   <li>componente sem exutório (OUTFALL) alcançável: aviso — rede
 *       legada pode terminar em lançamento ainda não cadastrado, mas
 *       o cadastro precisa enxergar isso;</li>
 *   <li>arco e nó do mesmo domínio (§5.3): garantido por construção
 *       nesta classe — todo nó/arco aqui é STORM; a conferência
 *       contra o banco é do worker de validação da GIS-03.</li>
 * </ul>
 *
 * A direção de cada arco é a do ESCOAMENTO (de montante para
 * jusante), resolvida por quem monta o grafo a partir de
 * {@code flow_direction} e das cotas (ver GisDrainRules).
 */
public final class GisDrainGraph {

    /** Arco dirigido: de {@code fromNode} (montante) para {@code toNode} (jusante). */
    public record DirectedArc(String arcCode, String arcTypeCode,
                              String fromNodeCode, String toNodeCode) {
    }

    /** Nó com o seu tipo do §4.4. */
    public record DrainNode(String code, String typeCode) {
    }

    private final Map<String, DrainNode> nodes = new HashMap<>();
    private final List<DirectedArc> arcs = new ArrayList<>();
    private final Map<String, List<DirectedArc>> outgoing = new HashMap<>();

    public GisDrainGraph(List<DrainNode> nodes, List<DirectedArc> arcs) {
        for (DrainNode n : nodes) {
            this.nodes.put(n.code(), n);
        }
        for (DirectedArc a : arcs) {
            if (!this.nodes.containsKey(a.fromNodeCode())
                    || !this.nodes.containsKey(a.toNodeCode())) {
                throw new IllegalArgumentException(
                        "arco " + a.arcCode() + " liga no fora do grafo de drenagem");
            }
            this.arcs.add(a);
            outgoing.computeIfAbsent(a.fromNodeCode(), k -> new ArrayList<>()).add(a);
        }
    }

    /**
     * Regra da boca de lobo (§5.3): cada INLET precisa de ao menos um
     * INLET_LEAD saindo dele. Devolve os códigos das bocas sem
     * ligação (erro STORM_INLET_WITHOUT_LEAD no worker).
     */
    public List<String> inletsWithoutLead() {
        List<String> out = new ArrayList<>();
        for (DrainNode n : nodes.values()) {
            if (!n.typeCode().equals("INLET")) {
                continue;
            }
            boolean hasLead = outgoing.getOrDefault(n.code(), List.of()).stream()
                    .anyMatch(a -> a.arcTypeCode().equals("INLET_LEAD"));
            if (!hasLead) {
                out.add(n.code());
            }
        }
        out.sort(String::compareTo);
        return List.copyOf(out);
    }

    /** O INLET_LEAD precisa desaguar em PV pluvial ou numa galeria (§5.3). */
    public List<String> leadsToInvalidTarget() {
        List<String> out = new ArrayList<>();
        for (DirectedArc a : arcs) {
            if (!a.arcTypeCode().equals("INLET_LEAD")) {
                continue;
            }
            DrainNode target = nodes.get(a.toNodeCode());
            boolean ok = target.typeCode().equals("STORM_MANHOLE");
            // "ou galeria": o lead pode terminar num nó que é ponta de
            // um arco de galeria (STORM_PIPE/BOX_CULVERT a jusante).
            if (!ok) {
                ok = outgoing.getOrDefault(target.code(), List.of()).stream()
                        .anyMatch(d -> d.arcTypeCode().equals("STORM_PIPE")
                                || d.arcTypeCode().equals("BOX_CULVERT"));
            }
            if (!ok) {
                out.add(a.arcCode());
            }
        }
        out.sort(String::compareTo);
        return List.copyOf(out);
    }

    /** Ciclo no grafo dirigido (§5.4: erro em rede por gravidade). */
    public boolean hasCycle() {
        Map<String, Integer> state = new HashMap<>(); // 0=novo 1=na pilha 2=fechado
        for (String code : nodes.keySet()) {
            if (state.getOrDefault(code, 0) == 0 && dfsCycle(code, state)) {
                return true;
            }
        }
        return false;
    }

    private boolean dfsCycle(String node, Map<String, Integer> state) {
        state.put(node, 1);
        for (DirectedArc a : outgoing.getOrDefault(node, List.of())) {
            int s = state.getOrDefault(a.toNodeCode(), 0);
            if (s == 1) {
                return true;
            }
            if (s == 0 && dfsCycle(a.toNodeCode(), state)) {
                return true;
            }
        }
        state.put(node, 2);
        return false;
    }

    /** Nós alcançáveis a jusante de um nó (inclusive ele mesmo). */
    public Set<String> downstreamOf(String nodeCode) {
        if (!nodes.containsKey(nodeCode)) {
            throw new IllegalArgumentException("no fora do grafo: " + nodeCode);
        }
        Set<String> seen = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        seen.add(nodeCode);
        queue.add(nodeCode);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (DirectedArc a : outgoing.getOrDefault(cur, List.of())) {
                if (seen.add(a.toNodeCode())) {
                    queue.add(a.toNodeCode());
                }
            }
        }
        return Set.copyOf(seen);
    }

    /** O nó deságua num exutório (OUTFALL) a jusante? */
    public boolean reachesOutfall(String nodeCode) {
        return downstreamOf(nodeCode).stream()
                .map(nodes::get)
                .anyMatch(n -> n != null && n.typeCode().equals("OUTFALL"));
    }

    /**
     * Nós de rede sem exutório alcançável (aviso
     * STORM_WITHOUT_OUTFALL): exclui o próprio OUTFALL.
     */
    public List<String> nodesWithoutOutfall() {
        List<String> out = new ArrayList<>();
        for (DrainNode n : nodes.values()) {
            if (n.typeCode().equals("OUTFALL")) {
                continue;
            }
            if (!reachesOutfall(n.code())) {
                out.add(n.code());
            }
        }
        out.sort(String::compareTo);
        return List.copyOf(out);
    }
}
