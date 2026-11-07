package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.Confirmation;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.DeadlineBasis;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.ProfileDivergence.Resolution;

import java.time.LocalDate;
import java.util.List;

/**
 * Perfis regulatórios de fábrica das ERIs de Santa Catarina (P2.31).
 *
 * <p>FONTE ÚNICA dos valores: o mapa de ERIs do projeto
 * ({@code docs/normas/partes/04-mapa-de-eris.md}, §4, §5, §6 e anexos A, B e
 * F) e {@code docs/arquitetura/PARAMETROS-DO-TENANT.md} (grupos G1, G5, G6,
 * G9 e bifurcações). Cada valor cita o ato e o artigo lidos no mapa. Onde o
 * mapa marca NC (não confirmado no texto lido), o valor nasce
 * {@link Confirmation#REQUIRES_TENANT_VALUE}, sem número de fábrica.
 *
 * <p>Este arquivo é a fonte da qual o gerador
 * {@code scripts/regulatory/generate-regulatory-profile-catalog.py} produz
 * a migration repetível {@code R__regulatory_profile_catalog.sql} (mesmo
 * desenho da carga da P1.34: a R__ não se edita à mão). O teste
 * {@code RegulatoryCatalogConsistencyTest} confere os dois lados.
 *
 * <p>Perfis fora de SC ficam sob demanda (ED-97, RAS-31) e NÃO estão aqui.
 */
public final class ScRegulatoryProfiles {

    private ScRegulatoryProfiles() {
    }

    public static List<RegulatoryProfileDef> all() {
        return List.of(aris(), aresc(), agir(), agrTubarao(), cisamMeioOesteRn01(), cisamMeioOesteRn05(),
                cisamSul());
    }

    /** Perfil por chave estável, ou {@code null}. */
    public static RegulatoryProfileDef byKey(String profileKey) {
        for (RegulatoryProfileDef profile : all()) {
            if (profile.profileKey().equals(profileKey)) {
                return profile;
            }
        }
        return null;
    }

    /**
     * A versão do perfil de uma agência vigente numa data (troca de perfil
     * na data de vigência do ato, com relógio simulado no teste).
     */
    public static RegulatoryProfileDef versionOn(String agencyCode, LocalDate date) {
        RegulatoryProfileDef found = null;
        for (RegulatoryProfileDef profile : all()) {
            if (profile.agencyCode().equals(agencyCode) && profile.covers(date)) {
                if (found != null) {
                    throw new IllegalStateException("Duas versões de perfil cobrem " + date
                            + " para " + agencyCode + ": " + found.profileKey() + " e "
                            + profile.profileKey());
                }
                found = profile;
            }
        }
        return found;
    }

    // =====================================================================
    // ARIS — RN 19/2019 compilada (RN 20/2019, 38/2023, 40/2024 e 55/2026)
    // Mapa de ERIs, anexo A (texto lido, marca C). Contagem: dias corridos.
    // =====================================================================
    private static RegulatoryProfileDef aris() {
        return RegulatoryProfileDef.builder("ARIS_RN19_2019", "ARIS", "ARIS - Agência Reguladora Intermunicipal de Saneamento", "RN 19/2019 compilada", LocalDate.of(2019, 3, 27), null)
                .value("reg.regulator", "\"ARIS\"", "RN 19/2019", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("reg.general_conditions_act", "{\"act\":\"RN 19/2019\",\"date\":\"2019-03-27\",\"compiledWith\":[\"RN 20/2019\",\"RN 38/2023\",\"RN 40/2024\",\"RN 55/2026\"]}", "RN 19/2019 compilada", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("sac.deadline_counting_rule", "\"CALENDAR\"", "RN 19/2019", "art. 171 (redação da RN 38/2023)", DeadlineBasis.CALENDAR_DAYS, Confirmation.CONFIRMED, "Dias corridos, salvo previsão expressa de dias úteis.")
                .value("meter.average_months", "6", "RN 19/2019", "art. 83", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("meter.max_estimated_cycles", "3", "RN 19/2019", "art. 83", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("meter.after_cap_rule", "\"BILL_MINIMUM\"", "RN 19/2019", "art. 83, §§1º, 2º, 4º e 5º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Depois do teto, fatura-se pelo consumo mínimo sem compensação futura; se o impedimento é do usuário, mantém-se a média até corrigir.")
                .value("meter.reading_cycle_days", "{\"minDays\":27,\"maxDays\":33,\"exceptionalMinDays\":15,\"exceptionalMaxDays\":47}", "RN 19/2019", "art. 84", DeadlineBasis.CALENDAR_DAYS, Confirmation.CONFIRMED, "Excepcional de 15 a 47 dias com aviso de 30 dias; faturamento inicial entre 15 e 47 dias.")
                .value("bill.due_date_options", "{\"minCount\":6}", "RN 19/2019", "art. 84, §4º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Requisito do catálogo, NÃO valor de versão: a norma confirma o mínimo de seis opções de vencimento ao usuário; os dias concretos (a lista LIST_INT que o billing lê) são escolha do tenant e completam esta chave na ativação (ver RegulatoryLoadGuard.TENANT_COMPLETED_KEYS).")
                .value("bill.min_days_delivery_to_due", "{\"default\":5,\"PUBLIC\":10}", "RN 19/2019", "art. 98, §1º", DeadlineBasis.BUSINESS_DAYS, Confirmation.CONFIRMED, "Formato do consumidor da P2.5 (BillingParameters): mapa por categoria com \"default\"; PUBLIC = categoria Pública. Em desligamento a pedido a antecedência é de 1 dia útil (mesmo art. 98, §1º): não é categoria de consumo do billing e por isso não entra no mapa. Unidade: dias úteis (deadline_basis).")
                .value("fee.fine_pct", "2.00", "RN 19/2019", "art. 108", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("fee.interest_mode", "\"DAILY_RATE_WITH_CAP\"", "RN 19/2019", "art. 108", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Juros simples, sem capitalização (trava do produto, P0.20).")
                .value("fee.interest_daily_pct", "0.033", "RN 19/2019", "art. 108", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Teto: até 0,033% ao dia; o prestador pode adotar menos.")
                .value("fee.correction_index", "\"IPCA\"", "RN 19/2019", "art. 108", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "IPCA (IBGE) ou outro índice previsto na legislação vigente.")
                .value("ret.billing_history_months", "120", "NR 11 (Res. ANA 230/2024)", "art. 22", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal, classe c: só amplia (P0.20, RAS-16).")
                .value("cut.min_notice_days", "30", "RN 19/2019", "art. 153, §§1º e 5º", DeadlineBasis.CALENDAR_DAYS, Confirmation.CONFIRMED, "Aviso por escrito, discriminando as faturas.")
                .value("cut.max_debt_age_days", "120", "RN 19/2019", "art. 153, §2º", DeadlineBasis.CALENDAR_DAYS, Confirmation.CONFIRMED, "Vedado cortar por tarifas vencidas há mais de 120 dias (só a ARIS tem este teto em SC).")
                .value("cut.forbidden_window", "{\"rule\":\"FEDERAL_FLOOR_14_015\"}", "Lei 14.015/2020", "art. 6º, §4º, da Lei 8.987/1995", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal travado: vedado iniciar corte em sexta, sábado, domingo, feriado e véspera. A regra local (art. 158) é divergência, não valor.")
                .value("rec.sla_hours", "{\"normalHours\":48,\"urgentHours\":4,\"urgentOptional\":true,\"undueHours\":4}", "RN 19/2019", "arts. 160, 161 e 25, III", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Urgência facultativa: se adotada, vale para qualquer usuário, com valores e prazos informados.")
                .value("rec.undue_cut_compensation", "\"RECONNECTION_4H_NO_CHARGE\"", "RN 19/2019", "arts. 25, III, 153, §7º e 107, §6º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Corte indevido: religação em 4 h, sem ônus (não há crédito em dinheiro na ARIS).")
                .value("sac.response_days", "{\"days\":15,\"unit\":\"CALENDAR_DAYS\"}", "RN 19/2019", "art. 23 e §1º", DeadlineBasis.CALENDAR_DAYS, Confirmation.CONFIRMED, null)
                .value("sac.priority_min_age_years", null, "RN 19/2019", "art. 24", DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "O art. 24 prevê atendimento prioritário, mas os trechos lidos no mapa não fixam a idade; sem valor de fábrica.")
                .value("leak.max_discount_pct_water", "70", "RN 19/2019", "art. 89, §§1º a 8º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Desconto de até 70% do volume acima da média; reclamação em até 45 dias da leitura. Consumo pela P3.28.")
                .adoption("NR1", "ADOPTED", "RN 49/2025")
                .adoption("NR3", "ADOPTED", "RN 52/2025")
                .adoption("NR4", "NO_ACT_LOCATED", null)
                .adoption("NR5", "ADOPTED", "RN 44/2025")
                .adoption("NR6", "PARTIAL", "RN 50/2025")
                .adoption("NR7", "ADOPTED", "RN 46/2025")
                .adoption("NR8", "ADOPTED", "RN 36/2022; RN 43/2025; RN 54/2026; RN 42/2025; RN 47/2025")
                .adoption("NR9", "ADOPTED", "RN 57/2026")
                .adoption("NR10", "PARTIAL", "RN 51/2025")
                .adoption("NR11", "PARTIAL", "RN 19/2019; RN 45/2025; RN 47/2025; RN 55/2026")
                .adoption("NR12", "NO_ACT_LOCATED", null)
                .adoption("NR13", "PARTIAL", "RN 56/2026")
                .adoption("NR14", "NO_ACT_LOCATED", null)
                .adoption("NR15", "NO_ACT_LOCATED", null)
                .adoption("NR16", "NO_ACT_LOCATED", null)
                .fact("ANA_NR11_VERIFICATION_FROM", LocalDate.of(2027, 5, 20), "Res. ANA 230/2024 (NR 11), art. 74, §3º")
                .divergence("CUT_FRIDAY_NOON", "Interrupção permitida até as 12 h de sexta-feira e na véspera de feriados", "RN 19/2019, art. 158", "Lei 14.015/2020", Resolution.FEDERAL_FLOOR_APPLIED, "A regra local não menciona sábado, domingo e o próprio feriado; vale o piso federal integral.")
                .note("Tarifa social da ARIS: RN 56/2026 (50% até 15 m³), citada no ato de condições gerais do perfil; o detalhamento vive nas tabelas do billing (V4_2), fora das chaves G1/G5/G6/G9 desta carga.")
                .build();
    }

    // =====================================================================
    // ARESC — Res. 046/2016 consolidada (Res. 139/2019 e 259/2023)
    // Mapa de ERIs, anexo B §2 (texto lido, marca C). Contagem: dias úteis.
    // =====================================================================
    private static RegulatoryProfileDef aresc() {
        return RegulatoryProfileDef.builder("ARESC_RES046_2016", "ARESC", "ARESC - Agência de Regulação de Serviços Públicos de Santa Catarina", "Res. 046/2016 consolidada", LocalDate.of(2016, 1, 19), null)
                .value("reg.regulator", "\"ARESC\"", "Res. 046/2016", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("reg.general_conditions_act", "{\"act\":\"Res. 046/2016\",\"date\":\"2016-01-19\",\"compiledWith\":[\"Res. 139/2019\",\"Res. 259/2023\"]}", "Res. 046/2016 consolidada", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("sac.deadline_counting_rule", "\"BUSINESS\"", "Res. 046/2016", "art. 153", DeadlineBasis.BUSINESS_DAYS, Confirmation.CONFIRMED, "Dias úteis: exclui o dia do início, inclui o do vencimento; começa e termina em dia útil.")
                .value("meter.average_months", "6", "Res. 046/2016", "art. 90", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("meter.max_estimated_cycles", "3", "Res. 046/2016", "art. 90", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("meter.after_cap_rule", "\"SETTLE_BALANCE_IN_3_INSTALLMENTS\"", "Res. 046/2016", "art. 90, §4º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Depois do teto, o saldo positivo é parcelado em 3 vezes pela faixa tarifária anterior; se o impedimento é do usuário, estimativa sem compensação (§5º).")
                .value("meter.reading_cycle_days", "{\"minDays\":27,\"maxDays\":33,\"exceptionalMinDays\":15,\"exceptionalMaxDays\":47}", "Res. 046/2016", "art. 91", DeadlineBasis.CALENDAR_DAYS, Confirmation.CONFIRMED, "Faixa em dias corridos de duração do ciclo (27 a 33): não é prazo contado pela regra do art. 153, por isso a base é CALENDAR_DAYS (correção pós-revisão).")
                .value("bill.due_date_options", "{\"minCount\":6}", "Res. 046/2016", "art. 103", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Requisito do catálogo, NÃO valor de versão: mínimo de seis datas de vencimento confirmado; os dias concretos (a lista LIST_INT que o billing lê) são escolha do tenant e completam esta chave na ativação (ver RegulatoryLoadGuard.TENANT_COMPLETED_KEYS).")
                .value("bill.min_days_delivery_to_due", "{\"default\":5,\"PUBLIC\":10}", "Res. 046/2016", "art. 100, §1º", DeadlineBasis.BUSINESS_DAYS, Confirmation.CONFIRMED, "Formato do consumidor da P2.5 (BillingParameters): mapa por categoria com \"default\"; PUBLIC = categoria Pública. Em desligamento a pedido a antecedência é de 1 dia útil (mesmo art. 100, §1º): não é categoria de consumo do billing e por isso não entra no mapa. Unidade: dias úteis (deadline_basis).")
                .value("fee.fine_pct", "2.00", "Res. 046/2016", "art. 104", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("fee.interest_mode", "\"DAILY_RATE_WITH_CAP\"", "Res. 046/2016", "art. 104", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Juros simples, sem capitalização (trava do produto, P0.20).")
                .value("fee.interest_daily_pct", "0.033", "Res. 046/2016", "art. 104", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Teto: até 0,033% ao dia.")
                .value("fee.correction_index", "\"IPCA\"", "Res. 046/2016", "art. 104", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "IPCA/IBGE ou outro índice previsto em lei.")
                .value("ret.billing_history_months", "120", "NR 11 (Res. ANA 230/2024)", "art. 22", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal, classe c: só amplia (P0.20, RAS-16).")
                .value("cut.min_notice_days", "30", "Res. 046/2016", "art. 79, §§1º, 2º e 5º", DeadlineBasis.BUSINESS_DAYS, Confirmation.CONFIRMED, null)
                .value("cut.max_debt_age_days", null, "Res. 046/2016", "art. 79", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED_ABSENT, "Sem teto de idade da dívida para cortar no texto lido (ausência confirmada, anexo B §7, item 5).")
                .value("cut.forbidden_window", "{\"rule\":\"FEDERAL_FLOOR_14_015\"}", "Lei 14.015/2020", "art. 6º, §4º, da Lei 8.987/1995", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal travado. A Lei SC 16.673/2015, art. 21, p.u., é ainda mais protetiva e consta da divergência.")
                .value("rec.sla_hours", "{\"normalHours\":48,\"urgentHours\":4,\"urgentOptional\":true,\"undueHours\":4}", "Res. 046/2016", "arts. 87, 88 e 79, §§6º e 7º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("rec.undue_cut_compensation", "\"GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION\"", "Res. 046/2016", "art. 79, §§6º e 7º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Crédito do maior entre 2x a religação de urgência e 20% da primeira fatura após a religação.")
                .value("sac.response_days", "{\"phoneBusinessDays\":10,\"writtenDays\":30}", "Res. 046/2016", "arts. 128, §§4º, 6º e 7º, e 129", DeadlineBasis.NOT_DEFINED, Confirmation.CONFIRMED, "Bases MISTAS dentro do objeto, nomeadas nos próprios campos: resposta conclusiva em 10 dias ÚTEIS no canal telefônico (phoneBusinessDays) e resposta escrita em 30 dias (writtenDays) — a fonte não marca os 30 dias da escrita como úteis, por isso a base única do valor fica NOT_DEFINED em vez de BUSINESS_DAYS (correção pós-revisão).")
                .value("sac.priority_min_age_years", "65", "Res. 046/2016", "art. 127, §3º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("leak.max_discount_pct_water", "70", "Res. 046/2016", "art. 97", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Até 70%; 2 faturas em 12 meses; pedido em 30 dias do vencimento. Consumo pela P3.28.")
                .adoption("NR3", "ADOPTED", "Res. 433/2026")
                .adoption("NR5", "ADOPTED", "Res. 432/2026")
                .adoption("NR6", "NO_ACT_LOCATED", null)
                .adoption("NR8", "ADOPTED", "Res. 430/2026; Res. 431/2026")
                .adoption("NR9", "NO_ACT_LOCATED", null)
                .adoption("NR10", "NO_ACT_LOCATED", null)
                .adoption("NR11", "NO_ACT_LOCATED", null)
                .adoption("NR13", "NO_ACT_LOCATED", null)
                .fact("ANA_NR11_VERIFICATION_FROM", LocalDate.of(2027, 5, 20), "Res. ANA 230/2024 (NR 11), art. 74, §3º")
                .divergence("CUT_FRIDAY_NOON", "Interrupção permitida até as 12 h de sexta-feira e na véspera de feriados", "Res. 046/2016, art. 85", "Lei 14.015/2020", Resolution.FEDERAL_FLOOR_APPLIED, "A Lei SC 16.673/2015, art. 21, p.u., veda o corte de sexta a domingo, véspera e feriado: camada estadual mais protetiva, registrada para a P3.4 (RAS-12).")
                .note("Tarifa social da ARESC: Res. 313/2024 (provisória) e Res. 348/2025, anteriores à NR 13; por isso a NR 13 consta sem ato de adoção. Prazos rurais de ligação (4 e 6 dias úteis, arts. 30 e 31) não têm chave nos grupos desta carga e ficam registrados nesta nota.")
                .build();
    }

    // =====================================================================
    // AGIR — RN 001/2013. Mapa de ERIs, anexo B §1 (texto lido, marca C).
    // Contagem de prazos NÃO definida na RN 001/2013.
    // =====================================================================
    private static RegulatoryProfileDef agir() {
        return RegulatoryProfileDef.builder("AGIR_RN001_2013", "AGIR", "AGIR - Agência Intermunicipal de Regulação de Serviços Públicos", "RN 001/2013", LocalDate.of(2013, 8, 30), null)
                .value("reg.regulator", "\"AGIR\"", "RN 001/2013", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("reg.general_conditions_act", "{\"act\":\"RN 001/2013\",\"date\":\"2013-08-30\",\"compiledWith\":[]}", "RN 001/2013", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Sem consolidação e sem alteração localizadas no mapa.")
                .value("sac.deadline_counting_rule", null, "RN 001/2013", null, DeadlineBasis.NOT_DEFINED, Confirmation.REQUIRES_TENANT_VALUE, "A RN 001/2013 não define corridos ou úteis; os prazos da ouvidoria contam em dias úteis pelo CPC, art. 219 (RN 012/2021). O tenant declara a contagem na ativação.")
                .value("meter.average_months", "6", "RN 001/2013", "art. 102", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("meter.max_estimated_cycles", "3", "RN 001/2013", "art. 102", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("meter.after_cap_rule", "\"BILL_MINIMUM\"", "RN 001/2013", "art. 102, §§1º, 2º e 4º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Depois do teto, consumo mínimo sem compensação futura.")
                .value("meter.reading_cycle_days", "{\"minDays\":27,\"maxDays\":33,\"exceptionalMinDays\":15,\"exceptionalMaxDays\":47}", "RN 001/2013", "art. 103", DeadlineBasis.NOT_DEFINED, Confirmation.CONFIRMED, "Excepcional de 15 a 47 dias com aviso por escrito de um ciclo.")
                .value("bill.due_date_options", "{\"minCount\":6}", "RN 001/2013", "art. 128", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Requisito do catálogo, NÃO valor de versão: mínimo de seis datas de vencimento confirmado; os dias concretos (a lista LIST_INT que o billing lê) são escolha do tenant e completam esta chave na ativação (ver RegulatoryLoadGuard.TENANT_COMPLETED_KEYS).")
                .value("bill.min_days_delivery_to_due", "{\"default\":5,\"PUBLIC\":10}", "RN 001/2013", "art. 118, p.u.", DeadlineBasis.BUSINESS_DAYS, Confirmation.CONFIRMED, "Formato do consumidor da P2.5 (BillingParameters): mapa por categoria com \"default\"; PUBLIC = categoria Pública. Em desligamento a pedido a antecedência é de 1 dia útil (mesmo art. 118, p.u.): não é categoria de consumo do billing e por isso não entra no mapa. Unidade: dias úteis (deadline_basis).")
                .value("fee.fine_pct", "2.00", "RN 001/2013", "art. 129", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("fee.interest_mode", "\"DAILY_RATE_WITH_CAP\"", "RN 001/2013", "art. 129", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Juros simples, sem capitalização (trava do produto, P0.20).")
                .value("fee.interest_daily_pct", "0.033", "RN 001/2013", "art. 129", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Teto: até 0,033% por dia.")
                .value("fee.correction_index", "\"IPCA\"", "RN 001/2013", "art. 129", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "IPCA/IBGE ou outro índice previsto em lei.")
                .value("ret.billing_history_months", "120", "NR 11 (Res. ANA 230/2024)", "art. 22", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal, classe c: só amplia (P0.20, RAS-16).")
                .value("cut.min_notice_days", "30", "RN 001/2013", "art. 143, §§1º, 2º e 4º", DeadlineBasis.NOT_DEFINED, Confirmation.CONFIRMED, "Com discriminação das faturas; vedado corte por impedimento de leitura não notificado.")
                .value("cut.max_debt_age_days", null, "RN 001/2013", "art. 47, §1º, IV", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED_ABSENT, "Sem teto de idade para cortar: os 120 dias do art. 47 só condicionam ligação e religação (ausência confirmada, anexo B §7, item 5).")
                .value("cut.forbidden_window", "{\"rule\":\"FEDERAL_FLOOR_14_015\"}", "Lei 14.015/2020", "art. 6º, §4º, da Lei 8.987/1995", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal travado: a regra local (art. 148) é divergência, não valor.")
                .value("rec.sla_hours", "{\"normalHours\":48,\"urgentHours\":4,\"urgentOptional\":true,\"undueHours\":4}", "RN 001/2013", "arts. 151, 152 e 143, §§6º e 7º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("rec.undue_cut_compensation", "\"GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE\"", "RN 001/2013", "art. 143, §§6º e 7º", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Crédito do maior entre 2x a religação de urgência e 20% da última fatura.")
                .value("sac.response_days", "{\"days\":15,\"unit\":\"NOT_DEFINED\"}", "RN 001/2013", "art. 27, §2º, e art. 28", DeadlineBasis.NOT_DEFINED, Confirmation.CONFIRMED, "15 dias, sem unidade definida na RN 001/2013.")
                .value("sac.priority_min_age_years", "60", "Mapa de ERIs, §6, item 4", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Idade confirmada no quadro-síntese do mapa (§6 e anexo B §7, item 13); o artigo da RN 001/2013 não foi discriminado nos trechos lidos.")
                .value("leak.max_discount_pct_water", "70", "RN 001/2013", "art. 107", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Até 70% do volume acima da média; 2 faturas sequenciais em 12 meses; pedido em 30 dias do vencimento. Consumo pela P3.28.")
                .adoption("NR1", "ADOPTED", "RN 018/2025")
                .adoption("NR3", "ADOPTED", "RN 019/2025")
                .adoption("NR5", "ADOPTED", "RN 020/2025")
                .adoption("NR6", "ADOPTED", "RN 021/2025")
                .adoption("NR7", "ADOPTED", "RN 022/2025")
                .adoption("NR8", "ADOPTED", "RN 023/2025; RN 024/2025")
                .adoption("NR9", "ADOPTED", "RN 025/2026")
                .adoption("NR10", "NO_ACT_LOCATED", null)
                .adoption("NR11", "NO_ACT_LOCATED", null)
                .adoption("NR13", "NO_ACT_LOCATED", null)
                .adoption("NR15", "NO_ACT_LOCATED", null)
                .adoption("NR16", "NO_ACT_LOCATED", null)
                .fact("ANA_NR11_VERIFICATION_FROM", LocalDate.of(2027, 5, 20), "Res. ANA 230/2024 (NR 11), art. 74, §3º")
                .divergence("CUT_FRIDAY_NOON", "Interrupção permitida até as 12 h de sexta-feira e na véspera de feriados nacionais, estaduais e municipais", "RN 001/2013, art. 148", "Lei 14.015/2020", Resolution.FEDERAL_FLOOR_APPLIED, "Vale o piso federal integral.")
                .divergence("SOCIAL_BENEFIT_LOSS_AFTER_TWO_CUTS", "Mais de 2 cortes no ano fiscal retiram a categoria social", "RN 001/2013, art. 127, §7º", "Lei 14.898/2024", Resolution.RULE_REJECTED, "Recusada na carga: nenhum valor a substitui; o benefício não se perde por corte. O art. 127, §4º (religação sem taxa após purgação da mora) é compatível e segue valendo.")
                .note("A AGIR não adotou a NR 11 nem a NR 13; a RN 001/2013 segue como condições gerais (mapa, anexo B §5).")
                .build();
    }

    // =====================================================================
    // AGR-Tubarão — Res. 007/2013 e Res. 040/2024. Mapa, anexo B §3 e
    // apêndice B: condições gerais comerciais NÃO localizadas. Quase tudo
    // comercial fica sem valor de fábrica, por desenho do acceptance.
    // =====================================================================
    private static RegulatoryProfileDef agrTubarao() {
        return RegulatoryProfileDef.builder("AGR_TUBARAO_RES007_2013", "AGR_TUBARAO", "AGR-Tubarão - Agência Reguladora de Saneamento de Tubarão", "Res. 007/2013 e Res. 040/2024", null, null)
                .value("reg.regulator", "\"AGR_TUBARAO\"", "Res. 007/2013", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("reg.general_conditions_act", "{\"act\":\"Res. 007/2013\",\"compiledWith\":[\"Res. 040/2024\"],\"note\":\"condicoes gerais comerciais nao localizadas\"}", "Res. 007/2013 e Res. 040/2024", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Res. 007/2013: serviço adequado e indicadores. Res. 040/2024: enquadramento de economias. Tarifa social: Res. 043/2025. As condições comerciais vivem no contrato de concessão 038/2012 e no regulamento da concessionária, não localizados (mapa, pendência 25).")
                .value("sac.deadline_counting_rule", null, null, null, DeadlineBasis.NOT_DEFINED, Confirmation.REQUIRES_TENANT_VALUE, "Contagem não confirmada: sem condições gerais lidas.")
                .value("meter.average_months", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Média sem leitura nas condições não localizadas; exige valor do tenant.")
                .value("meter.max_estimated_cycles", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Teto de ciclos nas condições não localizadas; exige valor do tenant.")
                .value("meter.after_cap_rule", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Regra posterior à média nas condições não localizadas; exige valor do tenant.")
                .value("meter.reading_cycle_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Ciclo de leitura nas condições não localizadas; exige valor do tenant.")
                .value("bill.due_date_options", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Datas de vencimento nas condições não localizadas; exige valor do tenant (para concessionária, o piso federal de 6 segue travado pela P0.20).")
                .value("bill.min_days_delivery_to_due", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Antecedência nas condições não localizadas; exige valor do tenant.")
                .value("fee.fine_pct", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Multa nas condições não localizadas; exige valor do tenant (o padrão de fábrica do produto é 2%, CDC art. 52, §1º, via catálogo da P0.20).")
                .value("fee.interest_mode", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Modo de juros nas condições não localizadas; exige valor do tenant.")
                .value("fee.interest_daily_pct", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Juros nas condições não localizadas; exige valor do tenant.")
                .value("fee.correction_index", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Correção nas condições não localizadas; exige valor do tenant.")
                .value("ret.billing_history_months", "120", "NR 11 (Res. ANA 230/2024)", "art. 22", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal, classe c: vale independentemente do texto da ERI (P0.20, RAS-16).")
                .value("cut.min_notice_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Aviso de corte nas condições não localizadas; exige valor do tenant (o piso federal de 30 dias segue travado, Lei 11.445/2007, art. 40, §2º).")
                .value("cut.max_debt_age_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Idade máxima da dívida nas condições não localizadas; exige valor do tenant.")
                .value("cut.forbidden_window", "{\"rule\":\"FEDERAL_FLOOR_14_015\"}", "Lei 14.015/2020", "art. 6º, §4º, da Lei 8.987/1995", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal travado, válido sem texto local; nenhuma regra local foi localizada, logo nenhuma divergência é registrada.")
                .value("rec.sla_hours", "{\"reconnectionHours\":24}", "Res. 007/2013", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Reparo e religação em 24 h confirmados na Res. 007/2013 (mapa, apêndice B); a discriminação normal/urgência/indevida não consta dos trechos lidos. Ligação em 5 dias úteis, também da Res. 007/2013, não tem chave nos grupos desta carga.")
                .value("rec.undue_cut_compensation", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Compensação por corte indevido nas condições não localizadas; exige valor do tenant.")
                .value("sac.response_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Prazos de resposta nas condições não localizadas; exige valor do tenant.")
                .value("sac.priority_min_age_years", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Idade do atendimento prioritário nas condições não localizadas; exige valor do tenant.")
                .value("leak.max_discount_pct_water", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Vazamento nas condições não localizadas; exige valor do tenant. Consumo pela P3.28.")
                .adoption("NR7", "ADOPTED", "Res. 044/2025")
                .adoption("NR8", "ADOPTED", "Res. 045/2025; Res. 048/2026")
                .adoption("NR9", "ADOPTED", "Res. 049/2026")
                .adoption("NR3", "NO_ACT_LOCATED", null)
                .adoption("NR5", "NO_ACT_LOCATED", null)
                .adoption("NR6", "NO_ACT_LOCATED", null)
                .adoption("NR10", "NO_ACT_LOCATED", null)
                .adoption("NR11", "NO_ACT_LOCATED", null)
                .adoption("NR13", "NO_ACT_LOCATED", null)
                .fact("ANA_NR11_VERIFICATION_FROM", LocalDate.of(2027, 5, 20), "Res. ANA 230/2024 (NR 11), art. 74, §3º")
                .note("Data exata da Res. 007/2013 não localizada no mapa: a vigência do perfil é definida na ativação pelo tenant. NR 3 em Tomada de Subsídios 001/2026, sem ato de adoção (mapa, anexo B §3).")
                .build();
    }

    // =====================================================================
    // CISAM Meio-Oeste — RN 01/2017 (vigor desde 21/12/2017) até 19/05/2027.
    // Mapa, apêndice B (CISAM-REG/CREFISBA). Juros, multa e correção são
    // remetidos à legislação municipal: sem percentuais confirmados.
    // =====================================================================
    private static RegulatoryProfileDef cisamMeioOesteRn01() {
        return RegulatoryProfileDef.builder("CISAM_MO_RN01_2017", "CISAM_MO", "CISAM Meio-Oeste (CISAM-REG/CREFISBA)", "RN 01/2017", LocalDate.of(2017, 12, 21), LocalDate.of(2027, 5, 19))
                .value("reg.regulator", "\"CISAM_MO\"", "RN 01/2017", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("reg.general_conditions_act", "{\"act\":\"RN 01/2017\",\"compiledWith\":[]}", "RN CISAM-REG 01/2017", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Em vigor desde 21/12/2017 até 19/05/2027, quando a RN 05/2026 a revoga.")
                .value("sac.deadline_counting_rule", null, "RN 01/2017", null, DeadlineBasis.NOT_DEFINED, Confirmation.REQUIRES_TENANT_VALUE, "O mapa confirma resposta em 15 dias úteis, mas não uma regra geral de contagem da RN 01/2017; o tenant declara na ativação.")
                .value("meter.average_months", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Média não consta dos trechos lidos da RN 01/2017 no mapa; exige valor do tenant.")
                .value("meter.max_estimated_cycles", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Teto de ciclos não consta dos trechos lidos; exige valor do tenant.")
                .value("meter.after_cap_rule", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Regra posterior não consta dos trechos lidos; exige valor do tenant.")
                .value("meter.reading_cycle_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Ciclo de leitura não consta dos trechos lidos; exige valor do tenant.")
                .value("bill.due_date_options", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Datas de vencimento não constam dos trechos lidos; exige valor do tenant.")
                .value("bill.min_days_delivery_to_due", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Antecedência não consta dos trechos lidos; exige valor do tenant.")
                .value("fee.fine_pct", null, "RN 01/2017", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Juros, multa e correção remetidos à legislação municipal, sem percentuais (confirmada a remissão, não o valor); exige valor do tenant.")
                .value("fee.interest_mode", null, "RN 01/2017", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Remetido à legislação municipal; exige valor do tenant.")
                .value("fee.interest_daily_pct", null, "RN 01/2017", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Remetido à legislação municipal; exige valor do tenant.")
                .value("fee.correction_index", null, "RN 01/2017", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Remetido à legislação municipal; exige valor do tenant.")
                .value("ret.billing_history_months", "120", "NR 11 (Res. ANA 230/2024)", "art. 22", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal, classe c: só amplia (P0.20, RAS-16).")
                .value("cut.min_notice_days", "30", "RN 01/2017", "art. 104", DeadlineBasis.NOT_DEFINED, Confirmation.CONFIRMED, null)
                .value("cut.max_debt_age_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Idade máxima não consta dos trechos lidos; exige valor do tenant.")
                .value("cut.forbidden_window", "{\"rule\":\"FEDERAL_FLOOR_14_015\"}", "Lei 14.015/2020", "art. 6º, §4º, da Lei 8.987/1995", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal travado: a regra local (art. 107) é divergência, não valor.")
                .value("rec.sla_hours", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Prazos de religação da RN 01/2017 não discriminados no mapa; exige valor do tenant.")
                .value("rec.undue_cut_compensation", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Compensação não consta dos trechos lidos; exige valor do tenant.")
                .value("sac.response_days", "{\"days\":15,\"unit\":\"BUSINESS_DAYS\"}", "RN 01/2017", null, DeadlineBasis.BUSINESS_DAYS, Confirmation.CONFIRMED, "Resposta em 15 dias úteis, confirmada no mapa (apêndice B); artigo não discriminado nos trechos lidos.")
                .value("sac.priority_min_age_years", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Idade do atendimento prioritário não consta dos trechos lidos; exige valor do tenant.")
                .value("leak.max_discount_pct_water", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Vazamento não consta dos trechos lidos; exige valor do tenant. Consumo pela P3.28.")
                .fact("ANA_NR11_VERIFICATION_FROM", LocalDate.of(2027, 5, 20), "Res. ANA 230/2024 (NR 11), art. 74, §3º")
                .divergence("CUT_FRIDAY_NOON", "Corte vedado apenas após as 12 horas de sexta e na véspera de feriado", "RN 01/2017, art. 107", "Lei 14.015/2020", Resolution.FEDERAL_FLOOR_APPLIED, "Vale o piso federal integral.")
                .note("Sem mapa de adoção neste perfil: os atos de adoção das NR são de 2025/2026 e constam do perfil sucessor (RN 05/2026). Ligação em 10 ou 15 dias úteis, confirmada no mapa, não tem chave nos grupos desta carga.")
                .build();
    }

    // =====================================================================
    // CISAM Meio-Oeste — RN 05/2026 (NR 11), a partir de 20/05/2027,
    // revogando a RN 01/2017 (troca testada com relógio simulado).
    // =====================================================================
    private static RegulatoryProfileDef cisamMeioOesteRn05() {
        return RegulatoryProfileDef.builder("CISAM_MO_RN05_2026", "CISAM_MO", "CISAM Meio-Oeste (CISAM-REG/CREFISBA)", "RN 05/2026", LocalDate.of(2027, 5, 20), null)
                .value("reg.regulator", "\"CISAM_MO\"", "RN 05/2026", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("reg.general_conditions_act", "{\"act\":\"RN 05/2026\",\"compiledWith\":[]}", "RN 05/2026", "art. 163", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Publicada em 05/10/2026; em vigor a partir de 20/05/2027, revogando a RN 01/2017 (vigência do art. 163, conforme o acceptance da P2.31 e o mapa). Tarifa social: RN 04/2026 (50% até 15 m³, renda até 1/2 salário mínimo com CadÚnico/BPC).")
                .value("sac.deadline_counting_rule", "\"BUSINESS\"", "RN 05/2026", null, DeadlineBasis.BUSINESS_DAYS, Confirmation.CONFIRMED, "Prazos em dias úteis na RN 05/2026 (mapa, apêndice B); artigo não discriminado nos trechos lidos.")
                .value("meter.average_months", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Média não consta dos trechos lidos da RN 05/2026 no mapa; exige valor do tenant.")
                .value("meter.max_estimated_cycles", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Teto de ciclos não consta dos trechos lidos; exige valor do tenant.")
                .value("meter.after_cap_rule", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Regra posterior não consta dos trechos lidos; exige valor do tenant.")
                .value("meter.reading_cycle_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Ciclo de leitura não consta dos trechos lidos; exige valor do tenant.")
                .value("bill.due_date_options", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Datas de vencimento não constam dos trechos lidos; exige valor do tenant.")
                .value("bill.min_days_delivery_to_due", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Antecedência não consta dos trechos lidos; exige valor do tenant.")
                .value("fee.fine_pct", null, "RN 05/2026", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Juros, multa e atualização remetidos à legislação, sem percentuais confirmados; exige valor do tenant.")
                .value("fee.interest_mode", null, "RN 05/2026", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Remetido à legislação; exige valor do tenant.")
                .value("fee.interest_daily_pct", null, "RN 05/2026", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Remetido à legislação; exige valor do tenant.")
                .value("fee.correction_index", null, "RN 05/2026", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Remetido à legislação; exige valor do tenant.")
                .value("ret.billing_history_months", "120", "NR 11 (Res. ANA 230/2024)", "art. 22", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal, classe c: só amplia (P0.20, RAS-16).")
                .value("cut.min_notice_days", "30", "RN 05/2026", null, DeadlineBasis.BUSINESS_DAYS, Confirmation.CONFIRMED, "Aviso de suspensão de 30 dias confirmado no mapa; artigo não discriminado nos trechos lidos.")
                .value("cut.max_debt_age_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Idade máxima não consta dos trechos lidos; exige valor do tenant.")
                .value("cut.forbidden_window", "{\"rule\":\"FEDERAL_FLOOR_14_015\"}", "Lei 14.015/2020", "art. 6º, §4º, da Lei 8.987/1995", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "A RN 05/2026 já veda sábado, domingo, feriado e véspera; a permissão implícita na manhã de sexta segue divergente do piso federal.")
                .value("rec.sla_hours", "{\"confirmedHours\":[12,24,120]}", "RN 05/2026", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Religação de 12/24/120 horas confirmada no mapa; a correspondência com normal/urgência/indevida não é discriminada nos trechos lidos, e o tenant a declara na ativação.")
                .value("rec.undue_cut_compensation", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Compensação não consta dos trechos lidos; exige valor do tenant.")
                .value("sac.response_days", null, "RN 05/2026", null, DeadlineBasis.BUSINESS_DAYS, Confirmation.REQUIRES_TENANT_VALUE, "A RN 05/2026 conta prazos em dias úteis, mas o prazo de resposta não é discriminado no mapa; exige valor do tenant.")
                .value("sac.priority_min_age_years", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Idade do atendimento prioritário não consta dos trechos lidos; exige valor do tenant.")
                .value("leak.max_discount_pct_water", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Vazamento não consta dos trechos lidos; exige valor do tenant. Consumo pela P3.28.")
                .adoption("NR3", "ADOPTED", "RN 01/2025")
                .adoption("NR5", "ADOPTED", "RN 02/2025")
                .adoption("NR6", "ADOPTED", "RN 03/2025")
                .adoption("NR7", "ADOPTED", "RN 04/2025")
                .adoption("NR8", "ADOPTED", "RN 05/2025; RN 06/2025")
                .adoption("NR9", "NO_ACT_LOCATED", null)
                .adoption("NR10", "NO_ACT_LOCATED", null)
                .adoption("NR11", "ADOPTED", "RN 05/2026")
                .adoption("NR13", "ADOPTED", "RN 04/2026")
                .fact("ANA_NR11_VERIFICATION_FROM", LocalDate.of(2027, 5, 20), "Res. ANA 230/2024 (NR 11), art. 74, §3º")
                .divergence("CUT_FRIDAY_MORNING", "Corte vedado aos sábados, domingos, feriados e vésperas, e na sexta apenas após as 12 horas", "RN 05/2026", "Lei 14.015/2020", Resolution.FEDERAL_FLOOR_APPLIED, "A manhã de sexta segue aberta pela regra local e fechada pela lei federal: vale o piso federal.")
                .note("Ligação em 10 dias úteis (urbana) ou 15 (rural) na RN 05/2026, confirmada no mapa, sem chave nos grupos desta carga.")
                .build();
    }

    // =====================================================================
    // CISAM-SUL — RN 03/2017, texto só digitalizado (reconhecimento
    // imperfeito; mapa, pendência 24). Só entram os valores que o quadro
    // de SC do mapa (§6) confirma para o CISAM-SUL; o resto exige tenant.
    // =====================================================================
    private static RegulatoryProfileDef cisamSul() {
        return RegulatoryProfileDef.builder("CISAM_SUL_RN03_2017", "CISAM_SUL", "CISAM-SUL (CREFISBA)", "RN 03/2017", LocalDate.of(2017, 10, 17), null)
                .value("reg.regulator", "\"CISAM_SUL\"", "RN 03/2017", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, null)
                .value("reg.general_conditions_act", "{\"act\":\"RN 03/2017\",\"date\":\"2017-10-17\",\"compiledWith\":[]}", "RN 03/2017", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Texto digitalizado, com reconhecimento imperfeito (mapa, pendência 24): os valores por artigo não foram confirmados nesta carga.")
                .value("sac.deadline_counting_rule", null, "RN 03/2017", null, DeadlineBasis.NOT_DEFINED, Confirmation.REQUIRES_TENANT_VALUE, "Contagem não confirmada no texto digitalizado; a RN 03/2016 (reajuste) também não define. Exige valor do tenant.")
                .value("meter.average_months", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("meter.max_estimated_cycles", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("meter.after_cap_rule", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("meter.reading_cycle_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("bill.due_date_options", "{\"minCount\":4}", "Mapa de ERIs, §6, item 4", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Requisito do catálogo, NÃO valor de versão: mínimo de quatro datas de vencimento (diferença do CISAM-SUL no quadro de SC); artigo não localizado no texto digitalizado. Os dias concretos (a lista LIST_INT que o billing lê) são escolha do tenant e completam esta chave na ativação (ver RegulatoryLoadGuard.TENANT_COMPLETED_KEYS). Para concessionária, o piso federal de 6 segue travado pela P0.20.")
                .value("bill.min_days_delivery_to_due", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("fee.fine_pct", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; no quadro do mapa, juros e multa do CISAM-SUL remetem à legislação municipal e ao contrato. Exige valor do tenant.")
                .value("fee.interest_mode", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("fee.interest_daily_pct", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("fee.correction_index", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("ret.billing_history_months", "120", "NR 11 (Res. ANA 230/2024)", "art. 22", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal, classe c: vale independentemente do texto da ERI (P0.20, RAS-16).")
                .value("cut.min_notice_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant (o piso federal de 30 dias segue travado, Lei 11.445/2007, art. 40, §2º).")
                .value("cut.max_debt_age_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("cut.forbidden_window", "{\"rule\":\"FEDERAL_FLOOR_14_015\"}", "Lei 14.015/2020", "art. 6º, §4º, da Lei 8.987/1995", DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Piso federal travado. O quadro do mapa (§6) registra para o CISAM-SUL a mesma regra local das demais ERIs de SC (até 12 h de sexta), como divergência.")
                .value("rec.sla_hours", null, "Mapa de ERIs, §4.1", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "O mapa registra religação na faixa de 24 h a 120 h para o CISAM-SUL, sem discriminar normal/urgência/indevida no texto digitalizado; a discriminação exige valor do tenant.")
                .value("rec.undue_cut_compensation", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("sac.response_days", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant.")
                .value("sac.priority_min_age_years", "60", "Mapa de ERIs, §6, item 4", null, DeadlineBasis.NOT_APPLICABLE, Confirmation.CONFIRMED, "Atendimento prioritário a partir de 60 anos (quadro de SC do mapa, §6 e anexo B §7, item 13); artigo não localizado no texto digitalizado.")
                .value("leak.max_discount_pct_water", null, null, null, DeadlineBasis.NOT_APPLICABLE, Confirmation.REQUIRES_TENANT_VALUE, "Texto só digitalizado; exige valor do tenant. Consumo pela P3.28.")
                .adoption("NR3", "ATENDIDA_LISTA_ANA_2025", null)
                .adoption("NR5", "ATENDIDA_LISTA_ANA_2025", null)
                .adoption("NR6", "DESOBRIGADA_LISTA_ANA_2025", null)
                .adoption("NR8", "ATENDIDA_LISTA_ANA_2025", null)
                .adoption("NR11", "NO_ACT_LOCATED", null)
                .fact("ANA_NR11_VERIFICATION_FROM", LocalDate.of(2027, 5, 20), "Res. ANA 230/2024 (NR 11), art. 74, §3º")
                .divergence("CUT_FRIDAY_NOON", "Interrupção permitida até as 12 h de sexta-feira (regra registrada no quadro do mapa para o CISAM-SUL)", "Mapa de ERIs, §6 (camada sobre os parâmetros); RN 03/2017 digitalizada, artigo não localizado", "Lei 14.015/2020", Resolution.FEDERAL_FLOOR_APPLIED, "Vale o piso federal integral.")
                .note("Adoção das NR: o CISAM-SUL consta como atendente das NR 3, 5 e 8 na lista positiva da ANA de 2025, sem ato individual de adoção localizado (mapa, anexo B §3 e §5): por isso o status é o da lista, não ADOPTED.")
                .build();
    }
}
