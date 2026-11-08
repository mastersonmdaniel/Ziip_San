package br.com.ziipsan.core.certification.infosec27001;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da ED-73: SoA com os 93 controles do Anexo A por referência —
 * aplicabilidade, justificativa, estado, evidência, responsável e última
 * revisão — exportável, sem impedir exclusão justificada, e SEM reproduzir
 * identificadores/títulos literais (decisão do dono pendente): as chaves
 * dos testes são sintéticas (SYN-CTL-nnn) e os títulos nascem [A DEFINIR].
 */
class StatementOfApplicabilityTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final Iso27001Activation ON = Iso27001Activation.enabled("escopo SYN",
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            UUID.fromString("00000000-0000-0000-0000-000000000102"));

    private static AnnexAControlEntry entry(int n, AnnexAControlEntry.Applicability app,
                                            AnnexAControlEntry.ImplementationStatus status) {
        List<AnnexAControlEntry.EvidenceRef> ev = status == AnnexAControlEntry.ImplementationStatus.IMPLEMENTED
                ? List.of(new AnnexAControlEntry.EvidenceRef(
                        AnnexAControlEntry.EvidenceKind.DOCUMENT, "SYN-DOC-" + n))
                : List.of();
        return AnnexAControlEntry.declare(ON, "SYN.CTL." + n, null, app,
                "justificativa sintética " + n, status, ev, OWNER, LocalDate.of(2026, 1, 10));
    }

    private static List<AnnexAControlEntry> ninetyThree() {
        List<AnnexAControlEntry> list = new ArrayList<>();
        for (int i = 1; i <= 93; i++) {
            list.add(entry(i, AnnexAControlEntry.Applicability.APPLICABLE,
                    AnnexAControlEntry.ImplementationStatus.IN_PROGRESS));
        }
        return list;
    }

    @Test
    @DisplayName("SoA completa tem exatamente 93 controles e exporta em JSON")
    void completeSoaExports() {
        StatementOfApplicability soa = StatementOfApplicability.build(ON, ninetyThree());
        assertThat(soa.entries()).hasSize(93);
        String json = soa.exportJson();
        assertThat(json).contains("\"expectedControls\":93").contains("SYN.CTL.93")
                .contains(StatementOfApplicability.SELF_ASSESSMENT_NOTICE)
                .contains(StatementOfApplicability.TITLES_PENDING_NOTE);
    }

    @Test
    @DisplayName("títulos nascem [A DEFINIR] e a pendência é contada, não disfarçada")
    void titlesPendingCounted() {
        StatementOfApplicability soa = StatementOfApplicability.build(ON, ninetyThree());
        assertThat(soa.titlesPending()).isEqualTo(93);
        assertThat(soa.entries().get(0).title()).isEqualTo(AnnexAControlEntry.TITLE_PENDING);
        assertThat(soa.entries().get(0).titlePending()).isTrue();
    }

    @Test
    @DisplayName("SoA com 92 ou 94 controles é recusada")
    void wrongCountRefused() {
        List<AnnexAControlEntry> ninetyTwo = ninetyThree();
        ninetyTwo.remove(0);
        assertThatThrownBy(() -> StatementOfApplicability.build(ON, ninetyTwo))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("93");
        List<AnnexAControlEntry> ninetyFour = ninetyThree();
        ninetyFour.add(entry(94, AnnexAControlEntry.Applicability.APPLICABLE,
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED));
        assertThatThrownBy(() -> StatementOfApplicability.build(ON, ninetyFour))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("chave duplicada na SoA é recusada")
    void duplicateKeyRefused() {
        List<AnnexAControlEntry> list = ninetyThree();
        list.set(92, entry(1, AnnexAControlEntry.Applicability.APPLICABLE,
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED));
        assertThatThrownBy(() -> StatementOfApplicability.build(ON, list))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicada");
    }

    @Test
    @DisplayName("exclusão justificada é permitida e contada; aplicável não implementado também")
    void justifiedExclusionAllowed() {
        List<AnnexAControlEntry> list = ninetyThree();
        list.set(0, entry(1, AnnexAControlEntry.Applicability.NOT_APPLICABLE,
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED));
        StatementOfApplicability soa = StatementOfApplicability.build(ON, list);
        assertThat(soa.justifiedExclusions()).isEqualTo(1);
        assertThat(soa.applicableNotImplemented()).isEqualTo(92);
        assertThat(soa.entries().get(0).justifiedExclusion()).isTrue();
    }

    @Test
    @DisplayName("linha sem justificativa é recusada — inclusive a exclusão")
    void justificationAlwaysRequired() {
        assertThatThrownBy(() -> AnnexAControlEntry.declare(ON, "SYN.CTL.1", null,
                AnnexAControlEntry.Applicability.NOT_APPLICABLE, " ",
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED, List.of(), OWNER,
                LocalDate.of(2026, 1, 10))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("aplicável IMPLEMENTED sem evidência é recusado")
    void implementedNeedsEvidence() {
        assertThatThrownBy(() -> AnnexAControlEntry.declare(ON, "SYN.CTL.1", null,
                AnnexAControlEntry.Applicability.APPLICABLE, "justificativa",
                AnnexAControlEntry.ImplementationStatus.IMPLEMENTED, List.of(), OWNER,
                LocalDate.of(2026, 1, 10))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("correção D1: construtor canônico da linha exige ativação ligada — sem fábrica, sem norma, sem linha")
    void canonicalEntryRequiresEnabledActivation() {
        assertThatThrownBy(() -> new AnnexAControlEntry(Iso27001Activation.disabled(), "SYN.CTL.1",
                null, AnnexAControlEntry.Applicability.APPLICABLE, "justificativa",
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED, List.of(), OWNER,
                LocalDate.of(2026, 1, 10))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AnnexAControlEntry(null, "SYN.CTL.1", null,
                AnnexAControlEntry.Applicability.APPLICABLE, "justificativa",
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED, List.of(), OWNER,
                LocalDate.of(2026, 1, 10))).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("correção D1: construtor canônico da SoA exige ativação ligada")
    void canonicalSoaRequiresEnabledActivation() {
        List<AnnexAControlEntry> entries = ninetyThree();
        assertThatThrownBy(() -> new StatementOfApplicability(Iso27001Activation.disabled(), entries))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("correção D5: exclusão (NOT_APPLICABLE) com estado IMPLEMENTED é recusada, mesmo com evidência")
    void excludedImplementedRefused() {
        assertThatThrownBy(() -> AnnexAControlEntry.declare(ON, "SYN.CTL.1", null,
                AnnexAControlEntry.Applicability.NOT_APPLICABLE, "justificativa da exclusão",
                AnnexAControlEntry.ImplementationStatus.IMPLEMENTED,
                List.of(new AnnexAControlEntry.EvidenceRef(
                        AnnexAControlEntry.EvidenceKind.DOCUMENT, "SYN-DOC-1")),
                OWNER, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exclusão justificada não é implementação");
    }

    @Test
    @DisplayName("linha da SoA não nasce com a norma desligada")
    void disabledBlocksEntry() {
        assertThatThrownBy(() -> AnnexAControlEntry.declare(Iso27001Activation.disabled(),
                "SYN.CTL.1", null, AnnexAControlEntry.Applicability.APPLICABLE, "justificativa",
                AnnexAControlEntry.ImplementationStatus.NOT_STARTED, List.of(), OWNER,
                LocalDate.of(2026, 1, 10))).isInstanceOf(IllegalStateException.class);
    }
}
