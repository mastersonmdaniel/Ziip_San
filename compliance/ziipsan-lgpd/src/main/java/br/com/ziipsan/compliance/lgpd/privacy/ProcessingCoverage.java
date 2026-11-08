package br.com.ziipsan.compliance.lgpd.privacy;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cobertura do registro de operações sobre a classificação (aceite da P3.40): coluna
 * classificada como dado pessoal SEM operação de tratamento associada aparece como
 * PENDÊNCIA no relatório do encarregado. É o teste que esta classe sustenta.
 *
 * <p>As entradas vêm de duas leituras reais da integração: o catálogo
 * {@code data_classification} da P0.11 ({@code public.data_classification}: tabela,
 * coluna, classe, motivo — uma linha por coluna com dado pessoal) e os vínculos
 * {@code lgpd_processing_record_columns} da {@code V23_1}. Esta classe só confronta os
 * dois conjuntos; não lê banco nem presume classificação.
 */
public final class ProcessingCoverage {

    private ProcessingCoverage() {
    }

    /** Coluna classificada como dado pessoal no catálogo da P0.11 (DAD-29). */
    public record ClassifiedColumn(String tableName, String columnName, String dataClass) {

        public ClassifiedColumn {
            if (tableName == null || tableName.isBlank()
                    || columnName == null || columnName.isBlank()) {
                throw new IllegalArgumentException("tabela e coluna são obrigatórias na classificação");
            }
            if (dataClass == null || dataClass.isBlank()) {
                throw new IllegalArgumentException("a classe (DAD-29) é obrigatória na classificação");
            }
        }

        /** Chave {@code tabela.coluna} usada no confronto e exibida na pendência. */
        public String key() {
            return tableName + "." + columnName;
        }
    }

    /** Vínculo coluna ↔ registro de operações ({@code lgpd_processing_record_columns}). */
    public record ColumnLink(String recordCode, String tableName, String columnName) {

        public ColumnLink {
            if (recordCode == null || recordCode.isBlank()
                    || tableName == null || tableName.isBlank()
                    || columnName == null || columnName.isBlank()) {
                throw new IllegalArgumentException("vínulo exige registro, tabela e coluna");
            }
        }

        /** Chave {@code tabela.coluna} do vínculo. */
        public String key() {
            return tableName + "." + columnName;
        }
    }

    /**
     * Pendências: colunas classificadas sem nenhum vínculo com registro de operações,
     * ordenadas por {@code tabela.coluna} para o relatório ser estável.
     */
    public static List<ClassifiedColumn> uncovered(List<ClassifiedColumn> classified,
                                                   List<ColumnLink> links) {
        Objects.requireNonNull(classified, "classified é obrigatório (catálogo da P0.11)");
        Objects.requireNonNull(links, "links é obrigatório (vínculos da V23_1)");
        Set<String> covered = links.stream().map(ColumnLink::key).collect(Collectors.toSet());
        return classified.stream()
                .filter(c -> !covered.contains(c.key()))
                .sorted(java.util.Comparator.comparing(ClassifiedColumn::key))
                .toList();
    }
}
