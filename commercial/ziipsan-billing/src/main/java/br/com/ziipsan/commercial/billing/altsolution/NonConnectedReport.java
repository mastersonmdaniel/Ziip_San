package br.com.ziipsan.commercial.billing.altsolution;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lista de não conectados e relatório de imóveis com rede disponível sem
 * ligação (ED-88; CAD-87), pelo cruzamento ramal × imóvel da P2.23: para
 * cada imóvel, a P2.23 informa se existe ramal de esgoto e se há ligação
 * ativa; esta classe cruza esse fato com a disponibilidade da rede e com a
 * dispensa por inviabilidade, e monta a lista destinada ao titular do
 * serviço e à agência.
 *
 * <p>Entram na lista apenas imóveis com rede DISPONÍVEL, SEM ligação ativa
 * e SEM dispensa comprovada. Imóvel dispensado não é "não conectado" para
 * fins de cobrança/compulsória — está fora do ciclo por inviabilidade.
 */
public final class NonConnectedReport {

    public enum Audience { TITLEHOLDER, AGENCY }

    /** Fato de cruzamento de um imóvel (ramal e ligação lidos da P2.23). */
    public record CrossingFact(UUID propertyId, UUID consumerUnitId, boolean networkAvailable,
                               boolean sewerBranchExists, boolean sewerConnectionActive,
                               boolean exemptedByProvenInfeasibility) {
        public CrossingFact {
            Objects.requireNonNull(propertyId, "imovel e obrigatorio");
            Objects.requireNonNull(consumerUnitId, "unidade consumidora e obrigatoria");
        }
        boolean nonConnected() {
            return networkAvailable && !sewerConnectionActive && !exemptedByProvenInfeasibility;
        }
    }

    public record Entry(UUID propertyId, UUID consumerUnitId, boolean sewerBranchExists,
                        boolean branchWithoutConnection) {}

    public record Report(Audience audience, List<Entry> entries) {
        public Report {
            Objects.requireNonNull(audience, "destinatario (titular ou agencia) e obrigatorio");
            entries = List.copyOf(entries);
        }
        public int total() { return entries.size(); }
        /** CSV simples do relatório (cabeçalho + uma linha por imóvel), para o titular/agência. */
        public String toCsv() {
            StringBuilder sb = new StringBuilder("property_id,consumer_unit_id,sewer_branch_exists,branch_without_connection\n");
            for (Entry e : entries) {
                sb.append(e.propertyId()).append(',').append(e.consumerUnitId()).append(',')
                        .append(e.sewerBranchExists()).append(',').append(e.branchWithoutConnection()).append('\n');
            }
            return sb.toString();
        }
    }

    private NonConnectedReport() {}

    public static Report build(Audience audience, List<CrossingFact> facts) {
        Objects.requireNonNull(facts, "fatos do cruzamento ramal x imovel (P2.23) sao obrigatorios");
        List<Entry> entries = facts.stream()
                .filter(CrossingFact::nonConnected)
                .map(f -> new Entry(f.propertyId(), f.consumerUnitId(), f.sewerBranchExists(),
                        f.sewerBranchExists() && !f.sewerConnectionActive()))
                .sorted(Comparator.comparing(e -> e.propertyId().toString()))
                .toList();
        return new Report(audience, entries);
    }
}
