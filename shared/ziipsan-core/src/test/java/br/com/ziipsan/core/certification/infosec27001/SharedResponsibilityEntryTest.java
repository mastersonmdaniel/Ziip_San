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
 * Aceite da ED-73: matriz de responsabilidade compartilhada Ziip × cliente
 * por controle; marcação de implementado pelo fornecedor acompanhada da
 * evidência do fornecedor.
 */
class SharedResponsibilityEntryTest {

    private static final Iso27001Activation ON = Iso27001Activation.enabled("escopo SYN",
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            UUID.fromString("00000000-0000-0000-0000-000000000102"));

    @Test
    @DisplayName("implementado pelo fornecedor com evidência do fornecedor nasce")
    void supplierImplementedWithEvidence() {
        SharedResponsibilityEntry e = SharedResponsibilityEntry.declare(ON, "SYN.CTL.1",
                SharedResponsibilityEntry.Responsibility.SHARED, true,
                List.of("SYN-PACK-1:BASELINE"), "nota sintética");
        assertThat(e.implementedBySupplier()).isTrue();
        assertThat(e.supplierEvidenceRefs()).containsExactly("SYN-PACK-1:BASELINE");
    }

    @Test
    @DisplayName("implementado pelo fornecedor SEM evidência do fornecedor é recusado")
    void supplierImplementedWithoutEvidenceRefused() {
        assertThatThrownBy(() -> SharedResponsibilityEntry.declare(ON, "SYN.CTL.1",
                SharedResponsibilityEntry.Responsibility.SUPPLIER, true, List.of(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidência do fornecedor");
    }

    @Test
    @DisplayName("responsabilidade só do cliente não aceita marcação de fornecedor")
    void clientOnlyCannotBeSupplierImplemented() {
        assertThatThrownBy(() -> SharedResponsibilityEntry.declare(ON, "SYN.CTL.1",
                SharedResponsibilityEntry.Responsibility.CLIENT, true,
                List.of("SYN-PACK-1:BASELINE"), ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("linha do cliente sem marcação de fornecedor nasce sem evidência")
    void clientLineWithoutSupplierMark() {
        SharedResponsibilityEntry e = SharedResponsibilityEntry.declare(ON, "SYN.CTL.2",
                SharedResponsibilityEntry.Responsibility.CLIENT, false, List.of(), null);
        assertThat(e.note()).isEmpty();
    }

    @Test
    @DisplayName("correção D1: construtor canônico da matriz exige ativação ligada")
    void canonicalMatrixRequiresEnabledActivation() {
        assertThatThrownBy(() -> new SharedResponsibilityEntry(Iso27001Activation.disabled(),
                "SYN.CTL.1", SharedResponsibilityEntry.Responsibility.SHARED, false, List.of(), ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("correção D3: chave fora do padrão de referência da SoA é recusada")
    void keyOutsideReferencePatternRefused() {
        assertThatThrownBy(() -> SharedResponsibilityEntry.declare(ON, "chave com espacos e texto!",
                SharedResponsibilityEntry.Responsibility.SHARED, false, List.of(), ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("correção D3: referências de evidência são aparadas (trim) como nos demais records")
    void evidenceRefsAreTrimmed() {
        SharedResponsibilityEntry e = SharedResponsibilityEntry.declare(ON, "SYN.CTL.1",
                SharedResponsibilityEntry.Responsibility.SHARED, true,
                List.of("  SYN-PACK  "), "");
        assertThat(e.supplierEvidenceRefs()).containsExactly("SYN-PACK");
    }

    @Test
    @DisplayName("correção D3: fábrica com a SoA confere a existência da chave na declaração")
    void matrixKeyCheckedAgainstSoa() {
        List<AnnexAControlEntry> entries = new ArrayList<>();
        UUID owner = UUID.fromString("00000000-0000-0000-0000-000000000201");
        for (int i = 1; i <= 93; i++) {
            entries.add(AnnexAControlEntry.declare(ON, "SYN.CTL." + i, null,
                    AnnexAControlEntry.Applicability.APPLICABLE, "justificativa SYN " + i,
                    AnnexAControlEntry.ImplementationStatus.IN_PROGRESS, List.of(), owner,
                    LocalDate.of(2026, 1, 10)));
        }
        StatementOfApplicability soa = StatementOfApplicability.build(ON, entries);
        SharedResponsibilityEntry ok = SharedResponsibilityEntry.declare(ON, soa, "SYN.CTL.7",
                SharedResponsibilityEntry.Responsibility.CLIENT, false, List.of(), "");
        assertThat(ok.controlKey()).isEqualTo("SYN.CTL.7");
        assertThatThrownBy(() -> SharedResponsibilityEntry.declare(ON, soa, "SYN.CTL.999",
                SharedResponsibilityEntry.Responsibility.CLIENT, false, List.of(), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora da Declaração de Aplicabilidade");
    }

    @Test
    @DisplayName("matriz não nasce com a norma desligada")
    void disabledBlocksMatrix() {
        assertThatThrownBy(() -> SharedResponsibilityEntry.declare(Iso27001Activation.disabled(),
                "SYN.CTL.1", SharedResponsibilityEntry.Responsibility.SHARED, false,
                List.of(), "")).isInstanceOf(IllegalStateException.class);
    }
}
