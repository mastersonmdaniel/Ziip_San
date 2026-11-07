package br.com.ziipsan.commercial.registry.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-57: o esquema de beneficiarios declarado ao importador generico da
 * P1.40 - modelo gerado do esquema, validacao por linha (dominio, tipo,
 * duplicidade no arquivo e no banco) e resolucao no registro de esquemas.
 * Sem banco de verdade: as checagens de banco sao o DbChecks falso, como
 * no ImportValidatorTest. Os hashes sao literais sinteticos de 64 hex.
 */
class SocialBeneficiaryImportSchemaTest {

    private static final SocialBeneficiaryImportSchema SCHEMA = new SocialBeneficiaryImportSchema();

    private static final String FAMILY =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String PERSON =
            "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
    private static final String PERSON2 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final String HEADER = "list_source,legal_basis,layout_version,received_on,"
            + "family_code_hash,person_document_hash,person_role,eligibility_basis,"
            + "cadunico_updated_on,benefit_valid_from,benefit_valid_to";

    private static String row(String source, String role, String basis, String cadunico) {
        return rowWithPerson(PERSON, source, role, basis, cadunico);
    }

    private static String rowWithPerson(String person, String source, String role, String basis,
                                        String cadunico) {
        return source + ",Lei 14.898/2024,v1,2026-01-15," + FAMILY + "," + person + "," + role + ","
                + basis + "," + cadunico + ",2026-01-01,";
    }

    private static ImportCsvParser.ParsedCsv csv(String... lines) {
        StringBuilder content = new StringBuilder(HEADER).append("\n");
        for (String line : lines) {
            content.append(line).append("\n");
        }
        return ImportCsvParser.parse(content.toString());
    }

    private static ImportValidator.DbChecks checks(boolean existsInDatabase) {
        return new ImportValidator.DbChecks() {
            @Override
            public boolean foreignKeyExists(String table, String column, String value) {
                return true;
            }

            @Override
            public boolean existsInDatabase(Map<String, String> values) {
                return existsInDatabase;
            }
        };
    }

    @Test
    @DisplayName("o esquema resolve no registro e o modelo sai do esquema, na ordem declarada")
    void schemaResolvesAndTemplateFollowsDeclaration() {
        ImportSchemaRegistry registry = new ImportSchemaRegistry(
                List.of(new CategoryImportSchema(), new SubcategoryImportSchema(), SCHEMA));
        assertThat(registry.require("social.beneficiaries").label())
                .isEqualTo("Beneficiarios de programas sociais");
        assertThat(SCHEMA.columns()).extracting(ImportColumn::name)
                .containsExactly("list_source", "legal_basis", "layout_version", "received_on",
                        "family_code_hash", "person_document_hash", "person_role", "eligibility_basis",
                        "cadunico_updated_on", "benefit_valid_from", "benefit_valid_to");
        String template = ImportTemplate.csv(SCHEMA);
        assertThat(template.lines().findFirst().orElseThrow()).isEqualTo(HEADER);
        // O exemplo do modelo e uma linha valida do proprio esquema.
        ImportValidator.ValidationResult example = ImportValidator.validate(SCHEMA,
                ImportCsvParser.parse(template), checks(false));
        assertThat(example.rejections()).isEmpty();
        assertThat(example.accepted()).hasSize(1);
    }

    @Test
    @DisplayName("a chave e a composicao (familia, pessoa, papel) do UNIQUE da V4_2; nenhum documento em claro")
    void keyIsTheV42CompositionAndNoPlainDocumentColumn() {
        assertThat(SCHEMA.columns().stream().filter(ImportColumn::uniqueKey).map(ImportColumn::name))
                .containsExactly("family_code_hash", "person_document_hash", "person_role");
        assertThat(SCHEMA.columns()).extracting(ImportColumn::name)
                .noneMatch(name -> name.contains("cpf") || name.contains("nis")
                        || name.equals("document") || name.contains("income"));
    }

    @Test
    @DisplayName("linha limpa de titular CadUnico e aceita; membro BPC sem data de CadUnico tambem")
    void cleanRowsAccepted() {
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv(row("REGULATOR_LIST", "HOLDER", "CADUNICO", "2025-06-01"),
                        row("MUNICIPAL_BASE", "MEMBER", "BPC", "")),
                checks(false));
        assertThat(result.rejections()).isEmpty();
        assertThat(result.accepted()).hasSize(2);
    }

    @Test
    @DisplayName("origem fora do dominio da V4_2 e base fora do dominio da P1.5 sao recusadas por campo")
    void domainRejections() {
        String badSource = "API_PUBLICA,Lei 14.898/2024,v1,2026-01-15," + FAMILY + "," + PERSON
                + ",HOLDER,CADUNICO,2025-06-01,2026-01-01,";
        String badBasis = row("REGULATOR_LIST", "HOLDER", "BOLSA_X", "2025-06-01");
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv(badSource, badBasis), checks(false));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections())
                .extracting(r -> r.field() + ":" + r.reasonCode())
                .containsExactlyInAnyOrder("list_source:INVALID_DOMAIN", "eligibility_basis:INVALID_DOMAIN");
    }

    @Test
    @DisplayName("sem base legal registrada a linha e recusada (REQUIRED em legal_basis)")
    void missingLegalBasisRejected() {
        String noBasis = "REGULATOR_LIST,,v1,2026-01-15," + FAMILY + "," + PERSON
                + ",HOLDER,CADUNICO,2025-06-01,2026-01-01,";
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv(noBasis), checks(false));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections()).extracting(ImportValidator.Rejection::field)
                .containsExactly("legal_basis");
        assertThat(result.rejections()).extracting(ImportValidator.Rejection::reasonCode)
                .containsExactly("REQUIRED");
    }

    @Test
    @DisplayName("a mesma pessoa no mesmo papel e duplicidade no arquivo; em outro papel, nao")
    void duplicateInFileByComposition() {
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv(row("REGULATOR_LIST", "HOLDER", "CADUNICO", "2025-06-01"),
                        row("REGULATOR_LIST", "HOLDER", "CADUNICO", "2025-06-01"),
                        row("REGULATOR_LIST", "MEMBER", "CADUNICO", "2025-06-01")),
                checks(false));
        assertThat(result.accepted()).extracting(r -> r.number()).containsExactly(1, 3);
        assertThat(result.rejections()).hasSize(1);
        assertThat(result.rejections().get(0).reasonCode()).isEqualTo("DUPLICATE_IN_FILE");
        assertThat(result.rejections().get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("registro ja na base do mesmo lote e duplicidade no banco; data fora de ISO e tipo invalido")
    void duplicateInDatabaseAndInvalidDate() {
        ImportValidator.ValidationResult inDb = ImportValidator.validate(SCHEMA,
                csv(row("CONECTA_API", "HOLDER", "CADUNICO", "2025-06-01")), checks(true));
        assertThat(inDb.accepted()).isEmpty();
        assertThat(inDb.rejections()).extracting(ImportValidator.Rejection::reasonCode)
                .containsExactly("DUPLICATE_IN_DATABASE");

        String badDate = "REGULATOR_LIST,Lei 14.898/2024,v1,15/01/2026," + FAMILY + "," + PERSON
                + ",HOLDER,CADUNICO,2025-06-01,2026-01-01,";
        ImportValidator.ValidationResult typed = ImportValidator.validate(SCHEMA,
                csv(badDate), checks(false));
        assertThat(typed.rejections())
                .extracting(r -> r.field() + ":" + r.reasonCode())
                .containsExactly("received_on:INVALID_TYPE");
    }

    // --- Combinacao base x data do CadUnico (CHECK da V4_2; achado A1 da revisao) --------
    // A validacao completa do esquema (validate) recusa a combinacao LINHA A LINHA,
    // nos dois sentidos: a linha nao chega ao aplicador e nao derruba o lote.

    @Test
    @DisplayName("A1: CadUnico sem data de atualizacao e recusado linha a linha; a linha boa do lote segue aceita")
    void cadunicoWithoutDateRejectedLineByLine() {
        ImportValidator.ValidationResult result = SCHEMA.validate(
                csv(row("REGULATOR_LIST", "HOLDER", "CADUNICO", ""),
                        row("MUNICIPAL_BASE", "MEMBER", "BPC", "")),
                checks(false));
        assertThat(result.accepted()).extracting(r -> r.number()).containsExactly(2);
        assertThat(result.rejections()).hasSize(1);
        assertThat(result.rejections().get(0).rowNumber()).isEqualTo(1);
        assertThat(result.rejections().get(0).field()).isEqualTo("cadunico_updated_on");
        assertThat(result.rejections().get(0).reasonCode()).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("A1: BPC com data de CadUnico e recusado (a data so existe para a base CADUNICO)")
    void bpcWithDateRejected() {
        ImportValidator.ValidationResult result = SCHEMA.validate(
                csv(row("REGULATOR_LIST", "HOLDER", "BPC", "2025-06-01")), checks(false));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections())
                .extracting(r -> r.field() + ":" + r.reasonCode())
                .containsExactly("cadunico_updated_on:INVALID_DOMAIN");
    }

    @Test
    @DisplayName("A1: programa local com data de CadUnico e recusado (a data so existe para a base CADUNICO)")
    void localProgramWithDateRejected() {
        ImportValidator.ValidationResult result = SCHEMA.validate(
                csv(row("MUNICIPAL_BASE", "HOLDER", "LOCAL_PROGRAM", "2025-06-01")), checks(false));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections())
                .extracting(r -> r.field() + ":" + r.reasonCode())
                .containsExactly("cadunico_updated_on:INVALID_DOMAIN");
    }

    @Test
    @DisplayName("A1: as combinacoes validas sao aceitas - CadUnico com data, BPC e programa local sem data")
    void basisDateValidCombinationsAccepted() {
        ImportValidator.ValidationResult result = SCHEMA.validate(
                csv(row("REGULATOR_LIST", "HOLDER", "CADUNICO", "2025-06-01"),
                        row("MUNICIPAL_BASE", "MEMBER", "BPC", ""),
                        rowWithPerson(PERSON2, "MUNICIPAL_BASE", "HOLDER", "LOCAL_PROGRAM", "")),
                checks(false));
        assertThat(result.rejections()).isEmpty();
        assertThat(result.accepted()).hasSize(3);
    }

    @Test
    @DisplayName("A1: a guarda do aplicador recusa a combinacao invalida antes de qualquer INSERT")
    void applierGuardRejectsInconsistentCombination() throws SQLException {
        assertThatThrownBy(() -> SocialBeneficiaryImportSchema.requireBasisDateConsistent(
                Map.of("eligibility_basis", "CADUNICO", "cadunico_updated_on", "")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("recusada na validacao");
        assertThatThrownBy(() -> SocialBeneficiaryImportSchema.requireBasisDateConsistent(
                Map.of("eligibility_basis", "BPC", "cadunico_updated_on", "2025-06-01")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("recusada na validacao");
        assertThatThrownBy(() -> SocialBeneficiaryImportSchema.requireBasisDateConsistent(
                Map.of("eligibility_basis", "LOCAL_PROGRAM", "cadunico_updated_on", "2025-06-01")))
                .isInstanceOf(SQLException.class);
        // As combinacoes validas passam pela guarda sem excecao.
        SocialBeneficiaryImportSchema.requireBasisDateConsistent(
                Map.of("eligibility_basis", "CADUNICO", "cadunico_updated_on", "2025-06-01"));
        SocialBeneficiaryImportSchema.requireBasisDateConsistent(
                Map.of("eligibility_basis", "BPC", "cadunico_updated_on", ""));
        SocialBeneficiaryImportSchema.requireBasisDateConsistent(
                Map.of("eligibility_basis", "LOCAL_PROGRAM"));
    }
}
