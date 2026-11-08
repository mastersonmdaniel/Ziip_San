package br.com.ziipsan.commercial.collection.dunning;

import br.com.ziipsan.commercial.collection.dunning.prescription.Prescription;
import br.com.ziipsan.commercial.collection.dunning.repactuation.Repactuation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Portoes de protecao: pre-corte (P3.4), negativacao, protesto e ajuizamento (ED-30/ED-31). */
class CollectionGatesTest {

    private static CollectionGates.PreCutFacts preCut(boolean contested, boolean repact,
                                                       Set<Repactuation.Effect> effects,
                                                       Prescription.Outcome outcome, boolean agreement) {
        return new CollectionGates.PreCutFacts(contested, repact, effects, outcome, agreement);
    }

    private static CollectionGates.ListingFacts listing(boolean contested, boolean repact,
                                                        Set<Repactuation.Effect> effects,
                                                        Prescription.Outcome outcome) {
        return new CollectionGates.ListingFacts(contested, repact, effects, outcome);
    }

    @Test
    @DisplayName("pre-corte: contestacao aberta bloqueia o corte (P3.4)")
    void contestedBlocksCut() {
        var blocks = CollectionGates.preCutBlocks(preCut(true, false, Set.of(),
                Prescription.Outcome.NOT_PRESCRIBED, false));
        assertThat(blocks).containsExactly(CollectionGates.BlockReason.BILL_UNDER_REVIEW_OR_CONTESTED);
    }

    @Test
    @DisplayName("pre-corte: repactuacao so bloqueia com SUSPEND_CUTOFF; acordo adimplente bloqueia; limpo nao bloqueia")
    void preCutRepactuationAndAgreement() {
        assertThat(CollectionGates.preCutBlocks(preCut(false, true,
                Set.of(Repactuation.Effect.SUSPEND_CUTOFF),
                Prescription.Outcome.NOT_PRESCRIBED, false)))
                .containsExactly(CollectionGates.BlockReason.REPACTUATION_ACTIVE);
        assertThat(CollectionGates.preCutBlocks(preCut(false, true,
                Set.of(Repactuation.Effect.SUSPEND_CHARGES),
                Prescription.Outcome.NOT_PRESCRIBED, false))).isEmpty();
        assertThat(CollectionGates.preCutBlocks(preCut(false, false, Set.of(),
                Prescription.Outcome.NOT_PRESCRIBED, true)))
                .containsExactly(CollectionGates.BlockReason.ACTIVE_INSTALLMENT_AGREEMENT);
        assertThat(CollectionGates.preCutBlocks(preCut(false, false, Set.of(),
                Prescription.Outcome.NOT_PRESCRIBED, false))).isEmpty();
    }

    @Test
    @DisplayName("negativacao, protesto e ajuizamento de divida PRESCRITA: bloqueados os tres")
    void prescribedBlocksAllPurposes() {
        var facts = listing(false, false, Set.of(), Prescription.Outcome.PRESCRIBED);
        for (CollectionGates.ListingPurpose purpose : CollectionGates.ListingPurpose.values()) {
            assertThat(CollectionGates.listingBlocks(purpose, facts))
                    .containsExactly(CollectionGates.BlockReason.DEBT_PRESCRIBED);
        }
    }

    @Test
    @DisplayName("prescricao indeterminada bloqueia por cautela; repactuacao separa negativacao de acoes")
    void undeterminedAndRepactuationPurposes() {
        assertThat(CollectionGates.listingBlocks(CollectionGates.ListingPurpose.NEGATIVATION,
                listing(false, false, Set.of(), Prescription.Outcome.UNDETERMINED)))
                .containsExactly(CollectionGates.BlockReason.PRESCRIPTION_UNDETERMINED);

        var negOnly = listing(false, true, Set.of(Repactuation.Effect.SUSPEND_NEGATIVATION),
                Prescription.Outcome.NOT_PRESCRIBED);
        assertThat(CollectionGates.listingBlocks(CollectionGates.ListingPurpose.NEGATIVATION, negOnly))
                .containsExactly(CollectionGates.BlockReason.REPACTUATION_ACTIVE);
        assertThat(CollectionGates.listingBlocks(CollectionGates.ListingPurpose.PROTEST, negOnly)).isEmpty();

        var actions = listing(false, true, Set.of(Repactuation.Effect.SUSPEND_ACTIONS),
                Prescription.Outcome.NOT_PRESCRIBED);
        assertThat(CollectionGates.listingBlocks(CollectionGates.ListingPurpose.PROTEST, actions))
                .containsExactly(CollectionGates.BlockReason.REPACTUATION_ACTIVE);
        assertThat(CollectionGates.listingBlocks(CollectionGates.ListingPurpose.LAWSUIT, actions))
                .containsExactly(CollectionGates.BlockReason.REPACTUATION_ACTIVE);
        assertThat(CollectionGates.listingBlocks(CollectionGates.ListingPurpose.NEGATIVATION, actions)).isEmpty();
    }

    @Test
    @DisplayName("D4: pre-corte LE o desfecho da prescricao — prescrita bloqueia, indeterminada bloqueia por cautela")
    void preCutReadsPrescriptionOutcome() {
        assertThat(CollectionGates.preCutBlocks(preCut(false, false, Set.of(),
                Prescription.Outcome.PRESCRIBED, false)))
                .containsExactly(CollectionGates.BlockReason.DEBT_PRESCRIBED);
        assertThat(CollectionGates.preCutBlocks(preCut(false, false, Set.of(),
                Prescription.Outcome.UNDETERMINED, false)))
                .containsExactly(CollectionGates.BlockReason.PRESCRIPTION_UNDETERMINED);
    }
}
