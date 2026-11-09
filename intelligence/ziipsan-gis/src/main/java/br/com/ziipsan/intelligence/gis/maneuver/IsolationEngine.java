package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.FunctionalConnection;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkArc;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkNode;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.NetworkSnapshot;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.ValveState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Isolamento sobre a topologia persistida (GIS-11): dado o trecho de
 * trabalho (arco ou nó), expande pelo grafo NÃO dirigido até as
 * primeiras válvulas OPERÁVEIS — essas são as válvulas a fechar.
 * Válvula INOPERÁVEL não barra a expansão: é ATRAVESSADA e reportada
 * nomeadamente (o aceite pede exatamente este caso). Válvula já
 * FECHADA barra sem entrar na lista (já está fechada).
 *
 * <p>Área afetada: com as válvulas que barram removidas do grafo
 * (as a fechar E as operáveis já fechadas), o componente do alvo —
 * semeado de TODAS as pontas do alvo que não estão barradas — é o
 * trecho isolado; ele só é área de
 * desabastecimento se NÃO contiver fonte — se a expansão alcançar
 * uma fonte sem válvula operável no caminho, ou se o componente
 * isolado ainda tiver fonte, o isolamento é IMPOSSÍVEL
 * ({@code isolable=false}, motivo nomeado) e nenhuma área afetada é
 * afirmada. Se NENHUMA ponta sobrevive (arco-alvo com válvula
 * operável nas DUAS pontas — correção B3-M1 da auditoria, rodada 3),
 * o trecho isolado é o próprio arco-alvo com suas pontas: fechando
 * as duas válvulas, exatamente esse arco fica isolado.
 *
 * <p>Imóveis afetados: pelas conexões funcionais (SG-852) cujo nó de
 * derivação ou arco cai na área afetada, ordenados com clientes
 * SENSÍVEIS PRIMEIRO, depois os essenciais, depois os demais por
 * código da conexão — a ordem é a do aviso da ED-41 e da lista do
 * plano de manobra.
 */
public final class IsolationEngine {

    /** Motivo nomeado de isolamento impossível. */
    public enum Failure { SOURCE_REACHED_WITHOUT_VALVE, TARGET_IS_VALVE, TARGET_NOT_IN_SERVICE }

    public record IsolationResult(boolean isolable, Failure failure,
                                  List<ValveState> valvesToClose,
                                  List<ValveState> inoperableTraversed,
                                  List<UUID> affectedNodeIds, List<UUID> affectedArcIds,
                                  List<FunctionalConnection> affectedConnections,
                                  List<UUID> affectedUnitIds, int totalEconomies,
                                  List<FunctionalConnection> essentialConnections) {
        public IsolationResult {
            valvesToClose = List.copyOf(valvesToClose);
            inoperableTraversed = List.copyOf(inoperableTraversed);
            affectedNodeIds = List.copyOf(affectedNodeIds);
            affectedArcIds = List.copyOf(affectedArcIds);
            affectedConnections = List.copyOf(affectedConnections);
            affectedUnitIds = List.copyOf(affectedUnitIds);
            essentialConnections = List.copyOf(essentialConnections);
        }

        static IsolationResult failed(Failure f) {
            return new IsolationResult(false, f, List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), 0, List.of());
        }
    }

    /** Ordem do aviso e do plano: sensível primeiro, essencial depois, código por fim. */
    public static final Comparator<FunctionalConnection> NOTICE_ORDER =
            Comparator.comparing((FunctionalConnection c) -> !c.sensitive())
                    .thenComparing(c -> !c.essential())
                    .thenComparing(FunctionalConnection::code);

    private IsolationEngine() {
    }

    public static IsolationResult isolate(NetworkSnapshot snapshot, Target target) {
        var valvesByNode = snapshot.valvesByNode();
        var nodesById = snapshot.nodesById();
        ManeuverGraph graph = new ManeuverGraph(snapshot);

        Set<UUID> seeds = new LinkedHashSet<>();
        if (target.arcId() != null) {
            NetworkArc a = snapshot.arcsById().get(target.arcId());
            if (a == null) throw new IllegalArgumentException("arco do alvo fora do retrato");
            if (!a.inService()) return IsolationResult.failed(Failure.TARGET_NOT_IN_SERVICE);
            seeds.add(a.node1Id());
            seeds.add(a.node2Id());
        } else {
            if (!nodesById.containsKey(target.nodeId()))
                throw new IllegalArgumentException("no do alvo fora do retrato");
            if (valvesByNode.containsKey(target.nodeId()))
                return IsolationResult.failed(Failure.TARGET_IS_VALVE);
            seeds.add(target.nodeId());
        }
        // Expansão até as primeiras válvulas operáveis abertas.
        // Ponta do arco-alvo que é válvula já nasce fronteira: operável
        // aberta entra em "a fechar"; inoperável é atravessada.
        Set<UUID> region = new LinkedHashSet<>(seeds);
        List<ValveState> toClose = new ArrayList<>();
        List<ValveState> traversed = new ArrayList<>();
        Set<UUID> seenValves = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        for (UUID seed : seeds) {
            ValveState v = valvesByNode.get(seed);
            if (v == null) { queue.add(seed); continue; }
            seenValves.add(seed);
            if (!v.operable()) { traversed.add(v); queue.add(seed); }
            else if (v.position() == ManeuverModel.ValvePosition.OPEN) { toClose.add(v); }
            // operável já fechada: fronteira pronta, sem OS de fechamento
        }
        boolean sourceReached = seeds.stream().anyMatch(id -> nodesById.get(id).source());
        while (!queue.isEmpty()) {
            UUID cur = queue.poll();
            for (NetworkArc a : graph.arcsAt(cur)) {
                UUID other = a.node1Id().equals(cur) ? a.node2Id() : a.node1Id();
                ValveState v = valvesByNode.get(other);
                if (v != null) {
                    if (!seenValves.add(other)) continue;
                    if (!v.operable()) {
                        traversed.add(v); // inoperável: atravessa e continua a expansão
                    } else if (v.position() == ManeuverModel.ValvePosition.OPEN) {
                        toClose.add(v);   // operável aberta: barra — é válvula a fechar
                        continue;
                    } else {
                        continue;         // operável já fechada: barra, sem OS de fechamento
                    }
                }
                if (region.add(other)) {
                    if (nodesById.get(other).source()) sourceReached = true;
                    queue.add(other);
                }
            }
        }
        if (sourceReached) return IsolationResult.failed(Failure.SOURCE_REACHED_WITHOUT_VALVE);

        // Área afetada: componente do alvo sem as válvulas que barram.
        // Barram TANTO as válvulas a fechar QUANTO as operáveis JÁ
        // FECHADAS (correção D1 da revisão): sem as já fechadas no
        // conjunto, o componente as atravessava, alcançava a fonte e
        // condenava um isolamento válido.
        Set<UUID> barred = new HashSet<>();
        toClose.forEach(v -> barred.add(v.nodeId()));
        snapshot.valves().stream()
                .filter(v -> v.operable()
                        && v.position() == ManeuverModel.ValvePosition.CLOSED)
                .forEach(v -> barred.add(v.nodeId()));
        // A área é semeada das pontas do alvo que NÃO estão barradas
        // (correção D2 da revisão): se uma ponta do arco-alvo é a
        // própria válvula a fechar, semear dela devolvia o conjunto
        // vazio com isolable=true; a área é o componente do arco
        // removido, igual qualquer que seja a orientação do arco.
        // Válvula INOPERÁVEL atravessada não barra: a semente sobre
        // ela vale (ela está DENTRO da área, como no resultado
        // conhecido de A5, que inclui o nó VX).
        Set<UUID> affectedNodes = new LinkedHashSet<>();
        for (UUID seed : seeds) {
            if (!barred.contains(seed)) {
                affectedNodes.addAll(graph.componentWithout(seed, barred));
            }
        }
        // B3-M1 (auditoria rodada 3, residual do D2): se NENHUMA semente
        // sobreviveu — arco-alvo com válvula operável nas DUAS pontas — a
        // área nascia vazia com isolable=true (falha silenciosa: plano
        // com aviso para ninguém). O trecho isolado é o próprio
        // arco-alvo com suas pontas: fechando as duas válvulas,
        // exatamente esse arco fica isolado, e as conexões derivadas
        // dele entram na área. Recusar seria um falso negativo (o
        // isolamento É possível); incluir o arco é o que o código
        // sustenta — mesma lógica do D2 (área = componente do arco
        // removido). Vale também quando as duas válvulas já estão
        // fechadas (barram do mesmo modo, sem OS de fechamento).
        if (affectedNodes.isEmpty()) {
            affectedNodes.addAll(seeds);
        }
        for (UUID n : affectedNodes) {
            if (nodesById.get(n).source()) return IsolationResult.failed(Failure.SOURCE_REACHED_WITHOUT_VALVE);
        }
        List<UUID> affectedArcs = new ArrayList<>();
        for (NetworkArc a : snapshot.serviceArcs()) {
            if (affectedNodes.contains(a.node1Id()) && affectedNodes.contains(a.node2Id()))
                affectedArcs.add(a.id());
        }
        Set<UUID> affectedArcSet = new HashSet<>(affectedArcs);

        List<FunctionalConnection> affected = snapshot.connections().stream()
                .filter(c -> (c.tapNodeId() != null && affectedNodes.contains(c.tapNodeId()))
                        || (c.arcId() != null && affectedArcSet.contains(c.arcId())))
                .sorted(NOTICE_ORDER)
                .toList();
        List<UUID> units = affected.stream().flatMap(c -> c.consumerUnitIds().stream()).distinct().toList();
        int economies = affected.stream().mapToInt(FunctionalConnection::economies).sum();
        List<FunctionalConnection> essential = affected.stream().filter(FunctionalConnection::essential).toList();

        Comparator<ValveState> byCode = Comparator.comparing(ValveState::code);
        toClose.sort(byCode);
        traversed.sort(byCode);
        List<UUID> nodeOrder = new ArrayList<>(affectedNodes);
        nodeOrder.sort(Comparator.comparing(id -> nodesById.get(id).code()));
        affectedArcs.sort(Comparator.comparing(id -> snapshot.arcsById().get(id).code()));
        return new IsolationResult(true, null, toClose, traversed, nodeOrder, affectedArcs,
                affected, units, economies, essential);
    }
}
