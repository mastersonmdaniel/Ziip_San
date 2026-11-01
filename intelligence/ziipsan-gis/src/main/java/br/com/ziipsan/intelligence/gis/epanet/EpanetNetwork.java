package br.com.ziipsan.intelligence.gis.epanet;

import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory EPANET network: the node and link collections that make up a
 * runnable hydraulic model, plus the options it would be run with.
 *
 * <p>Plain domain object - it holds topology only and performs no hydraulics.</p>
 *
 * <p>Units: lengths and elevations in metres, pipe and valve diameters in millimetres, flows in
 * L/s, pump power in kW. The .inp writer uses {@code Units LPS}.</p>
 */
@Getter
@Setter
public class EpanetNetwork {

    private String title = "";
    private String description = "";

    private List<EpanetJunction> junctions = new ArrayList<>();
    private List<EpanetTank> tanks = new ArrayList<>();
    private List<EpanetReservoir> reservoirs = new ArrayList<>();
    private List<EpanetPump> pumps = new ArrayList<>();
    private List<EpanetPipe> pipes = new ArrayList<>();
    private List<EpanetValve> valves = new ArrayList<>();

    private EpanetSimulationOptions options = new EpanetSimulationOptions();

    /** Posição de cada nó (x = longitude, y = latitude), para a seção [COORDINATES]. */
    private Map<String, Coordinate> nodeCoordinates = new LinkedHashMap<>();

    /** Vértices intermediários de tubulações não divididas, para a seção [VERTICES]. */
    private Map<String, List<Coordinate>> linkVertices = new LinkedHashMap<>();

    /**
     * Problemas que IMPEDEM gerar um modelo confiável: cota ausente, ponta de tubulação sem nó,
     * bomba sem potência. Enquanto houver algum, o escritor de .inp se recusa a produzir o
     * arquivo, porque o solver devolveria números plausíveis e falsos.
     */
    private List<String> issues = new ArrayList<>();

    /** Observações que não impedem o modelo (ex.: comprimento calculado da geometria). */
    private List<String> warnings = new ArrayList<>();

    // ------------------------------------------------------------------
    // Deterministic structural queries. These are pure topology/count
    // operations on the model as given - they are NOT hydraulic results.
    // Pressure, flow and headloss require a real EPANET solver.
    // ------------------------------------------------------------------

    /**
     * Total node count: junctions, tanks and reservoirs. Pumps are links in EPANET, not nodes
     * (an earlier version counted them as both).
     */
    public int nodeCount() {
        return junctions.size() + tanks.size() + reservoirs.size();
    }

    /** Total link count across pipes, valves and pumps. */
    public int linkCount() {
        return pipes.size() + valves.size() + pumps.size();
    }

    /** Sum of pipe lengths in metres. */
    public double totalPipeLength() {
        return pipes.stream().mapToDouble(EpanetPipe::getLength).sum();
    }

    /** Distinct nodes referenced as an endpoint by any pipe. */
    public int connectedNodeCount() {
        return (int) getPipes().stream()
                .flatMap(p -> java.util.stream.Stream.of(p.getNode1(), p.getNode2()))
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .count();
    }
}
