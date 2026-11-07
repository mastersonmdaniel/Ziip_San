package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.Confirmation;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.DeadlineBasis;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.ProfileValue;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Um bloco por perfil de SC (P2.31, RAS-14, RAS-24, JOR-11): valores
 * convergentes, diferenças por perfil, divergências, adoção das NR e a
 * marca de confirmação de cada chave. Cada valor esperado vem do mapa de
 * ERIs (docs/normas/partes/04) com o artigo citado no próprio perfil.
 */
class ScRegulatoryProfilesTest {

    private static RegulatoryProfileDef profile(String key) {
        RegulatoryProfileDef profile = ScRegulatoryProfiles.byKey(key);
        assertThat(profile).as("perfil %s", key).isNotNull();
        return profile;
    }

    private static int intValue(RegulatoryProfileDef profile, String key) {
        ProfileValue value = profile.value(key);
        assertThat(value).as("%s em %s", key, profile.profileKey()).isNotNull();
        assertThat(value.confirmation()).as("%s em %s", key, profile.profileKey())
                .isEqualTo(Confirmation.CONFIRMED);
        return Json.readTree(value.valueJson()).asInt();
    }

    private static BigDecimal decimalValue(RegulatoryProfileDef profile, String key) {
        ProfileValue value = profile.value(key);
        assertThat(value).isNotNull();
        assertThat(value.confirmation()).isEqualTo(Confirmation.CONFIRMED);
        return Json.readTree(value.valueJson()).decimalValue();
    }

    private static String textValue(RegulatoryProfileDef profile, String key) {
        ProfileValue value = profile.value(key);
        assertThat(value).isNotNull();
        assertThat(value.confirmation()).isEqualTo(Confirmation.CONFIRMED);
        return Json.readTree(value.valueJson()).asText();
    }

    private static JsonNode objectValue(RegulatoryProfileDef profile, String key) {
        ProfileValue value = profile.value(key);
        assertThat(value).isNotNull();
        assertThat(value.confirmation()).isEqualTo(Confirmation.CONFIRMED);
        return Json.readTree(value.valueJson());
    }

    @Test
    @DisplayName("Os 7 perfis de SC (6 ERI, CISAM-MO em duas vigências) passam na guarda de carga")
    void allProfilesPassLoadGuard() {
        assertThat(ScRegulatoryProfiles.all()).hasSize(7);
        for (RegulatoryProfileDef profile : ScRegulatoryProfiles.all()) {
            assertThatCode(() -> RegulatoryLoadGuard.validateProfile(profile))
                    .as("guarda de %s", profile.profileKey()).doesNotThrowAnyException();
            assertThatCode(() -> RegulatoryLoadGuard.validateDueDateOptionsFor(profile, false))
                    .as("vencimentos de %s", profile.profileKey()).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("ARIS: convergentes de SC, teto de 120 dias só dela e contagem em dias corridos")
    void arisProfile() {
        RegulatoryProfileDef aris = profile("ARIS_RN19_2019");
        assertThat(intValue(aris, "meter.average_months")).isEqualTo(6);
        assertThat(intValue(aris, "meter.max_estimated_cycles")).isEqualTo(3);
        assertThat(textValue(aris, "meter.after_cap_rule")).isEqualTo("BILL_MINIMUM");
        assertThat(decimalValue(aris, "fee.fine_pct")).isEqualByComparingTo("2.00");
        assertThat(decimalValue(aris, "fee.interest_daily_pct")).isEqualByComparingTo("0.033");
        assertThat(textValue(aris, "fee.correction_index")).isEqualTo("IPCA");
        assertThat(intValue(aris, "cut.min_notice_days")).isEqualTo(30);
        assertThat(aris.value("cut.min_notice_days").deadlineBasis())
                .isEqualTo(DeadlineBasis.CALENDAR_DAYS);
        assertThat(intValue(aris, "cut.max_debt_age_days")).isEqualTo(120);
        assertThat(aris.value("cut.max_debt_age_days").sourceArticle()).contains("153");
        assertThat(RegulatoryLoadGuard.dueDateMinCount("ARIS", aris.value("bill.due_date_options").valueJson()))
                .isEqualTo(6);
        JsonNode minDays = objectValue(aris, "bill.min_days_delivery_to_due");
        // Formato do consumidor da P2.5 (correcao pos-revisao): "default"
        // para as demais categorias e a categoria publica como chave.
        assertThat(minDays.get("default").asInt()).isEqualTo(5);
        assertThat(minDays.get("PUBLIC").asInt()).isEqualTo(10);
        JsonNode cycle = objectValue(aris, "meter.reading_cycle_days");
        assertThat(cycle.get("minDays").asInt()).isEqualTo(27);
        assertThat(cycle.get("maxDays").asInt()).isEqualTo(33);
        JsonNode sla = objectValue(aris, "rec.sla_hours");
        assertThat(sla.get("normalHours").asInt()).isEqualTo(48);
        assertThat(sla.get("urgentHours").asInt()).isEqualTo(4);
        assertThat(sla.get("undueHours").asInt()).isEqualTo(4);
        assertThat(textValue(aris, "rec.undue_cut_compensation"))
                .isEqualTo("RECONNECTION_4H_NO_CHARGE");
        assertThat(textValue(aris, "sac.deadline_counting_rule")).isEqualTo("CALENDAR");
        assertThat(decimalValue(aris, "leak.max_discount_pct_water")).isEqualByComparingTo("70");
        // Idade do prioritario: o mapa nao fixa nos trechos lidos -> sem fabrica.
        assertThat(aris.value("sac.priority_min_age_years").confirmation())
                .isEqualTo(Confirmation.REQUIRES_TENANT_VALUE);
        assertThat(aris.value("sac.priority_min_age_years").valueJson()).isNull();
        // Divergencia do corte de sexta ao meio-dia.
        assertThat(aris.divergences()).hasSize(1);
        assertThat(aris.divergences().get(0).ruleCode()).isEqualTo("CUT_FRIDAY_NOON");
        assertThat(aris.divergences().get(0).localSource()).contains("158");
        assertThat(aris.divergences().get(0).resolution())
                .isEqualTo(RegulatoryProfileDef.ProfileDivergence.Resolution.FEDERAL_FLOOR_APPLIED);
        // Adocao: NR 11 parcial com a RN 55/2026.
        JsonNode adoption = objectValue(aris, "reg.ana_nr_adoption");
        assertThat(adoption.get("NR11").get("status").asText()).isEqualTo("PARTIAL");
        assertThat(adoption.get("NR11").get("acts").toString()).contains("RN 55/2026");
        assertThat(adoption.get("NR13").get("acts").toString()).contains("RN 56/2026");
    }

    @Test
    @DisplayName("ARESC: saldo parcelado em 3 vezes, sem teto de idade, prioritário aos 65, dias úteis")
    void arescProfile() {
        RegulatoryProfileDef aresc = profile("ARESC_RES046_2016");
        assertThat(intValue(aresc, "meter.average_months")).isEqualTo(6);
        assertThat(intValue(aresc, "meter.max_estimated_cycles")).isEqualTo(3);
        assertThat(textValue(aresc, "meter.after_cap_rule"))
                .isEqualTo("SETTLE_BALANCE_IN_3_INSTALLMENTS");
        assertThat(aresc.value("meter.after_cap_rule").sourceArticle()).contains("90");
        assertThat(aresc.value("cut.max_debt_age_days").confirmation())
                .isEqualTo(Confirmation.CONFIRMED_ABSENT);
        assertThat(aresc.value("cut.max_debt_age_days").valueJson()).isNull();
        assertThat(intValue(aresc, "sac.priority_min_age_years")).isEqualTo(65);
        assertThat(textValue(aresc, "sac.deadline_counting_rule")).isEqualTo("BUSINESS");
        assertThat(aresc.value("cut.min_notice_days").deadlineBasis())
                .isEqualTo(DeadlineBasis.BUSINESS_DAYS);
        assertThat(textValue(aresc, "rec.undue_cut_compensation"))
                .isEqualTo("GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION");
        JsonNode adoption = objectValue(aresc, "reg.ana_nr_adoption");
        assertThat(adoption.get("NR11").get("status").asText()).isEqualTo("NO_ACT_LOCATED");
        assertThat(adoption.get("NR3").get("acts").toString()).contains("Res. 433/2026");
        assertThat(adoption.get("NR8").get("acts").toString()).contains("Res. 430/2026");
        assertThat(aresc.divergences()).hasSize(1);
        assertThat(aresc.divergences().get(0).localSource()).contains("85");
    }

    @Test
    @DisplayName("AGIR: consumo mínimo depois da média, prioritário aos 60, contagem não definida e as duas recusas")
    void agirProfile() {
        RegulatoryProfileDef agir = profile("AGIR_RN001_2013");
        assertThat(textValue(agir, "meter.after_cap_rule")).isEqualTo("BILL_MINIMUM");
        assertThat(intValue(agir, "sac.priority_min_age_years")).isEqualTo(60);
        assertThat(agir.value("cut.max_debt_age_days").confirmation())
                .isEqualTo(Confirmation.CONFIRMED_ABSENT);
        // Contagem nao definida na RN 001/2013: sem valor de fabrica, base NOT_DEFINED.
        ProfileValue counting = agir.value("sac.deadline_counting_rule");
        assertThat(counting.confirmation()).isEqualTo(Confirmation.REQUIRES_TENANT_VALUE);
        assertThat(counting.deadlineBasis()).isEqualTo(DeadlineBasis.NOT_DEFINED);
        assertThat(textValue(agir, "rec.undue_cut_compensation"))
                .isEqualTo("GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE");
        assertThat(agir.divergences()).hasSize(2);
        assertThat(agir.divergences())
                .anySatisfy(d -> {
                    assertThat(d.ruleCode()).isEqualTo("CUT_FRIDAY_NOON");
                    assertThat(d.localSource()).contains("148");
                })
                .anySatisfy(d -> {
                    assertThat(d.ruleCode()).isEqualTo("SOCIAL_BENEFIT_LOSS_AFTER_TWO_CUTS");
                    assertThat(d.localSource()).contains("127");
                    assertThat(d.federalNorm()).contains("14.898");
                    assertThat(d.resolution()).isEqualTo(
                            RegulatoryProfileDef.ProfileDivergence.Resolution.RULE_REJECTED);
                });
        JsonNode adoption = objectValue(agir, "reg.ana_nr_adoption");
        assertThat(adoption.get("NR9").get("acts").toString()).contains("RN 025/2026");
        assertThat(adoption.get("NR11").get("status").asText()).isEqualTo("NO_ACT_LOCATED");
    }

    @Test
    @DisplayName("AGR-Tubarão: sem condições comerciais localizadas, o comercial exige valor do tenant")
    void agrTubaraoRequiresTenantValues() {
        RegulatoryProfileDef agr = profile("AGR_TUBARAO_RES007_2013");
        for (String key : List.of("fee.fine_pct", "fee.interest_daily_pct", "fee.correction_index",
                "meter.average_months", "meter.max_estimated_cycles", "meter.after_cap_rule",
                "bill.due_date_options", "bill.min_days_delivery_to_due", "cut.min_notice_days",
                "leak.max_discount_pct_water")) {
            ProfileValue value = agr.value(key);
            assertThat(value).as(key).isNotNull();
            assertThat(value.confirmation()).as(key)
                    .isEqualTo(Confirmation.REQUIRES_TENANT_VALUE);
            assertThat(value.valueJson()).as(key).isNull();
        }
        // O que a Res. 007/2013 confirma entra: religacao em 24 h e os pisos federais.
        JsonNode sla = objectValue(agr, "rec.sla_hours");
        assertThat(sla.get("reconnectionHours").asInt()).isEqualTo(24);
        assertThat(intValue(agr, "ret.billing_history_months")).isEqualTo(120);
        assertThat(objectValue(agr, "cut.forbidden_window").get("rule").asText())
                .isEqualTo("FEDERAL_FLOOR_14_015");
        assertThat(agr.divergences()).isEmpty();
        JsonNode adoption = objectValue(agr, "reg.ana_nr_adoption");
        assertThat(adoption.get("NR8").get("acts").toString()).contains("Res. 048/2026");
        assertThat(adoption.get("NR11").get("status").asText()).isEqualTo("NO_ACT_LOCATED");
    }

    @Test
    @DisplayName("CISAM Meio-Oeste RN 01/2017: aviso de 30 dias (art. 104) e encargos remetidos à lei municipal")
    void cisamMeioOesteRn01() {
        RegulatoryProfileDef v1 = profile("CISAM_MO_RN01_2017");
        assertThat(intValue(v1, "cut.min_notice_days")).isEqualTo(30);
        assertThat(v1.value("cut.min_notice_days").sourceArticle()).contains("104");
        for (String key : List.of("fee.fine_pct", "fee.interest_daily_pct", "meter.average_months",
                "bill.due_date_options")) {
            assertThat(v1.value(key).confirmation()).as(key)
                    .isEqualTo(Confirmation.REQUIRES_TENANT_VALUE);
        }
        assertThat(v1.divergences()).hasSize(1);
        assertThat(v1.divergences().get(0).localSource()).contains("107");
        // Os atos de adocao sao de 2025/2026: nao retroagem a este perfil.
        assertThat(v1.value("reg.ana_nr_adoption")).isNull();
        assertThat(v1.validFrom()).isEqualTo(java.time.LocalDate.of(2017, 12, 21));
        assertThat(v1.validTo()).isEqualTo(java.time.LocalDate.of(2027, 5, 19));
    }

    @Test
    @DisplayName("CISAM Meio-Oeste RN 05/2026: dias úteis, religação 12/24/120 h e adoção da NR 11 e da NR 13")
    void cisamMeioOesteRn05() {
        RegulatoryProfileDef v2 = profile("CISAM_MO_RN05_2026");
        assertThat(textValue(v2, "sac.deadline_counting_rule")).isEqualTo("BUSINESS");
        assertThat(intValue(v2, "cut.min_notice_days")).isEqualTo(30);
        JsonNode sla = objectValue(v2, "rec.sla_hours");
        assertThat(sla.get("confirmedHours").toString()).isEqualTo("[12,24,120]");
        JsonNode adoption = objectValue(v2, "reg.ana_nr_adoption");
        assertThat(adoption.get("NR11").get("status").asText()).isEqualTo("ADOPTED");
        assertThat(adoption.get("NR11").get("acts").toString()).contains("RN 05/2026");
        assertThat(adoption.get("NR13").get("acts").toString()).contains("RN 04/2026");
        assertThat(adoption.get("NR9").get("status").asText()).isEqualTo("NO_ACT_LOCATED");
        assertThat(v2.validFrom()).isEqualTo(java.time.LocalDate.of(2027, 5, 20));
        assertThat(v2.validTo()).isNull();
    }

    @Test
    @DisplayName("CISAM-SUL: só o confirmado do quadro de SC entra (4 datas, prioritário aos 60); o resto exige tenant")
    void cisamSulPartialLoad() {
        RegulatoryProfileDef sul = profile("CISAM_SUL_RN03_2017");
        assertThat(RegulatoryLoadGuard.dueDateMinCount("CISAM_SUL",
                sul.value("bill.due_date_options").valueJson())).isEqualTo(4);
        assertThat(intValue(sul, "sac.priority_min_age_years")).isEqualTo(60);
        for (String key : List.of("fee.fine_pct", "fee.interest_daily_pct", "meter.average_months",
                "meter.max_estimated_cycles", "cut.min_notice_days", "rec.sla_hours",
                "sac.deadline_counting_rule", "leak.max_discount_pct_water")) {
            assertThat(sul.value(key).confirmation()).as(key)
                    .isEqualTo(Confirmation.REQUIRES_TENANT_VALUE);
            assertThat(sul.value(key).valueJson()).as(key).isNull();
        }
        JsonNode adoption = objectValue(sul, "reg.ana_nr_adoption");
        assertThat(adoption.get("NR3").get("status").asText())
                .isEqualTo("ATENDIDA_LISTA_ANA_2025");
        assertThat(sul.divergences()).hasSize(1);
        assertThat(sul.divergences().get(0).ruleCode()).isEqualTo("CUT_FRIDAY_NOON");
    }

    @Test
    @DisplayName("Todo perfil carrega o fato normativo da verificação da NR 11 em 20/05/2027 (Res. ANA 230/2024, art. 74, §3º)")
    void everyProfileCarriesNr11VerificationFact() {
        for (RegulatoryProfileDef profile : ScRegulatoryProfiles.all()) {
            ProfileValue facts = profile.value("reg.normative_facts");
            assertThat(facts).as(profile.profileKey()).isNotNull();
            JsonNode fact = Json.readTree(facts.valueJson()).get(0);
            assertThat(fact.get("code").asText()).isEqualTo("ANA_NR11_VERIFICATION_FROM");
            assertThat(fact.get("date").asText()).isEqualTo("2027-05-20");
            assertThat(fact.get("source").asText()).contains("230/2024");
            assertThat(profile.normativeFacts()).hasSize(1);
            assertThat(profile.normativeFacts().get(0).date())
                    .isEqualTo(java.time.LocalDate.of(2027, 5, 20));
        }
    }

    @Test
    @DisplayName("Todo valor confirmado traz ato de origem; prazo traz a base de contagem marcada")
    void provenanceAndDeadlineBasisOnEveryValue() {
        for (RegulatoryProfileDef profile : ScRegulatoryProfiles.all()) {
            for (ProfileValue value : profile.values()) {
                if (value.confirmation() == Confirmation.CONFIRMED) {
                    assertThat(value.sourceAct()).as("%s/%s", profile.profileKey(),
                            value.definitionKey()).isNotBlank();
                }
                assertThat(value.deadlineBasis()).as("%s/%s", profile.profileKey(),
                        value.definitionKey()).isNotNull();
            }
        }
        // As tres contagens do acceptance: ARIS corridos, ARESC uteis, AGIR nao definido.
        assertThat(profile("ARIS_RN19_2019").value("sac.deadline_counting_rule").deadlineBasis())
                .isEqualTo(DeadlineBasis.CALENDAR_DAYS);
        assertThat(profile("ARESC_RES046_2016").value("sac.deadline_counting_rule").deadlineBasis())
                .isEqualTo(DeadlineBasis.BUSINESS_DAYS);
        assertThat(profile("AGIR_RN001_2013").value("sac.deadline_counting_rule").deadlineBasis())
                .isEqualTo(DeadlineBasis.NOT_DEFINED);
    }
}
