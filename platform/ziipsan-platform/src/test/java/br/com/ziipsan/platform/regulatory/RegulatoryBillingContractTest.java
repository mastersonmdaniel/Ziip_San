package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import br.com.ziipsan.core.parameters.ParameterLimits;
import br.com.ziipsan.platform.regulatory.RegulatoryActivationRule.PlannedVersion;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.Confirmation;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.ProfileValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato de formato com o consumidor real da P2.5 (correção pós-revisão
 * do GRAVE da P2.31): os valores que a ativação grava em
 * {@code parameter_versions} precisam ser legíveis pelo
 * {@code BillingParameters} do billing, no formato EXATO que ele lê.
 *
 * <p>O platform NÃO pode reusar a classe real: o módulo platform depende
 * só de {@code ziipsan-core} e {@code ziipsan-security} (pom conferido);
 * depender de {@code ziipsan-billing} inverteria a camada (fundação →
 * comercial). Por isso os dois leitores abaixo são uma RÉPLICA FIEL dos
 * métodos {@code dueDateOptions} e {@code minDaysDeliveryToDue} de
 * {@code BillingParameters} (P2.5), linha a linha na lógica de leitura —
 * mesmo erro para não-array, mesmo fallback {@code "default"}, mesma
 * {@link MissingParameterException} — reusando as peças do core que os
 * dois módulos compartilham ({@link Json}, {@link ParameterLimits},
 * {@link MissingParameterException}). O teste
 * {@link #replicaRejectsTheOldFormats()} prova que a réplica não é
 * vacua: ela recusa os formatos antigos exatamente como o original.
 *
 * <p>Auditoria de tipos: TODAS as 24 chaves dos perfis são conferidas
 * contra o tipo declarado na definição do catálogo (core V3,
 * V4_4 §7 da P2.5 e V1_13 da P2.31) — não só as duas do GRAVE.
 */
class RegulatoryBillingContractTest {

    private static final LocalDate FROM = LocalDate.of(2027, 1, 1);
    private static final String DUE_FIXTURE = "[5, 10, 15, 20, 25, 28]";

    // ------------------------------------------------------------------
    // Réplica fiel de BillingParameters (P2.5) — ver javadoc da classe
    // ------------------------------------------------------------------

    /** Réplica de {@code BillingParameters.dueDateOptions(boolean, Integer)}. */
    private static List<Integer> replicaDueDateOptions(String json, boolean concessionaire,
                                                       Integer previousAcceptedCount) {
        if (json == null || json.isBlank()) {
            throw new MissingParameterException("bill.due_date_options", "nenhum valor entregue ao motor");
        }
        JsonNode node = Json.readTree(json);
        if (!node.isArray()) {
            throw new IllegalArgumentException(
                    "Parâmetro 'bill.due_date_options' inválido: esperado array de dias");
        }
        List<Integer> days = new ArrayList<>();
        for (JsonNode day : node) {
            try {
                days.add(new BigDecimal(day.asText()).intValueExact());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Parâmetro 'bill.due_date_options' inválido: esperado inteiro: " + day.asText());
            }
        }
        ParameterLimits.validateDueDateOptions(days, concessionaire, previousAcceptedCount);
        return List.copyOf(days);
    }

    /** Réplica de {@code BillingParameters.minDaysDeliveryToDue(String)}. */
    private static int replicaMinDaysDeliveryToDue(String json, String categoryKey) {
        if (json == null || json.isBlank()) {
            throw new MissingParameterException("bill.min_days_delivery_to_due",
                    "nenhum valor entregue ao motor");
        }
        Map<String, Object> map = Json.readMap(json);
        Object value = categoryKey == null ? null : map.get(categoryKey);
        if (value == null) {
            value = map.get("default");
        }
        if (value == null) {
            throw new MissingParameterException("bill.min_days_delivery_to_due",
                    "sem dias para a categoria " + categoryKey + " e sem \"default\"");
        }
        int days;
        try {
            days = new BigDecimal(String.valueOf(value)).intValueExact();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Parâmetro 'bill.min_days_delivery_to_due' inválido: esperado inteiro: " + value);
        }
        if (days < 0) {
            throw new IllegalArgumentException(
                    "Parâmetro 'bill.min_days_delivery_to_due' inválido: dias não podem ser negativos: " + days);
        }
        return days;
    }

    // ------------------------------------------------------------------
    // Fixtures do tenant (valores SINTÉTICOS de ativação, não de fábrica)
    // ------------------------------------------------------------------

    private static String fixtureFor(String key) {
        return switch (key) {
            case "sac.deadline_counting_rule" -> "\"CALENDAR\"";
            case "meter.average_months" -> "6";
            case "meter.max_estimated_cycles" -> "3";
            case "meter.after_cap_rule" -> "\"BILL_MINIMUM\"";
            case "meter.reading_cycle_days" -> "{\"minDays\":27,\"maxDays\":33}";
            case "bill.due_date_options" -> DUE_FIXTURE;
            case "bill.min_days_delivery_to_due" -> "{\"default\":5}";
            case "fee.fine_pct" -> "2.00";
            case "fee.interest_mode" -> "\"DAILY_RATE_WITH_CAP\"";
            case "fee.interest_daily_pct" -> "0.033";
            case "fee.correction_index" -> "\"IPCA\"";
            case "cut.min_notice_days" -> "30";
            case "cut.max_debt_age_days" -> "120";
            case "rec.sla_hours" -> "{\"normalHours\":48,\"urgentHours\":4,\"undueHours\":4}";
            case "rec.undue_cut_compensation" -> "\"RECONNECTION_4H_NO_CHARGE\"";
            case "sac.response_days" -> "{\"days\":15}";
            case "sac.priority_min_age_years" -> "60";
            case "leak.max_discount_pct_water" -> "70";
            default -> throw new IllegalArgumentException("sem fixture para " + key);
        };
    }

    /** Valores do tenant para ativar o perfil: REQUIRES + completamento de vencimentos. */
    private static Map<String, String> tenantValuesFor(RegulatoryProfileDef profile) {
        Map<String, String> values = new LinkedHashMap<>();
        for (ProfileValue value : profile.values()) {
            if (value.confirmation() == Confirmation.REQUIRES_TENANT_VALUE) {
                values.put(value.definitionKey(), fixtureFor(value.definitionKey()));
            } else if (value.confirmation() == Confirmation.CONFIRMED
                    && RegulatoryLoadGuard.TENANT_COMPLETED_KEYS.contains(value.definitionKey())) {
                values.put(value.definitionKey(), fixtureFor(value.definitionKey()));
            }
        }
        return values;
    }

    private static PlannedVersion planned(List<PlannedVersion> planned, String key) {
        return planned.stream().filter(v -> v.definitionKey().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("sem versão planejada para " + key));
    }

    // ------------------------------------------------------------------
    // A réplica é fiel: recusa os formatos antigos do GRAVE
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Réplica do BillingParameters recusa os formatos ANTIGOS (prova de que a réplica não é vacua)")
    void replicaRejectsTheOldFormats() {
        assertThatThrownBy(() -> replicaDueDateOptions("{\"minCount\":6}", false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("esperado array de dias");
        assertThatThrownBy(() -> replicaMinDaysDeliveryToDue(
                "{\"general\":5,\"publicCategory\":10,\"terminationOnRequest\":1,"
                        + "\"unit\":\"BUSINESS_DAYS\"}", "RES"))
                .isInstanceOf(MissingParameterException.class)
                .hasMessageContaining("default");
    }

    // ------------------------------------------------------------------
    // O formato novo é legível pelo consumidor
    // ------------------------------------------------------------------

    @Test
    @DisplayName("bill.min_days_delivery_to_due de fábrica (ARIS/ARESC/AGIR) é lido pela réplica: default 5, PUBLIC 10")
    void factoryMinDaysReadableByBillingReplica() {
        for (String key : List.of("ARIS_RN19_2019", "ARESC_RES046_2016", "AGIR_RN001_2013")) {
            String json = ScRegulatoryProfiles.byKey(key)
                    .value("bill.min_days_delivery_to_due").valueJson();
            assertThat(replicaMinDaysDeliveryToDue(json, "PUBLIC")).as(key).isEqualTo(10);
            assertThat(replicaMinDaysDeliveryToDue(json, "RES")).as(key).isEqualTo(5);
            assertThat(replicaMinDaysDeliveryToDue(json, null)).as(key).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("Toda ativação planejada é legível pela réplica do billing, em todos os 7 perfis")
    void plannedVersionsReadableByBillingReplicaForEveryProfile() {
        for (RegulatoryProfileDef profile : ScRegulatoryProfiles.all()) {
            List<PlannedVersion> planned = RegulatoryActivationRule.planVersions(
                    profile, tenantValuesFor(profile), FROM, null);
            long expected = profile.values().stream()
                    .filter(v -> v.confirmation() != Confirmation.CONFIRMED_ABSENT).count();
            assertThat(planned).as(profile.profileKey()).hasSize((int) expected);
            // bill.due_date_options planejado é a lista do tenant, legível:
            assertThat(replicaDueDateOptions(
                    planned(planned, "bill.due_date_options").valueJson(), false, null))
                    .as(profile.profileKey()).containsExactly(5, 10, 15, 20, 25, 28);
            // bill.min_days_delivery_to_due planejado tem "default" legível:
            assertThat(replicaMinDaysDeliveryToDue(
                    planned(planned, "bill.min_days_delivery_to_due").valueJson(), "QUALQUER"))
                    .as(profile.profileKey()).isEqualTo(5);
            // E NENHUMA versão planejada carrega o requisito {"minCount"}:
            for (PlannedVersion version : planned) {
                assertThat(version.valueJson()).as("%s/%s", profile.profileKey(),
                        version.definitionKey()).doesNotContain("minCount");
            }
        }
    }

    // ------------------------------------------------------------------
    // Auditoria de tipos de TODO o catálogo contra as definições
    // ------------------------------------------------------------------

    /** Tipo declarado de cada chave (core V3 / V4_4 §7 / V1_13) -> forma JSON esperada. */
    private static String declaredKind(String key) {
        return switch (key) {
            case "bill.due_date_options" -> "LIST_INT";
            case "reg.normative_facts" -> "TABLE";
            case "reg.general_conditions_act", "reg.ana_nr_adoption", "meter.reading_cycle_days",
                 "bill.min_days_delivery_to_due", "cut.forbidden_window", "rec.sla_hours",
                 "sac.response_days" -> "OBJECT";
            case "reg.regulator", "sac.deadline_counting_rule", "meter.after_cap_rule",
                 "fee.interest_mode", "fee.correction_index", "rec.undue_cut_compensation" -> "ENUM";
            case "meter.average_months", "meter.max_estimated_cycles", "ret.billing_history_months",
                 "cut.min_notice_days", "cut.max_debt_age_days", "sac.priority_min_age_years" -> "INT";
            case "fee.fine_pct", "fee.interest_daily_pct", "leak.max_discount_pct_water" -> "PERCENT";
            default -> throw new IllegalArgumentException("chave fora da auditoria: " + key);
        };
    }

    private static void assertKind(String kind, String json, String context) {
        JsonNode node = Json.readTree(json);
        switch (kind) {
            case "LIST_INT", "TABLE" -> {
                assertThat(node.isArray()).as("%s (%s)", context, kind).isTrue();
                if ("LIST_INT".equals(kind)) {
                    for (JsonNode item : node) {
                        assertThat(item.isIntegralNumber()).as(context).isTrue();
                    }
                }
            }
            case "OBJECT" -> assertThat(node.isObject()).as("%s (%s)", context, kind).isTrue();
            case "ENUM" -> assertThat(node.isTextual()).as("%s (%s)", context, kind).isTrue();
            case "INT" -> assertThat(node.isIntegralNumber()).as("%s (%s)", context, kind).isTrue();
            case "PERCENT" -> assertThat(node.isNumber()).as("%s (%s)", context, kind).isTrue();
            default -> throw new IllegalArgumentException(kind);
        }
    }

    @Test
    @DisplayName("Auditoria completa: todo valor de fábrica CONFIRMED casa com o tipo da definição (24 chaves)")
    void factoryValuesMatchDeclaredCatalogTypes() {
        for (RegulatoryProfileDef profile : ScRegulatoryProfiles.all()) {
            for (ProfileValue value : profile.values()) {
                String kind = declaredKind(value.definitionKey());
                if (value.confirmation() != Confirmation.CONFIRMED) {
                    continue;
                }
                if ("bill.due_date_options".equals(value.definitionKey())) {
                    // Exceção documentada e estrutural: o valor de fábrica
                    // desta chave é o REQUISITO {"minCount":N} do catálogo
                    // (objeto com inteiro positivo), nunca uma versão —
                    // a versão legível é a lista do tenant (teste acima).
                    JsonNode requirement = Json.readTree(value.valueJson());
                    assertThat(requirement.isObject()).as(profile.profileKey()).isTrue();
                    assertThat(requirement.get("minCount").isIntegralNumber())
                            .as(profile.profileKey()).isTrue();
                    assertThat(requirement.get("minCount").asInt())
                            .as(profile.profileKey()).isGreaterThanOrEqualTo(1);
                    continue;
                }
                assertKind(kind, value.valueJson(),
                        profile.profileKey() + "/" + value.definitionKey());
            }
            // E toda versão PLANEJADA casa com o tipo declarado, sem exceção:
            for (PlannedVersion version : RegulatoryActivationRule.planVersions(
                    profile, tenantValuesFor(profile), FROM, null)) {
                assertKind(declaredKind(version.definitionKey()), version.valueJson(),
                        "planejado " + profile.profileKey() + "/" + version.definitionKey());
            }
        }
    }

    // ------------------------------------------------------------------
    // Contrato do valor do tenant: recusa explícita, nunca silêncio
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Valor do tenant para chave CONFIRMED é RECUSADO nomeando a chave (mesmo igual ao de fábrica)")
    void tenantValueForConfirmedKeyRefusedExplicitly() {
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        Map<String, String> values = tenantValuesFor(aris);
        values.put("fee.fine_pct", "2.00");
        assertThatThrownBy(() -> RegulatoryActivationRule.planVersions(aris, values, FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fee.fine_pct")
                .hasMessageContaining("confirmada pelo perfil");
    }

    @Test
    @DisplayName("Valor do tenant para chave CONFIRMED_ABSENT é RECUSADO nomeando a chave")
    void tenantValueForConfirmedAbsentRefusedExplicitly() {
        RegulatoryProfileDef aresc = ScRegulatoryProfiles.byKey("ARESC_RES046_2016");
        Map<String, String> values = tenantValuesFor(aresc);
        values.put("cut.max_debt_age_days", "120");
        assertThatThrownBy(() -> RegulatoryActivationRule.planVersions(aresc, values, FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cut.max_debt_age_days")
                .hasMessageContaining("AUSÊNCIA");
    }

    @Test
    @DisplayName("Completamento de vencimentos: sem dias recusa nomeando; abaixo do mínimo confirmado recusa; no mínimo passa")
    void dueDateCompletionValidatedAgainstConfirmedMinimum() {
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        Map<String, String> withoutDays = tenantValuesFor(aris);
        withoutDays.remove("bill.due_date_options");
        assertThatThrownBy(() -> RegulatoryActivationRule.planVersions(aris, withoutDays, FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.due_date_options");

        Map<String, String> tooFew = tenantValuesFor(aris);
        tooFew.put("bill.due_date_options", "[5, 10, 15]");
        assertThatThrownBy(() -> RegulatoryActivationRule.planVersions(aris, tooFew, FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mínimo de 6");

        // CISAM-SUL: o mínimo confirmado é 4 — 4 dias do tenant passam, 3 não.
        RegulatoryProfileDef sul = ScRegulatoryProfiles.byKey("CISAM_SUL_RN03_2017");
        Map<String, String> four = tenantValuesFor(sul);
        four.put("bill.due_date_options", "[5, 10, 20, 28]");
        List<PlannedVersion> planned = RegulatoryActivationRule.planVersions(sul, four, FROM, null);
        assertThat(replicaDueDateOptions(
                planned(planned, "bill.due_date_options").valueJson(), false, null))
                .containsExactly(5, 10, 20, 28);
        Map<String, String> three = tenantValuesFor(sul);
        three.put("bill.due_date_options", "[5, 10, 20]");
        assertThatThrownBy(() -> RegulatoryActivationRule.planVersions(sul, three, FROM, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mínimo de 4");
    }

    // ------------------------------------------------------------------
    // DAD-28: códigos reais do catálogo da P1.34
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DAD-28: NR -> cert_standard.code confere com o catálogo da P1.34 e toda adoção liga")
    void dad28LinkageCodesAreRealCatalogCodes() {
        // Lidos do arquivo de catálogo da área 02 (ANA), gerado da fonte
        // da P1.34 (catalogo-normas.json): code = catalog_key = id slug.
        assertThat(AnaNormCatalogLink.catalogCode("NR1")).isEqualTo("ana-nr-1-res-79-2021");
        assertThat(AnaNormCatalogLink.catalogCode("NR3")).isEqualTo("ana-nr-3-res-161-2023");
        assertThat(AnaNormCatalogLink.catalogCode("NR11")).isEqualTo("ana-nr-11-res-230-2024");
        assertThat(AnaNormCatalogLink.catalogCode("NR13")).isEqualTo("ana-nr-13-res-271-2025");
        assertThat(AnaNormCatalogLink.catalogCode("NR16")).isEqualTo("ana-nr-16-res-302-2026");
        for (RegulatoryProfileDef profile : ScRegulatoryProfiles.all()) {
            org.assertj.core.api.Assertions.assertThatCode(
                            () -> AnaNormCatalogLink.validateAdoptionLinkage(profile))
                    .as(profile.profileKey()).doesNotThrowAnyException();
        }
        // O JSON de adoção carrega o catalogCode de cada NR:
        RegulatoryProfileDef aris = ScRegulatoryProfiles.byKey("ARIS_RN19_2019");
        JsonNode adoption = Json.readTree(aris.value("reg.ana_nr_adoption").valueJson());
        assertThat(adoption.get("NR11").get("catalogCode").asText())
                .isEqualTo("ana-nr-11-res-230-2024");
        assertThat(adoption.get("NR13").get("catalogCode").asText())
                .isEqualTo("ana-nr-13-res-271-2025");
        // ARIS tem 10 NR com ato: são os 10 códigos que o aceite confere
        // em cert_standard.
        assertThat(AnaNormCatalogLink.adoptedCatalogCodes(aris)).hasSize(10)
                .contains("ana-nr-11-res-230-2024", "ana-nr-13-res-271-2025");
    }
}
