package br.com.ziipsan.reporting.situation;

import java.util.List;
import java.util.UUID;

/**
 * ED-93: a porta da tabela de estado resumida e nativa — o UNICO
 * canal de leitura do console e o unico destino da projecao.
 * No SQL pendente, {@code ops_situation_state}; a implementacao JDBC
 * e fiacao de integracao (pendencia P-ED93-01). A troca do retrato e
 * por tenant inteiro: a projecao substitui o conjunto do tenant, e
 * linha de outro tenant nunca aparece na leitura (isolamento provado
 * nos testes sobre a implementacao em memoria).
 */
public interface SituationStateStore {

    /** Substitui o retrato completo do tenant pelas linhas projetadas. */
    void replaceTenantSnapshot(UUID tenantId, List<SituationStateRow> rows);

    /** Le as linhas do tenant (lista vazia quando nada foi projetado). */
    List<SituationStateRow> rows(UUID tenantId);
}
