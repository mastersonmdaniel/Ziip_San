package br.com.ziipsan.core.certification.quality9001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-69: checklist de prontidão por edição (2026 alvo, 2015 só
 * em transição) e comparação de cláusulas na troca de edição.
 * Itens 100% sintéticos (SYN-).
 */
class ReadinessChecklistTest {

    private static ReadinessChecklist.ChecklistItem item(String clause, String prompt,
                                                         ReadinessChecklist.ItemStatus status) {
        return new ReadinessChecklist.ChecklistItem(clause, prompt, status, "");
    }

    private static ReadinessChecklist.ChecklistItem na(String clause, String prompt,
                                                       String justification) {
        return new ReadinessChecklist.ChecklistItem(clause, prompt,
                ReadinessChecklist.ItemStatus.NOT_APPLICABLE, justification);
    }

    @Test
    @DisplayName("checklist da edição da ativação mede prontidão: 2 prontos de 3 aplicáveis = 67%")
    void readinessPercentCountsApplicableOnly() {
        Iso9001Activation on = Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, "escopo", UUID.randomUUID(),
                UUID.randomUUID());
        ReadinessChecklist list = ReadinessChecklist.build(on, List.of(
                item("SYN.1", "pergunta sintética 1", ReadinessChecklist.ItemStatus.READY),
                item("SYN.2", "pergunta sintética 2", ReadinessChecklist.ItemStatus.READY),
                item("SYN.3", "pergunta sintética 3", ReadinessChecklist.ItemStatus.PENDING),
                na("SYN.4", "pergunta sintética 4", "não se aplica ao escopo sintético")));
        assertThat(list.readinessPercent()).isEqualTo(67);
        assertThat(list.pendingClauses()).containsExactly("SYN.3");
        assertThat(list.ready()).isFalse();
    }

    @Test
    @DisplayName("checklist todo pronto (aplicáveis) está pronto")
    void allReadyIsReady() {
        Iso9001Activation on = Iso9001Activation.enabled("2015",
                Iso9001Activation.EditionUse.TRANSITION, "escopo", UUID.randomUUID(),
                UUID.randomUUID());
        ReadinessChecklist list = ReadinessChecklist.build(on, List.of(
                item("SYN.1", "pergunta", ReadinessChecklist.ItemStatus.READY)));
        assertThat(list.ready()).isTrue();
        assertThat(list.readinessPercent()).isEqualTo(100);
        assertThat(list.edition()).isEqualTo("2015");
    }

    @Test
    @DisplayName("item não aplicável sem justificativa é recusado")
    void notApplicableItemNeedsJustification() {
        assertThatThrownBy(() -> new ReadinessChecklist.ChecklistItem("SYN.1", "pergunta",
                ReadinessChecklist.ItemStatus.NOT_APPLICABLE, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cláusula duplicada no checklist é recusada")
    void duplicatedClauseRefused() {
        Iso9001Activation on = Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, "escopo", UUID.randomUUID(),
                UUID.randomUUID());
        assertThatThrownBy(() -> ReadinessChecklist.build(on, List.of(
                item("SYN.1", "pergunta a", ReadinessChecklist.ItemStatus.READY),
                item("SYN.1", "pergunta b", ReadinessChecklist.ItemStatus.PENDING))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("comparação na troca de edição separa incluídos, removidos, alterados e inalterados")
    void editionComparisonSeparatesChanges() {
        Iso9001Activation a2015 = Iso9001Activation.enabled("2015",
                Iso9001Activation.EditionUse.TRANSITION, "escopo", UUID.randomUUID(),
                UUID.randomUUID());
        Iso9001Activation a2026 = Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, "escopo", UUID.randomUUID(),
                UUID.randomUUID());
        ReadinessChecklist before = ReadinessChecklist.build(a2015, List.of(
                item("SYN.1", "pergunta igual", ReadinessChecklist.ItemStatus.READY),
                item("SYN.2", "pergunta antiga", ReadinessChecklist.ItemStatus.READY),
                item("SYN.3", "pergunta removida", ReadinessChecklist.ItemStatus.PENDING)));
        ReadinessChecklist after = ReadinessChecklist.build(a2026, List.of(
                item("SYN.1", "pergunta igual", ReadinessChecklist.ItemStatus.READY),
                item("SYN.2", "pergunta nova redação", ReadinessChecklist.ItemStatus.PENDING),
                item("SYN.4", "pergunta incluída", ReadinessChecklist.ItemStatus.PENDING)));
        ReadinessChecklist.EditionDiff diff = ReadinessChecklist.compareEditions(before, after);
        assertThat(diff.fromEdition()).isEqualTo("2015");
        assertThat(diff.toEdition()).isEqualTo("2026");
        assertThat(diff.added()).extracting(ReadinessChecklist.ChecklistItem::clauseNumber)
                .containsExactly("SYN.4");
        assertThat(diff.removed()).extracting(ReadinessChecklist.ChecklistItem::clauseNumber)
                .containsExactly("SYN.3");
        assertThat(diff.changed()).extracting(ReadinessChecklist.ChecklistItem::clauseNumber)
                .containsExactly("SYN.2");
        assertThat(diff.unchanged()).extracting(ReadinessChecklist.ChecklistItem::clauseNumber)
                .containsExactly("SYN.1");
        assertThat(diff.hasChanges()).isTrue();
    }

    @Test
    @DisplayName("comparar checklists da mesma edição é recusado: sem troca, não há comparação")
    void sameEditionComparisonRefused() {
        Iso9001Activation on = Iso9001Activation.enabled("2026",
                Iso9001Activation.EditionUse.TARGET, "escopo", UUID.randomUUID(),
                UUID.randomUUID());
        ReadinessChecklist list = ReadinessChecklist.build(on, List.of(
                item("SYN.1", "pergunta", ReadinessChecklist.ItemStatus.READY)));
        assertThatThrownBy(() -> ReadinessChecklist.compareEditions(list, list))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("com a norma desligada, checklist não nasce")
    void disabledBlocksChecklist() {
        assertThatThrownBy(() -> ReadinessChecklist.build(Iso9001Activation.disabled(),
                List.of(item("SYN.1", "pergunta", ReadinessChecklist.ItemStatus.READY))))
                .isInstanceOf(IllegalStateException.class);
    }
}
