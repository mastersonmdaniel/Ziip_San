package br.com.ziipsan.core.restore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Comparacao entre o schema extraido do instante-alvo e o estado atual
 * do tenant (P3.33): a etapa entre a extracao e a aplicacao, cujo
 * resultado e apresentado ao tenant na aprovacao e gravado na trilha.
 *
 * <p>Cada tabela e fotografada por contagem de linhas e SHA-256 do seu
 * conteudo canonico (a ordem de geracao do hash e do chamador/roteiro:
 * dump ordenado da tabela na instancia lateral e no banco atual, com o
 * mesmo comando — hashes de comandos diversos nao se comparam, e esta
 * classe RECUSA fotografia sem hash em vez de comparar no escuro).
 *
 * <p>Acoes por tabela:
 * <ul>
 *   <li>{@code UNCHANGED} — mesmo hash E mesma contagem: nada a fazer
 *       (hash igual com contagem diversa e fotografia inconsistente e
 *       cai em {@code RESTORE}, nunca em "nada a fazer");</li>
 *   <li>{@code RESTORE} — hash diverso: no modo REPLACE a tabela atual e
 *       substituida pela extraida; no modo MERGE ela entra na lista de
 *       mesclagem revisada (a mesclagem linha a linha e do roteiro, com
 *       a tabela nesta lista — esta classe nao inventa regra de
 *       mesclagem de negocio);</li>
 *   <li>{@code ONLY_IN_EXTRACTED} — tabela que existia no instante-alvo
 *       e nao existe hoje: volta na substituicao; na mesclagem, entra
 *       na lista de revisao;</li>
 *   <li>{@code ONLY_IN_CURRENT} — tabela criada depois do instante-alvo:
 *       NUNCA apagada por esta comparacao (apagar tabela que a
 *       restauracao nao conhece destruiria dado posterior ao alvo);
 *       registrada para a decisao explicita do tenant.</li>
 * </ul>
 */
public final class RestoreDiff {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** Fotografia de uma tabela: nome qualificado pelo schema, linhas e SHA-256 do conteudo. */
    public record TableSnapshot(String table, long rows, String sha256) {
        public TableSnapshot {
            if (table == null || table.isBlank()) {
                throw new IllegalArgumentException("table e obrigatorio");
            }
            if (rows < 0) {
                throw new IllegalArgumentException("rows nao pode ser negativo: " + table);
            }
            if (sha256 == null || !SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException(
                        "sha256 invalido para " + table + " (esperado 64 hex minusculos): sem hash nao ha comparacao");
            }
        }
    }

    /** Acao proposta para uma tabela. */
    public enum TableAction {
        UNCHANGED,
        RESTORE,
        ONLY_IN_EXTRACTED,
        ONLY_IN_CURRENT
    }

    /** Linha da comparacao. {@code extracted}/{@code current} nulos no lado ausente. */
    public record TableDiff(String table, TableAction action,
                            TableSnapshot extracted, TableSnapshot current) {
    }

    private RestoreDiff() {
    }

    /**
     * Compara as duas fotografias (listas de snapshots, uma por tabela).
     * Tabela repetida dentro da mesma fotografia e erro do chamador
     * ({@link IllegalArgumentException}).
     */
    public static List<TableDiff> compare(List<TableSnapshot> extracted, List<TableSnapshot> current) {
        Objects.requireNonNull(extracted, "extracted e obrigatorio");
        Objects.requireNonNull(current, "current e obrigatorio");
        Map<String, TableSnapshot> ext = index(extracted, "extraida");
        Map<String, TableSnapshot> cur = index(current, "atual");
        Map<String, TableDiff> byTable = new TreeMap<>();
        for (TableSnapshot e : ext.values()) {
            TableSnapshot c = cur.get(e.table());
            if (c == null) {
                byTable.put(e.table(), new TableDiff(e.table(), TableAction.ONLY_IN_EXTRACTED, e, null));
            } else if (e.sha256().equals(c.sha256()) && e.rows() == c.rows()) {
                byTable.put(e.table(), new TableDiff(e.table(), TableAction.UNCHANGED, e, c));
            } else {
                byTable.put(e.table(), new TableDiff(e.table(), TableAction.RESTORE, e, c));
            }
        }
        for (TableSnapshot c : cur.values()) {
            byTable.putIfAbsent(c.table(),
                    new TableDiff(c.table(), TableAction.ONLY_IN_CURRENT, null, c));
        }
        return List.copyOf(byTable.values());
    }

    /** Verdadeiro se alguma tabela difere ou existe de um lado so (ha o que restaurar/revisar). */
    public static boolean hasChanges(List<TableDiff> diff) {
        Objects.requireNonNull(diff, "diff e obrigatorio");
        return diff.stream().anyMatch(d -> d.action() != TableAction.UNCHANGED);
    }

    /** Resumo contavel da comparacao, para a trilha e para a aprovacao do tenant. */
    public static Map<String, Long> summary(List<TableDiff> diff) {
        Objects.requireNonNull(diff, "diff e obrigatorio");
        Map<String, Long> counts = new LinkedHashMap<>();
        for (TableAction action : TableAction.values()) {
            counts.put(action.name(), 0L);
        }
        for (TableDiff d : diff) {
            counts.merge(d.action().name(), 1L, Long::sum);
        }
        return counts;
    }

    private static Map<String, TableSnapshot> index(List<TableSnapshot> snapshots, String side) {
        Map<String, TableSnapshot> map = new LinkedHashMap<>();
        for (TableSnapshot s : snapshots) {
            if (map.putIfAbsent(s.table(), s) != null) {
                throw new IllegalArgumentException(
                        "tabela repetida na fotografia " + side + ": " + s.table());
            }
        }
        return map;
    }

    /** Lista mutavel auxiliar para quem monta a fotografia a partir de consultas. */
    public static List<TableSnapshot> snapshots(TableSnapshot... snapshots) {
        return new ArrayList<>(List.of(snapshots));
    }
}
