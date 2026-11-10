package br.com.ziipsan.commercial.billing.social.cycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static br.com.ziipsan.commercial.billing.social.cycle.SocialCycleFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * II-3/E2 - importacao por porta com formato declarado: aceites exactos,
 * deduplicacao e recusas por linha (aceite SS4.1 da estimativa na parte
 * da importacao).
 */
class DeclaredCsvSocialListImporterTest {

    private final DeclaredCsvSocialListImporter importer = new DeclaredCsvSocialListImporter();
    private final DeclaredSocialListFormat format = DeclaredSocialListFormat.proofFormat();

    @Test
    @DisplayName("aceites exactos e duplicada removida (mesma familia, pessoa e papel)")
    void acceptsExactlyAndDeduplicates() {
        String content = csv(
                row(F1, P1, "HOLDER", "CADUNICO", "2025-06-01", "2026-01-01", "", "MUN-A"),
                row(F1, P1, "HOLDER", "CADUNICO", "2025-06-01", "2026-02-01", "", "MUN-A"),
                row(F2, P9, "HOLDER", "BPC", "", "2026-01-01", "2027-12-31", "MUN-A"));
        SocialListImportPort.ImportOutcome outcome = importer.importBatch(content, format);
        assertThat(outcome.rejections()).isEmpty();
        assertThat(outcome.accepted()).extracting(SocialListImportPort.ImportedBeneficiaryRow::dedupKey)
                .containsExactly(F1 + "/" + P1 + "/HOLDER", F2 + "/" + P9 + "/HOLDER");
        assertThat(outcome.duplicatesRemoved()).hasSize(1);
        assertThat(outcome.duplicatesRemoved().get(0).benefitValidFrom())
                .isEqualTo(java.time.LocalDate.of(2026, 2, 1));
    }

    @Test
    @DisplayName("mesma pessoa em papel diferente nao e duplicada")
    void samePersonDifferentRoleIsNotDuplicate() {
        String content = csv(
                row(F1, P1, "HOLDER", "BPC", "", "2026-01-01", "", ""),
                row(F1, P1, "MEMBER", "BPC", "", "2026-01-01", "", ""));
        SocialListImportPort.ImportOutcome outcome = importer.importBatch(content, format);
        assertThat(outcome.accepted()).hasSize(2);
        assertThat(outcome.duplicatesRemoved()).isEmpty();
    }

    @Test
    @DisplayName("recusas por linha com linha e codigo exactos")
    void rejectionsCarryExactLineAndCode() {
        String content = csv(
                row("nao-e-hash", P1, "HOLDER", "BPC", "", "2026-01-01", "", ""),
                row(F1, P1, "HOLDER", "CADUNICO", "", "2026-01-01", "", ""),
                row(F2, P9, "HOLDER", "BPC", "2025-01-01", "2026-01-01", "", ""),
                row(F3, P5, "TIO", "BPC", "", "2026-01-01", "", ""),
                row(F4, P7, "HOLDER", "BPC", "", "2026-06-01", "2026-01-01", ""));
        SocialListImportPort.ImportOutcome outcome = importer.importBatch(content, format);
        assertThat(outcome.accepted()).isEmpty();
        assertThat(outcome.rejections()).extracting(SocialListImportPort.ImportRejection::lineNumber)
                .containsExactly(2, 3, 4, 5, 6);
        assertThat(outcome.rejections()).extracting(SocialListImportPort.ImportRejection::code)
                .containsExactly("INVALID_HASH", "REQUIRED", "INVALID_DOMAIN", "INVALID_DOMAIN", "INVALID_RANGE");
    }

    @Test
    @DisplayName("cabecalho divergente do declarado recusa o lote inteiro sem aceites")
    void wrongHeaderRejectsWholeBatch() {
        SocialListImportPort.ImportOutcome outcome =
                importer.importBatch("a;b;c\n" + row(F1, P1, "HOLDER", "BPC", "", "2026-01-01", "", ""), format);
        assertThat(outcome.accepted()).isEmpty();
        assertThat(outcome.rejections()).singleElement()
                .extracting(SocialListImportPort.ImportRejection::code).isEqualTo("INVALID_HEADER");
    }

    @Test
    @DisplayName("linha boa no mesmo ficheiro de linhas recusadas segue aceite")
    void goodRowSurvivesBadNeighbours() {
        String content = csv(
                row(F1, P1, "HOLDER", "CADUNICO", "", "2026-01-01", "", ""),
                row(F2, P9, "HOLDER", "BPC", "", "2026-01-01", "", "MUN-A"));
        SocialListImportPort.ImportOutcome outcome = importer.importBatch(content, format);
        assertThat(outcome.accepted()).extracting(SocialListImportPort.ImportedBeneficiaryRow::familyCodeHash)
                .containsExactly(F2);
        assertThat(outcome.rejections()).hasSize(1);
    }

    @Test
    @DisplayName("formato declarado de prova nao e o leiaute oficial: id e colunas exactos")
    void proofFormatIsTheDeclaredOne() {
        assertThat(format.formatId()).isEqualTo("II3-LISTA-SOCIAL-DECLARADA");
        assertThat(format.columns()).containsExactly("family_code_hash", "person_document_hash", "person_role",
                "eligibility_basis", "cadunico_updated_on", "benefit_valid_from", "benefit_valid_to",
                "municipality_code");
        assertThat(format.columns()).doesNotContain("cpf", "document", "name", "income");
    }
}
