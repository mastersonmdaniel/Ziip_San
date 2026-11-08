package br.com.ziipsan.platform.implantation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Linhagem da migração (ED-09, sobre o framework declarativo da P1.2, PLT-16).
 *
 * <p>A P1.2 já grava o relatório de linhagem por registro na carga (origem →
 * destino de cada linha encenada). O que a ED-09 acrescenta é a régua de
 * completude na implantação: <b>nenhum registro aplicado sem linhagem</b> e
 * <b>nenhuma linhagem órfã</b> (apontando para registro que não foi aplicado),
 * por lote. A conciliação da P1.2 confere totais; a linhagem confere o
 * caminho de cada registro — são provas diferentes e as duas fecham a
 * migração do primeiro cliente de edital.
 *
 * <p>Cada entrada diz: lote, sistema de origem, chave do registro na origem,
 * destino (tabela e chave no Ziip), mapeamento e versão que o transformou.
 * O mapeamento e a versão são os do arquivo declarativo da P1.2
 * ({@code LegacyMapping.name/version}) — linhagem sem a versão do mapeamento
 * não permite reproduzir a carga.
 */
public final class MigrationLineage {

    private MigrationLineage() {
    }

    public record Entry(UUID batchId, String sourceSystem, String sourceKey,
                        String targetTable, String targetKey,
                        String mappingName, String mappingVersion) {
        public Entry {
            if (batchId == null) {
                throw new IllegalArgumentException("O lote da linhagem é obrigatório.");
            }
            if (sourceSystem == null || sourceSystem.isBlank()
                    || sourceKey == null || sourceKey.isBlank()
                    || targetTable == null || targetTable.isBlank()
                    || targetKey == null || targetKey.isBlank()
                    || mappingName == null || mappingName.isBlank()
                    || mappingVersion == null || mappingVersion.isBlank()) {
                throw new IllegalArgumentException(
                        "Linhagem incompleta: origem, destino, mapeamento e versão são obrigatórios.");
            }
        }

        String identity() {
            return targetTable + "|" + targetKey;
        }

        String sourceIdentity() {
            return sourceSystem + "|" + sourceKey;
        }
    }

    public record Check(boolean complete, List<String> appliedWithoutLineage,
                        List<String> lineageWithoutApplied, List<String> duplicateLineage) {
    }

    /**
     * Confere a linhagem de um lote contra o conjunto de registros aplicados
     * (identidade {@code tabela|chave}).
     */
    public static Check verify(List<Entry> lineage, Set<String> appliedIdentities) {
        if (lineage == null || appliedIdentities == null) {
            throw new IllegalArgumentException("Linhagem e registros aplicados são obrigatórios.");
        }
        Set<String> covered = new HashSet<>();
        Set<String> seenSources = new HashSet<>();
        Set<String> seenTargets = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (Entry e : lineage) {
            covered.add(e.identity());
            if (!seenSources.add(e.sourceIdentity())) {
                duplicates.add(e.sourceIdentity());
            }
            if (!seenTargets.add(e.identity())) {
                duplicates.add("DESTINO_REPETIDO:" + e.identity());
            }
        }
        List<String> missing = new ArrayList<>();
        for (String applied : appliedIdentities) {
            if (!covered.contains(applied)) {
                missing.add(applied);
            }
        }
        List<String> orphans = new ArrayList<>();
        for (String c : covered) {
            if (!appliedIdentities.contains(c)) {
                orphans.add(c);
            }
        }
        missing.sort(String::compareTo);
        orphans.sort(String::compareTo);
        duplicates.sort(String::compareTo);
        boolean complete = missing.isEmpty() && orphans.isEmpty() && duplicates.isEmpty();
        return new Check(complete, List.copyOf(missing), List.copyOf(orphans),
                List.copyOf(duplicates));
    }
}
