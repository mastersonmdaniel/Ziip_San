package br.com.ziipsan.intelligence.gis.hydraulic.solver;

import java.util.List;
import java.util.Map;

/**
 * Porta do solver hidráulico (OPE-35). A implementação de produção é a
 * {@link NativeSolverRunner}, que executa a biblioteca oficial compilada
 * (EPANET 2.2 / SWMM 5.2) como processo — ver o relatório de viabilidade.
 * Não existe implementação "estimada": sem biblioteca, a execução falha
 * com {@link SolverUnavailableException}, e nenhum resultado é produzido.
 */
public interface SolverPort {

    enum SolverKind {EPANET, SWMM}

    record NodeValue(double demandLps, double headM, double pressureM) {
    }

    record LinkValue(double flowLps, double velocityMps, double unitHeadlossMperKm) {
    }

    /**
     * Resultado de uma execução REAL do solver.
     *
     * @param solverExecuted sempre true quando devolvido (sem solver, a porta lança exceção)
     * @param solverVersion  versão declarada pelo próprio relatório do solver (ex.: "2.2.0")
     * @param warnings       avisos do relatório do solver, verbatim quando existirem
     * @param continuityErrorPct erro de continuidade do SWMM (%); null no EPANET
     */
    record SolverResult(SolverKind kind, boolean solverExecuted, String solverVersion,
                        Map<String, NodeValue> nodes, Map<String, LinkValue> links,
                        Map<String, Double> subcatchmentRunoffMm, List<String> warnings,
                        Double continuityErrorPct, String reportExcerpt) {
        public SolverResult {
            nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
            links = links == null ? Map.of() : Map.copyOf(links);
            subcatchmentRunoffMm = subcatchmentRunoffMm == null ? Map.of() : Map.copyOf(subcatchmentRunoffMm);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    class SolverUnavailableException extends RuntimeException {
        public SolverUnavailableException(String message) {
            super(message);
        }
    }

    class SolverExecutionException extends RuntimeException {
        public SolverExecutionException(String message) {
            super(message);
        }
    }

    SolverResult runEpanet(String inpContent);

    SolverResult runSwmm(String inpContent);
}
