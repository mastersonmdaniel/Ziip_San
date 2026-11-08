package br.com.ziipsan.commercial.collection.dunning.negativation;

import br.com.ziipsan.commercial.collection.dunning.CollectionGates;
import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Elegibilidade de inclusao (ED-30): TODA unidade avaliada recebe uma
 * decisao com TODOS os motivos de recusa registrados — o aceite exige
 * "motivo registrado para cada unidade avaliada e recusada". Nada e
 * recusado em silencio e nenhuma inclusao passa com um unico motivo
 * escondido atras de outro.
 *
 * <p>Reuso, sem duplicar regra:
 * <ul>
 *   <li>contestada/em revisao, repactuacao por superendividamento
 *       (CAD-75) e divida prescrita (RAS-15) passam pelo portao da P4.1
 *       ({@link CollectionGates#listingBlocks}); a prescricao e avaliada
 *       ITEM A ITEM pela tabela da P4.1 ({@link Prescription}) — o
 *       reconhecimento de um debito nunca reinicia o relogio de outro
 *       (licao D1 da revisao da ED-31);</li>
 *   <li>titular falecido vem da situacao registrada na P1.41 (CAD-65),
 *       pela porta {@code HolderSituationPort};</li>
 *   <li>aviso previo: sem carta com ENTREGA REGISTRADA nao ha inclusao;
 *       com entrega, o intervalo entre a entrega e a inclusao tem de ser
 *       ao menos {@code neg.notice_min_days}.</li>
 * </ul>
 */
public final class ListingEligibility {

    public enum RefusalReason {
        EXCLUDED_PROFILE,
        BILL_CONTESTED_OR_UNDER_REVIEW,
        HOLDER_DECEASED,
        REPACTUATION_ACTIVE,
        DEBT_PRESCRIBED,
        PRESCRIPTION_UNDETERMINED,
        PRIOR_NOTICE_WITHOUT_DELIVERY_PROOF,
        PRIOR_NOTICE_INTERVAL_NOT_MET
    }

    /** Um debito da unidade: vencimento e ultimo reconhecimento (relógio da prescrição, por item). */
    public record DebtItem(UUID billId, LocalDate dueDate, LocalDate lastAcknowledgment) {
        public DebtItem {
            if (billId == null || dueDate == null)
                throw new IllegalArgumentException("item de debito exige conta e vencimento");
        }
    }

    /** Fatos da avaliacao de UMA unidade. Sem nenhum campo de escore (regra da P4.1/GES-24). */
    public record UnitFacts(
            UUID unitId, UUID customerId, String profileCode,
            boolean billContestedOrUnderReview,
            boolean repactuationActive, Set<Repactuation.Effect> repactuationEffects,
            List<DebtItem> debts) {
        public UnitFacts {
            if (unitId == null || customerId == null)
                throw new IllegalArgumentException("avaliacao exige unidade e titular");
            repactuationEffects = repactuationEffects == null ? Set.of() : Set.copyOf(repactuationEffects);
            debts = debts == null ? List.of() : List.copyOf(debts);
            if (debts.isEmpty()) throw new IllegalArgumentException("unidade sem debitos nao e avaliada para negativacao");
        }
    }

    /** Decisao registrada: elegivel ou a lista completa de motivos de recusa. */
    public record Decision(UUID unitId, boolean eligible, Set<RefusalReason> reasons, String detail) {
        public Decision { reasons = reasons == null ? Set.of() : Set.copyOf(reasons); }
    }

    private final NegativationParameters params;
    private final Prescription.Table prescriptionTable;
    private final NegativationPorts.HolderSituationPort holderPort;
    private final NegativationPorts.PriorNoticePort noticePort;

    public ListingEligibility(NegativationParameters params, Prescription.Table prescriptionTable,
                              NegativationPorts.HolderSituationPort holderPort,
                              NegativationPorts.PriorNoticePort noticePort) {
        this.params = Objects.requireNonNull(params, "parametros neg.* sao obrigatorios");
        this.prescriptionTable = Objects.requireNonNull(prescriptionTable, "tabela de prescricao (P4.1) e obrigatoria");
        this.holderPort = Objects.requireNonNull(holderPort, "porta da situacao do titular (P1.41) e obrigatoria");
        this.noticePort = Objects.requireNonNull(noticePort, "porta do aviso previo e obrigatoria");
    }

    /**
     * Avalia a unidade na data dada. {@code nature}/{@code claim} sao a
     * natureza do prestador e a pretensao da tabela de prescricao (P4.1);
     * {@code legalAnalysisRecorded} e a analise juridica registrada para
     * as linhas que a exigem (Decreto 20.910/1932) — entrada explicita,
     * nunca presumida.
     */
    public Decision evaluate(UUID tenantId, UnitFacts facts, CollectionGates.ListingPurpose purpose,
                             Prescription.ProviderNature nature, Prescription.ClaimType claim,
                             boolean legalAnalysisRecorded, LocalDate evaluationDate) {
        Objects.requireNonNull(facts, "fatos da unidade sao obrigatorios");
        Objects.requireNonNull(evaluationDate, "data de avaliacao e obrigatoria");
        EnumSet<RefusalReason> reasons = EnumSet.noneOf(RefusalReason.class);
        StringBuilder detail = new StringBuilder();

        // Perfis excluidos (neg.excluded_profiles)
        if (facts.profileCode() != null && params.excludedProfiles().contains(facts.profileCode())) {
            reasons.add(RefusalReason.EXCLUDED_PROFILE);
            detail.append("perfil excluido: ").append(facts.profileCode()).append("; ");
        }

        // Titular falecido — situacao registrada na P1.41 (CAD-65), pela porta
        var situation = holderPort.holderSituation(tenantId, facts.unitId(), facts.customerId());
        if (situation.deceased()) {
            reasons.add(RefusalReason.HOLDER_DECEASED);
            detail.append("titular falecido (P1.41: ").append(situation.situationCode()).append("); ");
        }

        // Prescricao ITEM A ITEM (P4.1/RAS-15): o pior desfecho manda
        Prescription.Outcome worst = Prescription.Outcome.NOT_PRESCRIBED;
        for (DebtItem item : facts.debts()) {
            var eval = Prescription.evaluate(prescriptionTable, nature, claim, item.dueDate(),
                    item.lastAcknowledgment(), legalAnalysisRecorded, evaluationDate);
            if (eval.outcome() == Prescription.Outcome.PRESCRIBED) {
                worst = Prescription.Outcome.PRESCRIBED;
                detail.append("item ").append(item.billId()).append(" prescrito (termo ")
                        .append(eval.deadline()).append("); ");
            } else if (eval.outcome() == Prescription.Outcome.UNDETERMINED
                    && worst == Prescription.Outcome.NOT_PRESCRIBED) {
                worst = Prescription.Outcome.UNDETERMINED;
                detail.append("item ").append(item.billId()).append(" com prescricao indeterminada; ");
            }
        }

        // Portao da P4.1: contestada/revisao, repactuacao (CAD-75), prescrita (RAS-15)
        var gateFacts = new CollectionGates.ListingFacts(facts.billContestedOrUnderReview(),
                facts.repactuationActive(), facts.repactuationEffects(), worst);
        for (CollectionGates.BlockReason block : CollectionGates.listingBlocks(purpose, gateFacts)) {
            switch (block) {
                case BILL_UNDER_REVIEW_OR_CONTESTED -> reasons.add(RefusalReason.BILL_CONTESTED_OR_UNDER_REVIEW);
                case REPACTUATION_ACTIVE -> reasons.add(RefusalReason.REPACTUATION_ACTIVE);
                case DEBT_PRESCRIBED -> reasons.add(RefusalReason.DEBT_PRESCRIBED);
                case PRESCRIPTION_UNDETERMINED -> reasons.add(RefusalReason.PRESCRIPTION_UNDETERMINED);
                case ACTIVE_INSTALLMENT_AGREEMENT -> { /* nao se aplica a ListingFacts da P4.1 */ }
            }
        }

        // Aviso previo com entrega registrada + intervalo minimo
        var proof = noticePort.deliveryProof(tenantId, facts.unitId(), params.priorNoticeBy());
        if (proof.isEmpty()) {
            reasons.add(RefusalReason.PRIOR_NOTICE_WITHOUT_DELIVERY_PROOF);
            detail.append("sem carta de aviso previo com entrega registrada (")
                    .append(params.priorNoticeBy()).append("); ");
        } else {
            LocalDate earliest = proof.get().deliveredOn().plusDays(params.noticeMinDays());
            if (evaluationDate.isBefore(earliest)) {
                reasons.add(RefusalReason.PRIOR_NOTICE_INTERVAL_NOT_MET);
                detail.append("intervalo minimo ").append(params.noticeMinDays())
                        .append("d nao cumprido: entrega ").append(proof.get().deliveredOn())
                        .append(", inclusao so a partir de ").append(earliest).append("; ");
            }
        }

        return new Decision(facts.unitId(), reasons.isEmpty(), reasons, detail.toString().trim());
    }
}
