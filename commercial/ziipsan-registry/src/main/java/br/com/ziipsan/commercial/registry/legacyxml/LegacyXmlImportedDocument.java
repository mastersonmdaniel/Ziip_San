package br.com.ziipsan.commercial.registry.legacyxml;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Resultado persistido de UM arquivo de um lote (P2.19, tabela
 * {@code legacy_xml_import_documents} da V14_3): aceito ou recusado, com o
 * motivo. É a matéria-prima do relatório por lote e da contagem por
 * competência.
 *
 * <p><b>Dado pessoal:</b> nenhum campo guarda o destinatário — o documento
 * dele que vem no XML real não é extraído pelo parser nem persistido aqui.
 * O emitente é pessoa jurídica (CNPJ) e a chave de acesso é o identificador
 * público do documento fiscal.
 *
 * <p>Documento ACEITO sempre tem {@code accessKey}, {@code competence},
 * {@code issuerCnpj}, {@code protocol} e os campos de retenção
 * preenchidos, com {@code origin} = {@code LEGACY_IMPORT}. Documento
 * RECUSADO tem {@code rejection} preenchido e retenção nula; campos de
 * leitura ({@code accessKey} etc.) ficam com o que foi possível ler antes
 * da recusa — podem ser nulos quando nem a chave pôde ser lida.
 *
 * <p><b>Validação de construção (correção A6 da revisão, padrão do
 * {@code RegistroRetencaoXml} da P2.18):</b> as invariantes acima são
 * impostas no construtor — aceito sem retenção, recusado com retenção,
 * origem diversa de {@code LEGACY_IMPORT} e hash fora do formato são
 * inconstruíveis, em vez de só "o serviço não faz".
 */
public record LegacyXmlImportedDocument(UUID id, UUID tenantId, UUID batchId, String fileName,
                                        String accessKey, YearMonth competence, String issuerCnpj,
                                        String protocol, String xmlSha256, Status status,
                                        LegacyXmlRejection rejection, String origin,
                                        String retentionKey, String retentionUri, String retentionHashChain,
                                        String detailJson) {

    public LegacyXmlImportedDocument {
        java.util.Objects.requireNonNull(id, "Id do documento importado é obrigatório");
        java.util.Objects.requireNonNull(tenantId, "Tenant do documento importado é obrigatório");
        java.util.Objects.requireNonNull(batchId, "Lote do documento importado é obrigatório");
        java.util.Objects.requireNonNull(status, "Status do documento importado é obrigatório");
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Documento importado exige o nome do arquivo do lote.");
        }
        if (xmlSha256 == null || !xmlSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Documento importado exige xmlSha256 em 64 hex minúsculos.");
        }
        if (!LegacyXmlRetentionPort.ORIGIN_LEGACY_IMPORT.equals(origin)) {
            throw new IllegalArgumentException("Documento importado por este importador tem origem"
                    + " fixa LEGACY_IMPORT (recebido: " + origin + ").");
        }
        if (detailJson == null || detailJson.isBlank()) {
            throw new IllegalArgumentException("Documento importado exige o detalhe JSON da importação.");
        }
        if (status == Status.ACCEPTED) {
            if (rejection != null) {
                throw new IllegalArgumentException("Documento ACEITO não pode ter motivo de recusa.");
            }
            if (accessKey == null || !accessKey.matches("\\d{44}")) {
                throw new IllegalArgumentException(
                        "Documento ACEITO exige chave de acesso de 44 dígitos.");
            }
            if (competence == null) {
                throw new IllegalArgumentException("Documento ACEITO exige a competência da chave.");
            }
            if (issuerCnpj == null || !issuerCnpj.matches("\\d{14}")) {
                throw new IllegalArgumentException(
                        "Documento ACEITO exige o CNPJ do emitente (14 dígitos).");
            }
            if (protocol == null || protocol.isBlank()) {
                throw new IllegalArgumentException(
                        "Documento ACEITO exige o protocolo de autorização.");
            }
            if (retentionKey == null || retentionKey.isBlank()
                    || retentionUri == null || retentionUri.isBlank()) {
                throw new IllegalArgumentException(
                        "Documento ACEITO exige a referência da retenção (chave do objeto e URI).");
            }
            if (retentionHashChain == null || !retentionHashChain.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Documento ACEITO exige o elo da HashChain"
                        + " da retenção em 64 hex minúsculos.");
            }
        } else {
            if (rejection == null) {
                throw new IllegalArgumentException("Documento RECUSADO exige o motivo da recusa.");
            }
            if (retentionKey != null || retentionUri != null || retentionHashChain != null) {
                throw new IllegalArgumentException(
                        "Documento RECUSADO nunca tem referência de retenção.");
            }
        }
    }

    public enum Status {
        ACCEPTED, REJECTED
    }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }
}
