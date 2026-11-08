package br.com.ziipsan.platform.orgscope;

import java.time.Instant;
import java.util.UUID;

/**
 * Unidade organizacional do tenant (P3.30, V1_8, ADR-0040): um nó da
 * hierarquia (matriz, regional, escritório local — os nomes são do tenant).
 * {@code parentId} nulo = raiz. É o espelho em memória de {@code org_units}.
 *
 * <p>Invariantes do construtor: código e nome não vazios; a unidade não é pai
 * de si mesma (o CHECK da V1_8 veda o mesmo no banco). Ciclo mais longo
 * (A → B → A) e profundidade são conferidos pelo serviço ao gravar, andando
 * na cadeia de pais com conjunto de visitados (ver OrgScopeService).
 */
public record OrgUnit(UUID id, UUID tenantId, UUID parentId, String code, String name,
                      boolean active, Instant createdAt) {

    public OrgUnit {
        if (id == null || tenantId == null || createdAt == null) {
            throw new IllegalArgumentException("unidade organizacional exige id, tenant e criação");
        }
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("unidade organizacional exige código e nome");
        }
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("unidade organizacional não pode ser pai de si mesma");
        }
    }
}
