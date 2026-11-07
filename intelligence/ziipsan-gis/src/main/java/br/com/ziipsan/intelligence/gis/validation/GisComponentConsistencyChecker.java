package br.com.ziipsan.intelligence.gis.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Comparação pura entre duas partições de componentes: a do traçado Java
 * ({@link GisGraph}) e a do {@code pgr_connectedComponents} (§5.7).
 *
 * <p>É a prova "pgr igual ao traçado Java" do aceite da GIS-03: duas
 * partições são iguais quando todo componente de uma está contido num só
 * componente da outra, nos dois sentidos, e nenhum nó existe de um lado
 * só. As divergências saem agrupadas por componente (não por par de nós),
 * para a comparação em lote não explodir em redes grandes.
 */
public final class GisComponentConsistencyChecker {

    /** Divergência entre as duas partições. */
    public record Divergence(String kind, UUID representative, String detail) {
    }

    public static final String JAVA_SPLIT_IN_PGR = "JAVA_SPLIT_IN_PGR";
    public static final String PGR_SPLIT_IN_JAVA = "PGR_SPLIT_IN_JAVA";
    public static final String ONLY_IN_JAVA = "ONLY_IN_JAVA";
    public static final String ONLY_IN_PGR = "ONLY_IN_PGR";

    private GisComponentConsistencyChecker() {
    }

    /**
     * @param javaComponents nó → representante do componente (saída de {@link GisGraph#components})
     * @param pgrComponents  nó → id do componente devolvido pelo pgRouting
     * @return divergências; lista vazia significa partições idênticas
     */
    public static List<Divergence> compare(Map<UUID, UUID> javaComponents, Map<UUID, Long> pgrComponents) {
        List<Divergence> divergences = new ArrayList<>();

        Set<UUID> onlyJava = new HashSet<>(javaComponents.keySet());
        onlyJava.removeAll(pgrComponents.keySet());
        onlyJava.forEach(node -> divergences.add(new Divergence(ONLY_IN_JAVA, node,
                "nó presente no traçado Java e ausente no pgRouting")));
        Set<UUID> onlyPgr = new HashSet<>(pgrComponents.keySet());
        onlyPgr.removeAll(javaComponents.keySet());
        onlyPgr.forEach(node -> divergences.add(new Divergence(ONLY_IN_PGR, node,
                "nó presente no pgRouting e ausente no traçado Java")));

        // Componente Java espalhado por mais de um componente do pgRouting.
        Map<UUID, Set<Long>> pgrByJavaRoot = new HashMap<>();
        javaComponents.forEach((node, root) -> {
            Long pgr = pgrComponents.get(node);
            if (pgr != null) {
                pgrByJavaRoot.computeIfAbsent(root, k -> new HashSet<>()).add(pgr);
            }
        });
        pgrByJavaRoot.forEach((root, pgrIds) -> {
            if (pgrIds.size() > 1) {
                divergences.add(new Divergence(JAVA_SPLIT_IN_PGR, root,
                        "componente Java dividido em " + pgrIds.size() + " componentes no pgRouting"));
            }
        });

        // Componente do pgRouting espalhado por mais de um componente Java.
        Map<Long, Set<UUID>> javaByPgr = new HashMap<>();
        pgrComponents.forEach((node, component) -> {
            UUID root = javaComponents.get(node);
            if (root != null) {
                javaByPgr.computeIfAbsent(component, k -> new HashSet<>()).add(root);
            }
        });
        javaByPgr.forEach((component, roots) -> {
            if (roots.size() > 1) {
                divergences.add(new Divergence(PGR_SPLIT_IN_JAVA, roots.iterator().next(),
                        "componente " + component + " do pgRouting dividido em "
                                + roots.size() + " componentes no traçado Java"));
            }
        });
        return divergences;
    }
}
