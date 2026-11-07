package br.com.ziipsan.commercial.registry.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-12: os esquemas de importação dos catálogos auxiliares sobre o
 * importador genérico da P1.40 — declaração completa e conferida, e a
 * recusa de linha inválida (a linha recusada fica fora do lote e vai para o
 * relatório de recusas; a aplicação, por OS de lote, é a do ImportService).
 */
class RegistryCatalogImportSchemaTest {

    private static final RegistryCatalogImportSchemas CONFIG = new RegistryCatalogImportSchemas();

    private static List<RegistryCatalogImportSchema> declared() {
        return List.of(
                CONFIG.customerTypesImportSchema(),
                CONFIG.propertyProfilesImportSchema(),
                CONFIG.relationRolesImportSchema(),
                CONFIG.documentExemptionReasonsImportSchema(),
                CONFIG.customerDeleteReasonsImportSchema(),
                CONFIG.unitLinkEndReasonsImportSchema(),
                CONFIG.sewerDischargesImportSchema(),
                CONFIG.consentPurposesImportSchema(),
                CONFIG.ibgeMunicipalitiesImportSchema());
    }

    private static ImportValidator.DbChecks db(Set<String> existingKeys) {
        return new ImportValidator.DbChecks() {
            @Override
            public boolean foreignKeyExists(String table, String column, String value) {
                return true;
            }

            @Override
            public boolean existsInDatabase(Map<String, String> values) {
                return existingKeys.contains(values.get("code"));
            }
        };
    }

    @Test
    @DisplayName("nove esquemas declarados, códigos e tabelas distintos, todos na allowlist")
    void declaredSchemas() {
        List<RegistryCatalogImportSchema> schemas = declared();
        assertThat(schemas).hasSize(9);
        assertThat(schemas.stream().map(ImportSchema::code).toList())
                .containsExactlyInAnyOrder(
                        "registry.customer_types", "registry.property_profiles", "registry.relation_roles",
                        "registry.document_exemption_reasons", "registry.customer_delete_reasons",
                        "registry.unit_link_end_reasons", "registry.sewer_discharges",
                        "registry.consent_purposes", "registry.ibge_municipalities");
        assertThat(schemas.stream().map(RegistryCatalogImportSchema::table).toList())
                .doesNotHaveDuplicates()
                .allSatisfy(table -> assertThat(RegistryCatalogImportSchema.TABLES).contains(table));
        for (RegistryCatalogImportSchema schema : schemas) {
            assertThat(schema.columns().get(0).name()).isEqualTo("code");
            assertThat(schema.columns().get(0).uniqueKey()).isTrue();
            assertThat(schema.columns()).extracting(ImportColumn::name).contains("name", "active");
        }
    }

    @Test
    @DisplayName("extras espelham o CRUD genérico: applies_to com domínio, system_role obrigatório, UF com 27 valores")
    void extrasMirrorTheCatalogCrud() {
        RegistryCatalogImportSchema customerTypes = CONFIG.customerTypesImportSchema();
        assertThat(customerTypes.columns()).filteredOn(c -> c.name().equals("applies_to"))
                .singleElement()
                .satisfies(c -> assertThat(c.domain()).containsExactly("PF", "PJ", "BOTH"));

        RegistryCatalogImportSchema roles = CONFIG.relationRolesImportSchema();
        assertThat(roles.columns()).filteredOn(c -> c.name().equals("system_role"))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.required()).isTrue();
                    assertThat(c.domain()).containsExactly("OWNER", "USER", "RESPONSIBLE");
                });

        RegistryCatalogImportSchema ibge = CONFIG.ibgeMunicipalitiesImportSchema();
        assertThat(ibge.columns()).filteredOn(c -> c.name().equals("state"))
                .singleElement()
                .satisfies(c -> assertThat(c.domain()).hasSize(27).contains("SC", "SP"));
    }

    @Test
    @DisplayName("linha inválida é recusada e fica fora do lote; a válida é aceita (customer_types)")
    void invalidRowRejected() {
        ImportSchema schema = CONFIG.customerTypesImportSchema();
        String csv = "code,name,applies_to,is_public_entity,active\n"
                + "T1,Tipo Um,BOTH,nao,sim\n"
                + "T2,Tipo Dois,XX,nao,sim\n"
                + "T3,Tipo Tres,PF,talvez,sim\n"
                + "T1,Tipo Repetido,PF,nao,sim\n"
                + "T9,Tipo Existente,PF,nao,sim\n";
        ImportValidator.ValidationResult result = ImportValidator.validate(
                schema, ImportCsvParser.parse(csv), db(Set.of("T9")));

        // Só a primeira linha entra no lote.
        assertThat(result.accepted()).hasSize(1);
        assertThat(result.accepted().get(0).values().get("code")).isEqualTo("T1");
        // Uma recusa por linha inválida, com o motivo certo e a linha certa.
        assertThat(result.rejections()).hasSize(4);
        assertThat(result.rejections()).filteredOn(r -> r.rowNumber() == 2)
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.field()).isEqualTo("applies_to");
                    assertThat(r.reasonCode()).isEqualTo("INVALID_DOMAIN");
                    assertThat(r.message()).doesNotContain("XX");
                });
        assertThat(result.rejections()).filteredOn(r -> r.rowNumber() == 3)
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.field()).isEqualTo("is_public_entity");
                    assertThat(r.reasonCode()).isEqualTo("INVALID_TYPE");
                });
        assertThat(result.rejections()).filteredOn(r -> r.rowNumber() == 4)
                .singleElement()
                .satisfies(r -> assertThat(r.reasonCode()).isEqualTo("DUPLICATE_IN_FILE"));
        assertThat(result.rejections()).filteredOn(r -> r.rowNumber() == 5)
                .singleElement()
                .satisfies(r -> assertThat(r.reasonCode()).isEqualTo("DUPLICATE_IN_DATABASE"));
    }

    @Test
    @DisplayName("system_role ausente em relation_roles é recusa REQUIRED")
    void requiredExtraRejected() {
        ImportSchema schema = CONFIG.relationRolesImportSchema();
        String csv = "code,name,system_role,active\n"
                + "DONO,Dono,,sim\n"
                + "USUARIO,Usuario,USER,sim\n";
        ImportValidator.ValidationResult result = ImportValidator.validate(
                schema, ImportCsvParser.parse(csv), db(Set.of()));
        assertThat(result.accepted()).hasSize(1);
        assertThat(result.rejections()).singleElement().satisfies(r -> {
            assertThat(r.rowNumber()).isEqualTo(1);
            assertThat(r.field()).isEqualTo("system_role");
            assertThat(r.reasonCode()).isEqualTo("REQUIRED");
        });
    }

    @Test
    @DisplayName("tabela fora da allowlist falha na construção, não na importação")
    void tableOutsideAllowlistFailsFast() {
        assertThatThrownBy(() -> new RegistryCatalogImportSchema(
                "registry.service_orders", "OS", "service_orders", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlist");
        assertThatThrownBy(() -> new RegistryCatalogImportSchema(
                "registry.block_faces", "Faces", "block_faces", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
