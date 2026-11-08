package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.compliance.lgpd.incident.DataCategory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Registro de operações de tratamento — ROPA (LGPD, art. 37), por finalidade e base legal,
 * sobre a classificação de dados por coluna da P0.11 (DAD-29): as categorias do registro
 * são as classes do catálogo {@code data_classification} ({@link DataCategory}: ID, CT,
 * LOC, FIN, SOC, SENS), nunca texto livre novo.
 *
 * <p>Campos do art. 37 e do aceite: finalidade (código do catálogo {@code consent_purposes}
 * da P1.3, DAD-30 — teto de 40 caracteres da coluna do catálogo), base legal, categorias
 * de dado, categorias de titulares, compartilhamento, retenção (linha da
 * {@code lgpd_retention_schedule} quando houver), operador e suboperadores.
 *
 * <p>Origem: {@code FACTORY} (pré-carga do pacote {@code sanitation}) ou {@code TENANT}
 * (criada/editada pelo tenant). Editar NÃO reescreve: toda edição produz a versão
 * seguinte ({@link #edited}), e a versão anterior é supersedida na escrita — o registro
 * é a prova do tratamento ao longo do tempo. Na escrita, desativar a versão anterior
 * ({@code active = false}) e inserir a nova ativa acontecem na MESMA transação: o
 * índice parcial {@code uq_processing_records_one_active} da {@code V23_1} reprova
 * qualquer ordem diferente.
 *
 * <p>PRÉ-CARGA DE FÁBRICA — LACUNA DECLARADA: nenhuma fonte disponível (plano, fontes
 * espelhadas, repo até o 429) enumera as operações de fábrica do pacote {@code sanitation}
 * nem semeia códigos no catálogo {@code consent_purposes} (que nasce vazio por tenant).
 * Inventar a lista seria fabricar o registro jurídico do tenant. A {@code V23_1} cria a
 * estrutura e a repetível {@code R__lgpd_factory_processing_records.sql} está reservada
 * para a carga, SEM nenhum SQL de carga nesta entrega (só o {@code set_config} do
 * tenant): as linhas entram quando o manifesto da vertical {@code sanitation} (P0.47)
 * for lido na integração — pendência nomeada no relatório da P3.40.
 */
public record ProcessingRecord(String code, String purposeCode, LegalBasis legalBasis,
                               Set<DataCategory> dataCategories, String dataSubjectCategories,
                               String sharingDescription, UUID retentionScheduleId,
                               String operatorName, List<String> subprocessors,
                               int version, Origin origin, boolean active) {

    /** Origem do registro: pré-carga de fábrica ou autoria do tenant. */
    public enum Origin {
        FACTORY, TENANT
    }

    /** Teto da coluna {@code code} de {@code consent_purposes} ({@code V3_12}: VARCHAR(40)). */
    public static final int PURPOSE_CODE_MAX = 40;
    /** Teto do código do registro ({@code V23_1}: VARCHAR(60), precedente dos códigos da {@code V8_1}). */
    public static final int CODE_MAX = 60;

    public ProcessingRecord {
        if (code == null || code.isBlank() || code.length() > CODE_MAX) {
            throw new IllegalArgumentException("code do registro é obrigatório e tem no máximo "
                    + CODE_MAX + " caracteres");
        }
        if (purposeCode == null || purposeCode.isBlank() || purposeCode.length() > PURPOSE_CODE_MAX) {
            throw new IllegalArgumentException("purposeCode é obrigatório (catálogo da P1.3, DAD-30) "
                    + "e tem no máximo " + PURPOSE_CODE_MAX + " caracteres");
        }
        Objects.requireNonNull(legalBasis, "legalBasis é obrigatória (art. 37)");
        if (dataCategories == null || dataCategories.isEmpty()) {
            throw new IllegalArgumentException("dataCategories é obrigatório: o registro nasce da "
                    + "classificação por coluna da P0.11 (DAD-29)");
        }
        dataCategories = Set.copyOf(dataCategories);
        if (dataSubjectCategories == null || dataSubjectCategories.isBlank()) {
            throw new IllegalArgumentException("dataSubjectCategories é obrigatório (art. 37)");
        }
        if (sharingDescription == null || sharingDescription.isBlank()) {
            throw new IllegalArgumentException("sharingDescription é obrigatório (art. 37): "
                    + "sem compartilhamento, declare isso no texto — vazio não é declaração");
        }
        if (operatorName == null || operatorName.isBlank()) {
            throw new IllegalArgumentException("operatorName é obrigatório (art. 37)");
        }
        subprocessors = subprocessors == null ? List.of() : List.copyOf(subprocessors);
        if (version < 1) {
            throw new IllegalArgumentException("version começa em 1");
        }
        Objects.requireNonNull(origin, "origin é obrigatória");
    }

    /**
     * Edição pelo tenant: devolve a versão SEGUINTE com o conteúdo novo; o registro atual
     * não muda (record imutável) e é supersedido na escrita. Registro inativo não é
     * editável — reativar é ato separado e declarado, não efeito colateral de edição.
     */
    public ProcessingRecord edited(String newPurposeCode, LegalBasis newLegalBasis,
                                   Set<DataCategory> newDataCategories, String newSubjectCategories,
                                   String newSharingDescription, UUID newRetentionScheduleId,
                                   String newOperatorName, List<String> newSubprocessors) {
        if (!active) {
            throw new IllegalStateException("registro inativo não é editável: " + code);
        }
        return new ProcessingRecord(code, newPurposeCode, newLegalBasis, newDataCategories,
                newSubjectCategories, newSharingDescription, newRetentionScheduleId,
                newOperatorName, newSubprocessors, version + 1, origin, true);
    }
}
