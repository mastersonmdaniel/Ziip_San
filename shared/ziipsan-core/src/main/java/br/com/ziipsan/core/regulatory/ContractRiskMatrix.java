package br.com.ziipsan.core.regulatory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Matriz de riscos do instrumento (NR 5/ANA) como PRIMEIRO USO do
 * registro de riscos da P3.31: cada linha da matriz REFERENCIA um
 * risco ja registrado na P3.31 ({@code cert_risks}) — esta entrega
 * nao cria segundo registro de riscos nem duplica titulo/escore.
 * A existencia do risco referenciado e conferida pela porta
 * {@link ContractPorts.RiskRegistryPort} no servico.
 *
 * <p>A alocacao (quem assume o risco no instrumento) e fato
 * DECLARADO do contrato do tenant. Os tres valores abaixo sao a
 * regra declarada DESTA entrega para representar a alocacao —
 * nenhuma categoria de risco da NR 5/ANA e semeada aqui: o conteudo
 * da matriz (quais riscos, com que alocacao) e do instrumento de
 * cada tenant [SEM FONTE para conteudo presumido].
 */
public final class ContractRiskMatrix {

    private ContractRiskMatrix() {
    }

    /** Alocacao contratual do risco, como declarada no instrumento. */
    public enum Allocation { PROVIDER, GRANTING_AUTHORITY, SHARED }

    /**
     * Linha da matriz: risco da P3.31 + alocacao + referencia da
     * clausula/item da matriz no instrumento.
     */
    public record Entry(UUID id, UUID tenantId, UUID contractId, UUID riskId,
                        Allocation allocation, String matrixRef) {
        public Entry {
            Objects.requireNonNull(id, "linha da matriz sem id");
            Objects.requireNonNull(tenantId, "linha da matriz sem tenant");
            Objects.requireNonNull(contractId, "linha da matriz sem instrumento");
            Objects.requireNonNull(riskId, "linha da matriz sem o risco da P3.31");
            Objects.requireNonNull(allocation, "linha da matriz sem alocacao declarada");
            if (matrixRef == null || matrixRef.isBlank())
                throw new IllegalArgumentException("linha da matriz sem referencia no instrumento (NR 5/ANA)");
        }
    }

    /** Matriz de um instrumento: conjunto de linhas, sem risco repetido. */
    public record Matrix(UUID contractId, List<Entry> entries) {
        public Matrix {
            Objects.requireNonNull(contractId, "matriz sem instrumento");
            entries = entries == null ? List.of() : List.copyOf(entries);
            long distinctRisks = entries.stream().map(Entry::riskId).distinct().count();
            if (distinctRisks != entries.size())
                throw new IllegalArgumentException("matriz com o mesmo risco da P3.31 em mais de uma linha");
            for (Entry e : entries) {
                if (!contractId.equals(e.contractId()))
                    throw new IllegalArgumentException("linha da matriz de outro instrumento");
            }
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }
}
