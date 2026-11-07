package br.com.ziipsan.commercial.registry.legacyxml;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência das tabelas próprias da V14_3 (lotes e documentos
 * de XML de legado), no mesmo desenho de portas do framework da P1.2 e da
 * P2.10: o motor ({@link LegacyXmlImportService}) fala com esta interface,
 * os testes rodam contra {@link InMemoryLegacyXmlImportStore} e a
 * integração liga a implementação JDBC sobre as tabelas da V14_3 (com o
 * tenant da RLS — toda consulta é por tenant).
 */
public interface LegacyXmlImportStore {

    /**
     * Lote pela identidade do framework: (tenant, sistema de origem, chave externa).
     * Devolve também lote NÃO concluído ({@code reportJson} nulo — correção
     * A2 da revisão): distinguir "já importado" de "interrompido no meio" é
     * decisão de quem lê o lote, nunca desta porta.
     */
    Optional<LegacyXmlBatch> findBatch(UUID tenantId, String sourceSystem, String externalBatchKey);

    /** Lote pelo id, dentro do tenant (correção A10: lote inexistente é erro nomeado, não divergência contra zero). */
    Optional<LegacyXmlBatch> findBatchById(UUID tenantId, UUID batchId);

    LegacyXmlBatch createBatch(UUID tenantId, String sourceSystem, String mappingName, String mappingVersion,
                               String externalBatchKey, String expectedIssuerCnpj, UUID createdBy);

    /**
     * Grava o relatório final do lote (JSON do {@code Json} do core).
     * É o MARCADOR DE CONCLUSÃO do lote (correção A2): antes desta
     * gravação, o lote é um lote interrompido e não pode ser lido como
     * relatório final por ninguém.
     */
    void saveReport(UUID batchId, String reportJson);

    void insertDocument(LegacyXmlImportedDocument document);

    List<LegacyXmlImportedDocument> listDocuments(UUID tenantId, UUID batchId);

    /** Verdadeiro se a chave já foi ACEITA em qualquer lote do tenant (a trava de duplicidade entre lotes). */
    boolean alreadyAccepted(UUID tenantId, String accessKey);
}
