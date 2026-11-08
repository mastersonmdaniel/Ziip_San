package br.com.ziipsan.integration.esfinge;

import br.com.ziipsan.core.api.ApiException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro da remessa e-Sfinge (ED-33): hash, versao do leiaute e retorno
 * REGISTRADOS, como manda o aceite.
 *
 * <p>Ciclo de vida (transicoes fora de ordem sao recusadas):
 * <pre>
 *   VALIDATED --(cliente baixa)--&gt; DOWNLOADED --(cliente informa o retorno)--&gt; RETURN_REGISTERED
 * </pre>
 * O retorno e fato EXTERNO (o que o canal oficial do TCE/SC respondeu ao
 * cliente): quem registra informa protocolo/referencia, desfecho e data —
 * nada e presumido pelo sistema. Sem download nao ha retorno (nada foi
 * enviado); retorno e unico (segundo registro e recusado; correcao de
 * retorno e ato de integracao/auditoria, fora desta classe).
 *
 * <p>Unicidade: para o mesmo tenant e competencia so pode existir UMA
 * remessa viva por versao de leiaute. Se o retorno registrado for
 * REJECTED, uma nova geracao da mesma competencia/versao e admitida
 * (retificacao — interpretacao declarada no RELATORIO-ED-33); com
 * qualquer outro desfecho (ou sem retorno), a duplicidade e recusada.
 */
public final class EsfingeRemittance {

    public enum Status { VALIDATED, DOWNLOADED, RETURN_REGISTERED }

    public enum ReturnOutcome { ACCEPTED, REJECTED, PARTIAL }

    /** Retorno do canal oficial, informado pelo cliente: protocolo, desfecho e data de recebimento. */
    public record ReturnReceipt(String protocolReference, ReturnOutcome outcome, LocalDate receivedOn, String notes) {
        public ReturnReceipt {
            if (protocolReference == null || protocolReference.isBlank())
                throw new IllegalArgumentException("retorno exige protocolo/referencia do canal oficial");
            Objects.requireNonNull(outcome, "retorno exige desfecho");
            Objects.requireNonNull(receivedOn, "retorno exige a data de recebimento");
        }
    }

    public record Remittance(UUID id, UUID tenantId, YearMonth competence, String layoutVersion,
                             boolean layoutOfficial, String sha256, String content, Status status,
                             OffsetDateTime generatedAt, OffsetDateTime downloadedAt, String downloadedBy,
                             ReturnReceipt returnReceipt) {
        public Remittance {
            Objects.requireNonNull(id, "remessa exige id");
            Objects.requireNonNull(tenantId, "remessa exige tenant");
            Objects.requireNonNull(competence, "remessa exige competencia");
            if (layoutVersion == null || layoutVersion.isBlank()) throw new IllegalArgumentException("remessa exige a versao do leiaute");
            if (sha256 == null || sha256.isBlank()) throw new IllegalArgumentException("remessa exige o hash do arquivo");
            if (content == null || content.isBlank()) throw new IllegalArgumentException("remessa exige o conteudo do arquivo");
            Objects.requireNonNull(status, "remessa exige situacao");
            Objects.requireNonNull(generatedAt, "remessa exige o instante de geracao");
            if (status == Status.DOWNLOADED && downloadedAt == null)
                throw new IllegalArgumentException("remessa baixada exige o instante do download");
            if (status == Status.RETURN_REGISTERED && returnReceipt == null)
                throw new IllegalArgumentException("remessa com retorno registrado exige o retorno");
        }
        Remittance withDownload(OffsetDateTime at, String by) {
            return new Remittance(id, tenantId, competence, layoutVersion, layoutOfficial, sha256, content,
                    Status.DOWNLOADED, generatedAt, at, by, null);
        }
        Remittance withReturn(ReturnReceipt receipt) {
            return new Remittance(id, tenantId, competence, layoutVersion, layoutOfficial, sha256, content,
                    Status.RETURN_REGISTERED, generatedAt, downloadedAt, downloadedBy, receipt);
        }
    }

    /** Livro de remessas em memoria: somente acrescimo, com substituicao controlada de situacao. */
    public static final class Ledger {
        private final List<Remittance> entries = new ArrayList<>();

        public synchronized Remittance register(UUID tenantId, YearMonth competence,
                                                EsfingeRemittanceFile.GeneratedFile file, OffsetDateTime generatedAt) {
            Objects.requireNonNull(file, "arquivo gerado e obrigatorio");
            for (Remittance existing : entries) {
                if (!existing.tenantId().equals(tenantId) || !existing.competence().equals(competence)
                        || !existing.layoutVersion().equals(file.layoutVersion())) continue;
                boolean rejected = existing.status() == Status.RETURN_REGISTERED
                        && existing.returnReceipt().outcome() == ReturnOutcome.REJECTED;
                if (!rejected)
                    throw new ApiException.Conflict("esfinge-remittance-duplicate",
                            "Ja existe remessa " + existing.status() + " para a competencia " + competence
                                    + " na versao de leiaute " + file.layoutVersion() + " (hash " + existing.sha256() + ").");
            }
            var remittance = new Remittance(UUID.randomUUID(), tenantId, competence, file.layoutVersion(),
                    file.layoutOfficial(), file.sha256(), file.json(), Status.VALIDATED, generatedAt, null, null, null);
            entries.add(remittance);
            return remittance;
        }

        public synchronized Remittance markDownloaded(UUID remittanceId, OffsetDateTime at, String downloadedBy) {
            Objects.requireNonNull(at, "instante do download e obrigatorio");
            if (downloadedBy == null || downloadedBy.isBlank())
                throw new IllegalArgumentException("download exige quem baixou (a remessa e baixada pelo cliente)");
            Remittance current = find(remittanceId);
            if (current.status() != Status.VALIDATED)
                throw new ApiException.Conflict("esfinge-download-out-of-order",
                        "Download so a partir de VALIDATED; a remessa esta " + current.status() + ".");
            return replace(current.withDownload(at, downloadedBy));
        }

        public synchronized Remittance registerReturn(UUID remittanceId, ReturnReceipt receipt) {
            Objects.requireNonNull(receipt, "retorno e obrigatorio");
            Remittance current = find(remittanceId);
            if (current.status() != Status.DOWNLOADED)
                throw new ApiException.Conflict("esfinge-return-out-of-order",
                        "Retorno so depois do download (nada foi enviado antes); a remessa esta " + current.status() + ".");
            return replace(current.withReturn(receipt));
        }

        public Remittance find(UUID id) {
            return entries.stream().filter(e -> e.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ApiException.NotFound("remessa e-Sfinge nao encontrada: " + id));
        }

        public List<Remittance> entries() { return List.copyOf(entries); }

        public List<Remittance> ofTenant(UUID tenantId) {
            return entries.stream().filter(e -> e.tenantId().equals(tenantId)).toList();
        }

        private Remittance replace(Remittance updated) {
            for (int i = 0; i < entries.size(); i++)
                if (entries.get(i).id().equals(updated.id())) { entries.set(i, updated); return updated; }
            throw new IllegalStateException("remessa nao encontrada no livro: " + updated.id());
        }
    }

    private EsfingeRemittance() {}
}
