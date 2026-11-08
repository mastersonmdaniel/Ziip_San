package br.com.ziipsan.commercial.billing.servicecatalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluxo encadeado por tipo de solicitação ({@code service_type.workflow}, ED-34): etapas,
 * dependências e prazos do catálogo. O grafo é validado na construção (dependência
 * inexistente e ciclo são recusados). {@link Progress} só conclui etapa com as
 * dependências concluídas — pular etapa é recusado.
 *
 * <p>Prazos: {@code deadlineHours} vem do catálogo do tenant/agência (na ligação nova,
 * dos arts. 72 e 73 da NR 11/ANA lidos da regra da agência); nenhum prazo é fixado aqui.
 */
public final class WorkflowDefinition {

    public record Step(String code, String name, List<String> dependsOn, Integer deadlineHours) {
        public Step {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo da etapa e obrigatorio");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("nome da etapa e obrigatorio");
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
            if (deadlineHours != null && deadlineHours <= 0) throw new IllegalArgumentException("prazo da etapa deve ser positivo");
        }
    }

    private final List<Step> steps;
    private final Map<String, Step> byCode = new LinkedHashMap<>();

    public WorkflowDefinition(List<Step> steps) {
        if (steps == null || steps.isEmpty()) throw new IllegalArgumentException("fluxo sem etapas");
        this.steps = List.copyOf(steps);
        for (Step s : steps) {
            if (byCode.put(s.code(), s) != null) throw new IllegalArgumentException("etapa duplicada: " + s.code());
        }
        for (Step s : steps) for (String d : s.dependsOn())
            if (!byCode.containsKey(d)) throw new IllegalArgumentException("etapa " + s.code() + " depende de etapa inexistente: " + d);
        // ciclo: DFS com cores
        Map<String, Integer> color = new LinkedHashMap<>();
        for (Step s : steps) visit(s.code(), color);
    }

    private void visit(String code, Map<String, Integer> color) {
        int c = color.getOrDefault(code, 0);
        if (c == 1) throw new IllegalArgumentException("ciclo no fluxo na etapa " + code);
        if (c == 2) return;
        color.put(code, 1);
        for (String d : byCode.get(code).dependsOn()) visit(d, color);
        color.put(code, 2);
    }

    public List<Step> steps() { return steps; }
    public Step step(String code) {
        Step s = byCode.get(code);
        if (s == null) throw new IllegalArgumentException("etapa inexistente: " + code);
        return s;
    }

    /** Andamento de uma solicitação no fluxo. */
    public static final class Progress {
        private final WorkflowDefinition def;
        private final Set<String> done = new HashSet<>();
        public Progress(WorkflowDefinition def) { this.def = def; }
        public boolean canComplete(String code) {
            return !done.contains(code) && done.containsAll(def.step(code).dependsOn());
        }
        public void complete(String code) {
            if (done.contains(code)) throw new IllegalStateException("etapa ja concluida: " + code);
            if (!canComplete(code))
                throw new IllegalStateException("dependencias nao concluidas para a etapa " + code + ": " + def.step(code).dependsOn());
            done.add(code);
        }
        public boolean finished() { return done.size() == def.steps().size(); }
        public List<String> completed() {
            List<String> out = new ArrayList<>();
            for (Step s : def.steps()) if (done.contains(s.code())) out.add(s.code());
            return out;
        }
    }
}
