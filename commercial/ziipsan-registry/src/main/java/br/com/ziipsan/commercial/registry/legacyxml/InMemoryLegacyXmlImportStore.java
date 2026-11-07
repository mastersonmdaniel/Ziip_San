package br.com.ziipsan.commercial.registry.legacyxml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Duplo em memória da {@link LegacyXmlImportStore} para os testes da
 * P2.19, impondo a mesma unicidade que a V14_3 impõe no banco: lote único
 * por (tenant, sistema, chave externa) e no máximo um documento ACEITO
 * por (tenant, chave de acesso) — o índice único parcial da migration.
 * Vive no código principal pelo mesmo precedente da P2.10
 * ({@code InMemoryV1MigrationStore}): os mesmos testes rodam em memória
 * aqui e contra o PostgreSQL na integração.
 */
public final class InMemoryLegacyXmlImportStore implements LegacyXmlImportStore {

    private final Map<UUID, LegacyXmlBatch> batches = new LinkedHashMap<>();
    private final Map<UUID, List<LegacyXmlImportedDocument>> documents = new LinkedHashMap<>();

    @Override
    public Optional<LegacyXmlBatch> findBatch(UUID tenantId, String sourceSystem, String externalBatchKey) {
        return batches.values().stream()
                .filter(b -> b.tenantId().equals(tenantId)
                        && b.sourceSystem().equals(sourceSystem)
                        && b.externalBatchKey().equals(externalBatchKey))
                .findFirst();
    }

    @Override
    public Optional<LegacyXmlBatch> findBatchById(UUID tenantId, UUID batchId) {
        LegacyXmlBatch batch = batches.get(batchId);
        return batch != null && batch.tenantId().equals(tenantId)
                ? Optional.of(batch) : Optional.empty();
    }

    @Override
    public LegacyXmlBatch createBatch(UUID tenantId, String sourceSystem, String mappingName,
                                      String mappingVersion, String externalBatchKey,
                                      String expectedIssuerCnpj, UUID createdBy) {
        if (findBatch(tenantId, sourceSystem, externalBatchKey).isPresent()) {
            throw new IllegalStateException("Lote já existe para (tenant, sistema, chave externa): "
                    + externalBatchKey);
        }
        LegacyXmlBatch batch = new LegacyXmlBatch(UUID.randomUUID(), tenantId, sourceSystem, mappingName,
                mappingVersion, externalBatchKey, expectedIssuerCnpj, createdBy, null);
        batches.put(batch.id(), batch);
        documents.put(batch.id(), new ArrayList<>());
        return batch;
    }

    @Override
    public void saveReport(UUID batchId, String reportJson) {
        LegacyXmlBatch atual = batches.get(batchId);
        if (atual == null) {
            throw new IllegalArgumentException("Lote não encontrado: " + batchId);
        }
        batches.put(batchId, new LegacyXmlBatch(atual.id(), atual.tenantId(), atual.sourceSystem(),
                atual.mappingName(), atual.mappingVersion(), atual.externalBatchKey(),
                atual.expectedIssuerCnpj(), atual.createdBy(), reportJson));
    }

    @Override
    public void insertDocument(LegacyXmlImportedDocument document) {
        if (document.accepted() && alreadyAccepted(document.tenantId(), document.accessKey())) {
            throw new IllegalStateException("Documento já aceito para a chave " + document.accessKey()
                    + " (espelho do índice único parcial da V14_3)");
        }
        documents.computeIfAbsent(document.batchId(), k -> new ArrayList<>()).add(document);
    }

    @Override
    public List<LegacyXmlImportedDocument> listDocuments(UUID tenantId, UUID batchId) {
        return documents.getOrDefault(batchId, List.of()).stream()
                .filter(d -> d.tenantId().equals(tenantId))
                .toList();
    }

    @Override
    public boolean alreadyAccepted(UUID tenantId, String accessKey) {
        return documents.values().stream().flatMap(List::stream)
                .anyMatch(d -> d.tenantId().equals(tenantId) && d.accepted()
                        && accessKey.equals(d.accessKey()));
    }
}
