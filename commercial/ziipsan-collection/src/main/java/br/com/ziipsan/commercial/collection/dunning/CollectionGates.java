package br.com.ziipsan.commercial.collection.dunning;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation;

import java.util.EnumSet;
import java.util.Set;

/**
 * Portoes de protecao da cobranca (P4.1): pre-corte (consumido com a
 * P3.4), negativacao, protesto e ajuizamento (consumidos pela ED-30 e
 * pela ED-31). Sao funcoes puras sobre FATOS — e, por desenho verificado
 * no teste de arquitetura da GES-24, NENHUM deles recebe escore de
 * propensao: corte, negativacao e protesto nunca leem o escore
 * (Lei 14.015/2020; Lei 14.898/2024; LGPD, art. 20).
 *
 * <p>FRONTEIRA EXPLICITA da tarifa social (correcao D2): a concessao/
 * manutencao da tarifa social NAO vive neste modulo — nao ha nenhuma
 * classe de tarifa social aqui (a tarefa dona e a P1.5, Lei 14.898/2024).
 * O que este pacote prova e apenas que OS SEUS portoes nao leem escore;
 * a protecao da tarifa social tem de ser provada onde ela vive (P1.5),
 * nao e afirmada por este pacote.
 */
public final class CollectionGates {

    private CollectionGates() {
    }

    public enum BlockReason {
        /** Conta em revisao ou contestada (contestacao aberta bloqueia o corte — P3.4). */
        BILL_UNDER_REVIEW_OR_CONTESTED,
        /** Repactuacao vigente com o efeito correspondente ligado no parametro. */
        REPACTUATION_ACTIVE,
        /** Divida prescrita pela tabela versionada (RAS-15). */
        DEBT_PRESCRIBED,
        /** Prescricao indeterminada (linha sem prazo/analise): cautela bloqueia. */
        PRESCRIPTION_UNDETERMINED,
        /** Parcelamento ativo e adimplente sobre as contas motivadoras. */
        ACTIVE_INSTALLMENT_AGREEMENT
    }

    public enum ListingPurpose {
        NEGATIVATION,
        PROTEST,
        LAWSUIT
    }

    /**
     * Fatos do pre-corte. Sem nenhum campo de escore — ver o teste de
     * arquitetura {@code ScoringIsolationArchitectureTest}.
     */
    public record PreCutFacts(
            boolean billUnderReviewOrContested,
            boolean repactuationActive,
            Set<Repactuation.Effect> repactuationEffects,
            Prescription.Outcome prescriptionOutcome,
            boolean activeInstallmentAgreementInGoodStanding) {
        public PreCutFacts {
            repactuationEffects = repactuationEffects == null ? Set.of() : Set.copyOf(repactuationEffects);
            if (prescriptionOutcome == null) {
                throw new IllegalArgumentException("desfecho da prescricao e obrigatorio no pre-corte");
            }
        }
    }

    /**
     * Motivos de bloqueio do corte; vazio = o portao da P4.1 nao bloqueia.
     * O desfecho da prescricao e LIDO aqui (correcao D4 da revisao
     * cruzada — era obrigatorio e ignorado): divida PRESCRITA nao se
     * cobra por corte, e prescricao INDETERMINADA bloqueia por cautela,
     * o mesmo desenho fail-closed de {@link #listingBlocks}.
     */
    public static Set<BlockReason> preCutBlocks(PreCutFacts facts) {
        EnumSet<BlockReason> blocks = EnumSet.noneOf(BlockReason.class);
        if (facts.billUnderReviewOrContested()) {
            blocks.add(BlockReason.BILL_UNDER_REVIEW_OR_CONTESTED);
        }
        if (facts.repactuationActive()
                && facts.repactuationEffects().contains(Repactuation.Effect.SUSPEND_CUTOFF)) {
            blocks.add(BlockReason.REPACTUATION_ACTIVE);
        }
        if (facts.prescriptionOutcome() == Prescription.Outcome.PRESCRIBED) {
            blocks.add(BlockReason.DEBT_PRESCRIBED);
        }
        if (facts.prescriptionOutcome() == Prescription.Outcome.UNDETERMINED) {
            blocks.add(BlockReason.PRESCRIPTION_UNDETERMINED);
        }
        if (facts.activeInstallmentAgreementInGoodStanding()) {
            blocks.add(BlockReason.ACTIVE_INSTALLMENT_AGREEMENT);
        }
        return Set.copyOf(blocks);
    }

    /** Fatos de negativacao/protesto/ajuizamento (ED-30/ED-31). Sem escore. */
    public record ListingFacts(
            boolean billUnderReviewOrContested,
            boolean repactuationActive,
            Set<Repactuation.Effect> repactuationEffects,
            Prescription.Outcome prescriptionOutcome) {
        public ListingFacts {
            repactuationEffects = repactuationEffects == null ? Set.of() : Set.copyOf(repactuationEffects);
            if (prescriptionOutcome == null) {
                throw new IllegalArgumentException("desfecho da prescricao e obrigatorio");
            }
        }
    }

    /**
     * Bloqueios de negativacao, protesto e ajuizamento. Divida PRESCRITA
     * bloqueia os tres (e a consulta a tabela de prescricao e obrigatoria
     * justamente para isso); prescricao INDETERMINADA tambem bloqueia —
     * nao se negativa nem se ajuiza sobre prazo nao confirmado.
     */
    public static Set<BlockReason> listingBlocks(ListingPurpose purpose, ListingFacts facts) {
        if (purpose == null || facts == null) {
            throw new IllegalArgumentException("finalidade e fatos sao obrigatorios");
        }
        EnumSet<BlockReason> blocks = EnumSet.noneOf(BlockReason.class);
        if (facts.billUnderReviewOrContested()) {
            blocks.add(BlockReason.BILL_UNDER_REVIEW_OR_CONTESTED);
        }
        if (facts.prescriptionOutcome() == Prescription.Outcome.PRESCRIBED) {
            blocks.add(BlockReason.DEBT_PRESCRIBED);
        }
        if (facts.prescriptionOutcome() == Prescription.Outcome.UNDETERMINED) {
            blocks.add(BlockReason.PRESCRIPTION_UNDETERMINED);
        }
        if (facts.repactuationActive()) {
            boolean suspends = switch (purpose) {
                case NEGATIVATION -> facts.repactuationEffects().contains(Repactuation.Effect.SUSPEND_NEGATIVATION);
                case PROTEST, LAWSUIT -> facts.repactuationEffects().contains(Repactuation.Effect.SUSPEND_ACTIONS);
            };
            if (suspends) {
                blocks.add(BlockReason.REPACTUATION_ACTIVE);
            }
        }
        return Set.copyOf(blocks);
    }
}
