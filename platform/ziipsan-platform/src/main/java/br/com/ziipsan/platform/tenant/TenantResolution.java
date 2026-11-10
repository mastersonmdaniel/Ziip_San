package br.com.ziipsan.platform.tenant;

import java.util.Objects;
import java.util.UUID;

/**
 * P4.6, item 4 do aceite: a resolucao de um tenant devolve tambem o CLUSTER e o
 * MODO de isolamento — nao so o schema. E sobre esta resolucao que a aplicacao
 * roteia os pools por cluster e por papel ({@link ClusterPoolRouter} sobre a
 * P2.2).
 *
 * <p>Companheira de {@code TenantCatalog.Entry} (id, schema, status), que NAO
 * muda: quem so precisa do schema continua a usa-la. Esta resolucao completa
 * nasce de {@code TenantCatalog.findResolution(UUID)}.
 */
public record TenantResolution(UUID id, String schema, String clusterId,
                               IsolationMode isolationMode, String status) {

    public TenantResolution {
        Objects.requireNonNull(id, "id do tenant e obrigatorio");
        Objects.requireNonNull(schema, "schema do tenant e obrigatorio");
        if (clusterId == null || clusterId.isBlank()) {
            clusterId = "default";
        }
        Objects.requireNonNull(isolationMode, "isolation_mode do tenant e obrigatorio");
    }

    /** So ACTIVE atende requisicoes (mesma regra de TenantCatalog.Entry). */
    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
