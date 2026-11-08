package br.com.ziipsan.compliance.lgpd.privacyiso;

import br.com.ziipsan.compliance.lgpd.incident.DataCategory;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Mapa de dados do pacote (ED-74), apoiado na classificação de dados por
 * coluna da P0.11 (DAD-29): cada linha é uma coluna classificada como
 * dado pessoal (tabela, coluna, classe do catálogo
 * {@code public.data_classification}) e o conjunto de registros de
 * operações da P3.40 que a tratam. Coluna classificada SEM registro
 * ligado é PENDÊNCIA do mapa — a mesma régua da cobertura da P3.40
 * ({@code privacy.ProcessingCoverage}), aqui como visão do sistema de
 * gestão da privacidade: é deste confronto que a autoavaliação aponta
 * onde o mapa de dados não fecha.
 *
 * <p>Diferença para a P3.40 (sem duplicar): lá, a pendência sai no
 * relatório do encarregado como obrigação legal de todo tenant; aqui, o
 * mapa é artefato do SGPI e só existe com a norma ligada — a fábrica
 * {@link #build} passa pela porta da ativação. As classes de dado são o
 * enum {@link DataCategory} já usado pela P3.10/P3.40 (ID, CT, LOC,
 * FIN, SOC, SENS), nunca texto livre novo.
 */
public final class PrivacyDataMap {

    private PrivacyDataMap() {
    }

    /** Linha do mapa: coluna classificada e os códigos de registro que a cobrem (vazio = pendência). */
    public record Entry(String tableName, String columnName, DataCategory dataClass,
                        Set<String> processingRecordCodes) {

        /** Tetos dos identificadores PostgreSQL, como na {@code V23_1}. */
        public static final int NAME_MAX = 63;

        public Entry {
            if (tableName == null || tableName.isBlank() || tableName.length() > NAME_MAX
                    || columnName == null || columnName.isBlank()
                    || columnName.length() > NAME_MAX) {
                throw new IllegalArgumentException("tabela e coluna são obrigatórias e têm no "
                        + "máximo " + NAME_MAX + " caracteres (identificador PostgreSQL)");
            }
            Objects.requireNonNull(dataClass, "dataClass (DAD-29) é obrigatória");
            if (processingRecordCodes != null) {
                for (String code : processingRecordCodes) {
                    if (code == null || code.isBlank()
                            || code.length() > ProcessingRoleAssignment.RECORD_CODE_MAX) {
                        throw new IllegalArgumentException("cada código de registro de "
                                + "operações é obrigatório (código do registro da P3.40, "
                                + "nunca branco) e tem no máximo "
                                + ProcessingRoleAssignment.RECORD_CODE_MAX + " caracteres "
                                + "(V23_1: lgpd_processing_records.code VARCHAR(60)): um "
                                + "código inválido não pode cobrir a coluna nem suprimir "
                                + "a pendência dela no mapa");
                    }
                }
            }
            processingRecordCodes = processingRecordCodes == null
                    ? Set.of() : Set.copyOf(processingRecordCodes);
        }

        /**
         * Chave {@code tabela.coluna} da linha, em minúsculas: identificadores
         * PostgreSQL sem aspas dobram para minúsculas, então
         * {@code Consumers.name} e {@code consumers.name} são a MESMA coluna —
         * a chave normalizada faz a detecção de duplicidade (e a ordenação das
         * pendências) enxergar as duas grafias como uma só.
         */
        public String key() {
            return (tableName + "." + columnName).toLowerCase(Locale.ROOT);
        }

        /** Coluna classificada sem nenhum registro de operações ligado. */
        public boolean uncovered() {
            return processingRecordCodes.isEmpty();
        }
    }

    /**
     * Monta o mapa com a norma ligada, recusando coluna repetida: a
     * classificação da P0.11 tem uma linha por tabela+coluna, e mapa com
     * a mesma coluna duas vezes esconderia pendência.
     */
    public static List<Entry> build(Iso27701Activation activation, List<Entry> entries) {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        Objects.requireNonNull(entries, "entries é obrigatório (classificação da P0.11)");
        Set<String> seen = new HashSet<>();
        for (Entry e : entries) {
            if (!seen.add(e.key())) {
                throw new IllegalArgumentException("coluna repetida no mapa de dados: " + e.key());
            }
        }
        return List.copyOf(entries);
    }

    /** Pendências do mapa: colunas classificadas sem registro ligado, em ordem estável. */
    public static List<Entry> uncovered(List<Entry> entries) {
        Objects.requireNonNull(entries, "entries é obrigatório");
        return entries.stream().filter(Entry::uncovered)
                .sorted(Comparator.comparing(Entry::key)).toList();
    }
}
