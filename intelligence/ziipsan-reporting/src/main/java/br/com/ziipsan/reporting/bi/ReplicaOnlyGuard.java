package br.com.ziipsan.reporting.bi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * ED-47: o teste do aceite em forma de regra — "nenhum relatório nem
 * consulta de BI no primário, com teste que confere a conexão de CADA
 * fonte de dados".
 *
 * <p>A guarda recebe TODAS as fontes registradas no Superset e o
 * endpoint conhecido do primário (vindo da configuração da aplicação,
 * nunca descoberto pela fonte) e devolve a lista de violações: fonte
 * alguma passa por média — uma única fonte no primário reprova o
 * conjunto inteiro. As checagens DESTA guarda são duas, independentes
 * de propósito:
 * <ol>
 *   <li>tipo declarado diferente de réplica;</li>
 *   <li>endpoint igual ao do primário (mesmo que o tipo minta).</li>
 * </ol>
 * A terceira barreira — role diferente da role do tenant dono da fonte
 * (fonte "compartilhada" disfarçada) — é invariante do construtor de
 * {@link SupersetDataSource}: fonte assim nem chega a existir para a
 * guarda conferir.
 * A fiação roda esta guarda na subida e periodicamente sobre o cadastro
 * de fontes do Superset; violação é evento de auditoria
 * ({@code DATASOURCE_REJECTED}) e motivo para não habilitar o BI.
 */
public final class ReplicaOnlyGuard {

    public record Violation(String dataSourceName, String code, String message) {
    }

    private final SupersetDataSource.Endpoint primaryEndpoint;

    public ReplicaOnlyGuard(SupersetDataSource.Endpoint primaryEndpoint) {
        this.primaryEndpoint = Objects.requireNonNull(primaryEndpoint, "endpoint do primário é obrigatório");
    }

    public List<Violation> checkAll(List<SupersetDataSource> dataSources) {
        Objects.requireNonNull(dataSources, "dataSources é obrigatório");
        List<Violation> violations = new ArrayList<>();
        for (SupersetDataSource ds : dataSources) {
            if (ds.kind() != SupersetDataSource.Kind.READ_REPLICA) {
                violations.add(new Violation(ds.name(), "bi-datasource-not-replica",
                        "Fonte de BI deve ser a réplica de leitura"));
            }
            if (ds.endpoint().key().equals(primaryEndpoint.key())) {
                violations.add(new Violation(ds.name(), "bi-datasource-on-primary",
                        "Fonte de BI aponta para o endpoint do primário"));
            }
        }
        return List.copyOf(violations);
    }

    /** Atalho de subida: qualquer violação impede habilitar o BI. */
    public void requireReplicaOnly(List<SupersetDataSource> dataSources) {
        List<Violation> violations = checkAll(dataSources);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Fontes de BI reprovadas: " + violations);
        }
    }
}
