package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.Confirmation;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.ProfileValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guardas das migrations da P2.31 (mesmo modelo dos testes de consistência
 * da P2.5): a R__ gerada carrega exatamente o catálogo Java (a fonte
 * única), a V1_13 traz a estrutura que o serviço usa e nenhuma regra
 * recusada vazou como valor na carga SQL.
 */
class RegulatoryCatalogConsistencyTest {

    private static String resource(String path) {
        try (InputStream in = RegulatoryCatalogConsistencyTest.class.getResourceAsStream(path)) {
            assertThat(in).as("recurso %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Falha lendo " + path, e);
        }
    }

    private static String catalogSql() {
        return resource("/db/migration/tenant/R__regulatory_profile_catalog.sql");
    }

    @Test
    @DisplayName("R__: todo perfil e todo valor do catálogo Java estão na carga, com o mesmo conteúdo")
    void repeatableLoadMatchesJavaCatalog() {
        String sql = catalogSql();
        int totalValues = 0;
        int totalDivergences = 0;
        for (RegulatoryProfileDef profile : ScRegulatoryProfiles.all()) {
            assertThat(sql).contains("'" + profile.profileKey() + "'");
            totalDivergences += profile.divergences().size();
            for (ProfileValue value : profile.values()) {
                totalValues++;
                String rowPrefix = "'" + profile.profileKey() + "', '" + value.definitionKey() + "', ";
                assertThat(sql).as("%s/%s", profile.profileKey(), value.definitionKey())
                        .contains(rowPrefix);
                if (value.confirmation() == Confirmation.CONFIRMED) {
                    // O JSON canonizado da carga e o do catalogo sao o mesmo objeto.
                    assertThat(Json.readTree(value.valueJson())).isNotNull();
                    String canonical = Json.readTree(value.valueJson()).toString();
                    assertThat(sql).as("valor de %s/%s", profile.profileKey(),
                            value.definitionKey()).contains(canonical.replace("'", "''"));
                } else {
                    // Sem fabrica ou ausencia confirmada: a carga grava NULL, nunca um numero.
                    assertThat(sql).as("%s/%s sem valor", profile.profileKey(),
                            value.definitionKey()).contains(rowPrefix + "NULL,");
                }
            }
            for (RegulatoryProfileDef.ProfileDivergence divergence : profile.divergences()) {
                assertThat(sql).contains("'" + divergence.ruleCode() + "'");
                assertThat(sql).contains(divergence.federalNorm());
            }
        }
        // A guarda de contagem da R__ confere com o catalogo.
        assertThat(sql).contains("v_profiles <> " + ScRegulatoryProfiles.all().size());
        assertThat(sql).contains("v_values <> " + totalValues);
        assertThat(sql).contains("v_divergences <> " + totalDivergences);
    }

    @Test
    @DisplayName("R__: nenhuma regra recusada aparece como valor; o piso federal é o valor da janela de corte")
    void repeatableLoadHasNoRejectedRuleAsValue() {
        String sql = catalogSql();
        assertThat(sql).doesNotContain("FRIDAY_UNTIL_12H");
        assertThat(sql).doesNotContain("SOCIAL_LOSS_AFTER_TWO_CUTS");
        assertThat(sql).contains("FEDERAL_FLOOR_14_015");
        assertThat(sql).contains("SOCIAL_BENEFIT_LOSS_AFTER_TWO_CUTS");
    }

    @Test
    @DisplayName("V1_13: município no perfil, EXCLUDE corrigido, ativações e as definições novas do catálogo")
    void versionedMigrationShape() {
        String sql = resource("/db/migration/tenant/V1_13__regulatory_profile_origin.sql");
        assertThat(sql).contains("municipality_ibge");
        assertThat(sql).contains("DROP CONSTRAINT IF EXISTS excl_regulatory_profiles_no_overlap");
        assertThat(sql).contains("COALESCE(municipality_ibge, '')");
        assertThat(sql).contains("regulatory_profile_catalog");
        assertThat(sql).contains("regulatory_profile_values");
        assertThat(sql).contains("regulatory_profile_divergences");
        assertThat(sql).contains("regulatory_profile_activations");
        assertThat(sql).contains("regulatory_activation_id");
        for (String key : new String[]{"reg.regulator", "reg.general_conditions_act",
                "reg.ana_nr_adoption", "reg.normative_facts", "meter.reading_cycle_days",
                "fee.correction_index", "cut.forbidden_window", "cut.max_debt_age_days",
                "rec.sla_hours", "rec.undue_cut_compensation", "sac.response_days",
                "sac.priority_min_age_years", "leak.max_discount_pct_water",
                "sac.deadline_counting_rule", "cut.min_notice_days"}) {
            assertThat(sql).as("definição %s", key).contains("'" + key + "'");
        }
        // reg.normative_facts e TABLE (array de objetos), nao OBJECT:
        // o valor construido pelo Builder e um array JSON (correcao
        // pos-revisao; TABLE e o tipo de bill.auto_review_value_bands).
        assertThat(sql).contains("('reg.normative_facts', 'TABLE'");
        // Toda chave usada pelos perfis TEM definição (asserção real,
        // pós-revisão — antes o loop só conferia chave não-branca):
        // ou já existia no core V3, ou foi registrada pela V4_4 §7 da
        // P2.5, ou a V1_13 a registra. Conjuntos fixos, conferidos contra
        // os três arquivos-fonte.
        java.util.Set<String> definedKeys = new java.util.HashSet<>();
        // Core V3 (V3__tenant_parameters_catalog.sql):
        definedKeys.addAll(java.util.List.of("meter.average_months", "meter.max_estimated_cycles",
                "bill.due_date_options", "fee.fine_pct", "fee.interest_mode",
                "fee.interest_daily_pct", "ret.billing_history_months"));
        // V4_4 §7.2/§7.3 da P2.5:
        definedKeys.addAll(java.util.List.of("meter.after_cap_rule", "bill.min_days_delivery_to_due"));
        // V1_13 da P2.31 (as 15 acima):
        definedKeys.addAll(java.util.List.of("reg.regulator", "reg.general_conditions_act",
                "reg.ana_nr_adoption", "reg.normative_facts", "meter.reading_cycle_days",
                "fee.correction_index", "cut.forbidden_window", "cut.max_debt_age_days",
                "cut.min_notice_days", "rec.sla_hours", "rec.undue_cut_compensation",
                "sac.response_days", "sac.priority_min_age_years",
                "sac.deadline_counting_rule", "leak.max_discount_pct_water"));
        assertThat(definedKeys).hasSize(24);
        for (RegulatoryProfileDef profile : ScRegulatoryProfiles.all()) {
            for (ProfileValue value : profile.values()) {
                assertThat(definedKeys).as("definição de %s (perfil %s)",
                        value.definitionKey(), profile.profileKey())
                        .contains(value.definitionKey());
            }
        }
    }
}
