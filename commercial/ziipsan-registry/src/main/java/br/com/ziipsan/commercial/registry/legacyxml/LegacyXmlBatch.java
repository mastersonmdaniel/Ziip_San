package br.com.ziipsan.commercial.registry.legacyxml;

import java.util.UUID;

/**
 * Um lote de importação de XML de legado (P2.19, tabela
 * {@code legacy_xml_import_batches} da V14_3): a unidade de idempotência e
 * do relatório. Como no framework da P1.2, o lote é identificado por
 * (sistema de origem, chave externa do lote): reimportar a mesma chave
 * devolve o lote existente, sem revalidar, sem regravar na retenção e sem
 * duplicar documento.
 *
 * @param reportJson relatório do lote serializado com o {@code Json} do
 *                   core; {@code null} até a importação concluir — é o
 *                   MARCADOR DE CONCLUSÃO do lote (correção A2 da revisão):
 *                   lote com {@code reportJson} nulo é lote interrompido no
 *                   meio e nunca pode ser devolvido como relatório final
 */
public record LegacyXmlBatch(UUID id, UUID tenantId, String sourceSystem, String mappingName,
                             String mappingVersion, String externalBatchKey, String expectedIssuerCnpj,
                             UUID createdBy, String reportJson) {
}
