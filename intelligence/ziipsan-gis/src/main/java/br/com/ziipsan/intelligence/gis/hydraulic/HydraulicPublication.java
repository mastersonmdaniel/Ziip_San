package br.com.ziipsan.intelligence.gis.hydraulic;

import br.com.ziipsan.intelligence.gis.hydraulic.solver.SolverPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Portão de publicação do resultado hidráulico (regra N7 do 15c, nos
 * termos literais do aceite da GIS-12): resultado só é publicado com
 * solver, versão, premissas e grau de confiança. MARCADOR DECLARADO: o
 * texto do documento 15c não foi localizado nas fontes locais desta
 * frente — o portão implementa exatamente os quatro campos que o aceite
 * nomeia, sem inventar o restante da regra.
 */
public final class HydraulicPublication {

    private HydraulicPublication() {
    }

    public enum Confidence {HIGH, MEDIUM, LOW}

    /** Resultado publicável: o do solver + metadados obrigatórios da N7. */
    public record PublishedResult(SolverPort.SolverResult solverResult, String solverName,
                                  String solverVersion, List<String> premises, Confidence confidence) {
        public PublishedResult {
            premises = premises == null ? List.of() : List.copyOf(premises);
        }
    }

    /** Problemas que barram a publicação; lista vazia = pode publicar. */
    public static List<String> gateProblems(PublishedResult r) {
        List<String> problems = new ArrayList<>();
        if (r == null) return List.of("resultado ausente");
        if (r.solverResult() == null || !r.solverResult().solverExecuted()) {
            problems.add("sem execucao real do solver (solverExecuted=false)");
        }
        if (r.solverName() == null || r.solverName().isBlank()) problems.add("solver nao identificado");
        if (r.solverVersion() == null || r.solverVersion().isBlank()) problems.add("versao do solver ausente");
        if (r.premises() == null || r.premises().isEmpty()) problems.add("premissas ausentes");
        if (r.confidence() == null) problems.add("grau de confianca ausente");
        return problems;
    }

    public static PublishedResult publish(PublishedResult r) {
        List<String> problems = gateProblems(r);
        if (!problems.isEmpty()) throw new IllegalStateException("Publicacao barrada (N7): " + String.join("; ", problems));
        return r;
    }
}
