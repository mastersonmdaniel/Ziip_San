package br.com.ziipsan.commercial.collection.dunning.negativation;

import br.com.ziipsan.commercial.collection.dunning.CollectionGates;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Remessa e retorno por arquivo (ED-30): para cada canal de
 * {@code neg.channels}, a remessa e gerada SOMENTE com unidades julgadas
 * elegiveis por {@link ListingEligibility} — unidade recusada NUNCA entra
 * no arquivo, e a recusa fica registrada na decisao. O retorno importado
 * atualiza o livro: aceita confirma a inclusao; rejeitada volta com o
 * motivo do canal, verbatim (nenhum codigo de ocorrencia e inventado —
 * layout [SEM FONTE], ver {@code NegativationPorts}).
 *
 * <p>Finalidades: NEGATIVATION (biro) e PROTEST (central de protesto),
 * pelos MESMOS canais parametrizados — o protesto da ED-30 e o protesto
 * por arquivo a central, distinto do protesto de CDA da ED-31
 * ({@code ProtestControl}, PD-10), com o qual nao se confunde nem se
 * duplica.
 */
public final class RemittanceAndReturn {

    /** Candidato a remessa: a unidade, o debito agregado e a data de referencia. */
    public record Candidate(UUID unitId, UUID customerId, java.math.BigDecimal amount, LocalDate firstDueDate,
                            ListingEligibility.UnitFacts facts) {
        public Candidate {
            if (unitId == null || customerId == null || amount == null || amount.signum() <= 0
                    || firstDueDate == null || facts == null)
                throw new IllegalArgumentException("candidato exige unidade, titular, valor positivo, vencimento e fatos");
        }
    }

    /** Resultado da remessa de um canal: arquivo, inclusoes criadas e recusas registradas. */
    public record RemittanceOutcome(String channel, NegativationPorts.BureauFilePort.GeneratedFile file,
                                    List<ListingLedger.Listing> included,
                                    List<ListingEligibility.Decision> refused) {}

    /** Resultado da importacao de um retorno: aceitas e rejeitadas (motivo do canal). */
    public record ReturnOutcome(String channel, int accepted, List<RejectedReturn> rejected) {}
    public record RejectedReturn(UUID listingId, String channelCode, String channelMessage) {}

    private final NegativationParameters params;
    private final ListingEligibility eligibility;
    private final NegativationPorts.BureauFilePort filePort;
    private final ListingLedger ledger;

    public RemittanceAndReturn(NegativationParameters params, ListingEligibility eligibility,
                               NegativationPorts.BureauFilePort filePort, ListingLedger ledger) {
        this.params = Objects.requireNonNull(params, "parametros neg.* sao obrigatorios");
        this.eligibility = Objects.requireNonNull(eligibility, "elegibilidade e obrigatoria");
        this.filePort = Objects.requireNonNull(filePort, "porta de arquivo [SEM FONTE] e obrigatoria");
        this.ledger = Objects.requireNonNull(ledger, "livro de inclusoes e obrigatorio");
    }

    /**
     * Gera a remessa de UM canal: avalia cada candidato; elegiveis viram
     * entradas do arquivo e inclusoes no livro; recusados voltam com a
     * decisao e os motivos. Canal fora de {@code neg.channels} e recusado.
     */
    public RemittanceOutcome generate(UUID tenantId, String channel, CollectionGates.ListingPurpose purpose,
                                      List<Candidate> candidates,
                                      br.com.ziipsan.commercial.collection.dunning.prescription.Prescription.ProviderNature nature,
                                      br.com.ziipsan.commercial.collection.dunning.prescription.Prescription.ClaimType claim,
                                      boolean legalAnalysisRecorded, LocalDate evaluationDate) {
        if (!params.channels().contains(channel))
            throw new IllegalArgumentException("canal fora de neg.channels: " + channel);
        if (purpose == CollectionGates.ListingPurpose.LAWSUIT)
            throw new IllegalArgumentException("remessa por arquivo nao cobre ajuizamento (isso e da ED-31)");
        List<ListingEligibility.Decision> refused = new ArrayList<>();
        List<NegativationPorts.BureauFilePort.FileEntry> entries = new ArrayList<>();
        List<ListingLedger.Listing> included = new ArrayList<>();
        for (Candidate c : candidates) {
            var decision = eligibility.evaluate(tenantId, c.facts(), purpose, nature, claim,
                    legalAnalysisRecorded, evaluationDate);
            if (!decision.eligible()) { refused.add(decision); continue; }
            ListingLedger.Listing listing = ledger.registerInclusion(tenantId, c.unitId(), c.customerId(),
                    channel, purpose.name(), c.amount());
            entries.add(new NegativationPorts.BureauFilePort.FileEntry(listing.id(), c.unitId(), c.customerId(),
                    c.amount(), c.firstDueDate()));
            included.add(listing);
        }
        var file = filePort.generateRemittance(tenantId, channel, purpose.name(), List.copyOf(entries));
        if (file.entryCount() != entries.size())
            throw new IllegalStateException("arquivo gerado com contagem divergente da remessa: "
                    + file.entryCount() + " != " + entries.size());
        return new RemittanceOutcome(channel, file, List.copyOf(included), List.copyOf(refused));
    }

    /** Importa o retorno de um arquivo: aceitas confirmam; rejeitadas sao registradas com o motivo do canal. */
    public ReturnOutcome importReturn(UUID tenantId, String channel, String fileRef) {
        var results = filePort.importReturn(tenantId, channel, fileRef);
        int accepted = 0;
        List<RejectedReturn> rejected = new ArrayList<>();
        for (var r : results) {
            if (r.accepted()) { accepted++; continue; }
            rejected.add(new RejectedReturn(r.listingId(), r.channelCode(), r.channelMessage()));
        }
        return new ReturnOutcome(channel, accepted, List.copyOf(rejected));
    }
}
