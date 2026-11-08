package br.com.ziipsan.compliance.lgpd.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste do aceite: coluna classificada como dado pessoal SEM operação de tratamento
 * associada aparece como pendência. Sobras ordenadas por tabela.coluna.
 */
class ProcessingCoverageTest {

    private static final List<ProcessingCoverage.ClassifiedColumn> CLASSIFIED = List.of(
            new ProcessingCoverage.ClassifiedColumn("customers", "name", "ID"),
            new ProcessingCoverage.ClassifiedColumn("customers", "email", "CT"),
            new ProcessingCoverage.ClassifiedColumn("bills", "total", "FIN"));

    @Test
    @DisplayName("coluna classificada sem operação associada é pendência do encarregado")
    void classifiedColumnWithoutRecordIsGap() {
        List<ProcessingCoverage.ColumnLink> links = List.of(
                new ProcessingCoverage.ColumnLink("OP-CADASTRO", "customers", "name"));
        List<ProcessingCoverage.ClassifiedColumn> gaps =
                ProcessingCoverage.uncovered(CLASSIFIED, links);
        assertThat(gaps).extracting(ProcessingCoverage.ClassifiedColumn::key)
                .containsExactly("bills.total", "customers.email");
    }

    @Test
    @DisplayName("todas as colunas vinculadas: nenhuma pendência")
    void fullyCoveredHasNoGaps() {
        List<ProcessingCoverage.ColumnLink> links = List.of(
                new ProcessingCoverage.ColumnLink("OP-CADASTRO", "customers", "name"),
                new ProcessingCoverage.ColumnLink("OP-CADASTRO", "customers", "email"),
                new ProcessingCoverage.ColumnLink("OP-FATURAMENTO", "bills", "total"));
        assertThat(ProcessingCoverage.uncovered(CLASSIFIED, links)).isEmpty();
    }

    @Test
    @DisplayName("sem vínculo nenhum: tudo é pendência")
    void noLinksAllGap() {
        assertThat(ProcessingCoverage.uncovered(CLASSIFIED, List.of()))
                .extracting(ProcessingCoverage.ClassifiedColumn::key)
                .containsExactly("bills.total", "customers.email", "customers.name");
    }
}
