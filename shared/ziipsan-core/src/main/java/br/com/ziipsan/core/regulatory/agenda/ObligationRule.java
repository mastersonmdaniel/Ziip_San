package br.com.ziipsan.core.regulatory.agenda;

import java.time.Month;
import java.util.Objects;
import java.util.Set;

/**
 * ED-98 (GES-05): a regra de uma obrigacao. A regra so existe para o
 * calendario se a norma correspondente for LIDA do catalogo da P3.15
 * (casa de {@link ObligationKind#normToken()} feita pelo gerador); esta
 * classe carrega a periodicidade e a ancora de vencimento.
 *
 * <p>Valores sem fonte NAO se inventam: mes de ancora sem dia (ou
 * ancora nula) significa vencimento {@code [A DEFINIR]} no ponto
 * exato — o item nasce com {@code dueLabel} marcado e sem
 * {@code dueDate} fingida. As unicas ancoras com fonte no aceite sao:
 * declaracao anual de quitacao na fatura de MAIO (mes conhecido, dia
 * [A DEFINIR] — correcao D5: a regra expoe SO o mes; o dia concreto
 * {@code --05-01} sem fonte foi removido do record) e a publicacao do
 * reajuste 30 dias ANTES da data-base do tenant (regra do aceite; a
 * data-base vem do perfil {@code reg.*}).</p>
 *
 * <p>Correcao D6 (REVISAO-ED-98): a periodicidade de cada regra declara
 * a propria fonte em {@code periodicityDefined} — {@code false} marca
 * periodicidade SEM fonte no aceite (NR 15, SISAGUA e o item manual
 * RAS-17, hoje anuais por decisao provisoria), registrada na
 * P-ED98-01 (ampliada) e visivel no rotulo do item gerado. Nenhuma
 * periodicidade nova foi inventada nesta correcao.</p>
 *
 * <p>Correcao D2 (REVISAO-ED-98): RREO/RGF sao relatorios DO ENTE
 * (LC 101/2000) — as regras aplicam-se SO a
 * {@link TenantLegalNature#ADMINISTRACAO_DIRETA_ENTE} E exigem a flag
 * fiscal do perfil ("quando aplicavel" = natureza de ente + flag).
 * Antes, {@code appliesTo} vazio + flag bastavam: concessionaria
 * privada com a flag ligada recebia RREO/RGF (sonda S3b).</p>
 */
public record ObligationRule(ObligationKind kind, Periodicity periodicity,
                             Month annualMonth, Integer daysBeforeBaseDate,
                             Set<TenantLegalNature> appliesTo, boolean requiresFiscalFlag,
                             boolean periodicityDefined) {

    public enum Periodicity { ANNUAL, BIMONTHLY, QUADRIMESTRAL, EVENT_DRIVEN }

    /** Marca textual da periodicidade sem fonte (D6), repetida no rotulo do item. */
    public static final String UNDEFINED_PERIODICITY_MARK =
            "[A DEFINIR] periodicidade anual provisoria sem fonte no aceite";

    public ObligationRule {
        Objects.requireNonNull(kind, "tipo da obrigacao e obrigatorio");
        Objects.requireNonNull(periodicity, "periodicidade e obrigatoria");
        appliesTo = appliesTo == null ? Set.of() : Set.copyOf(appliesTo);
        if (daysBeforeBaseDate != null && daysBeforeBaseDate < 0)
            throw new IllegalArgumentException("antecedencia da data-base nao pode ser negativa");
    }

    /** Aplica-se a esta natureza? Conjunto vazio = todas. A flag fiscal (RREO/RGF) e checada a parte. */
    public boolean appliesTo(TenantLegalNature nature) {
        return appliesTo.isEmpty() || appliesTo.contains(nature);
    }

    /** As regras padrao do aceite (GES-05). Periodicidades fiscais: LC 101/2000 (RREO bimestral, RGF quadrimestral). */
    public static java.util.List<ObligationRule> standardRules() {
        return java.util.List.of(
            // Aceite: ciclo ANUAL do SINISA (periodicidade com fonte).
            new ObligationRule(ObligationKind.SINISA_ANNUAL_CYCLE, Periodicity.ANNUAL, null, null, Set.of(), false, true),
            // Aceite: relatorio ANUAL de tarifa social (periodicidade com fonte).
            new ObligationRule(ObligationKind.SOCIAL_TARIFF_ANNUAL_REPORT, Periodicity.ANNUAL, null, null, Set.of(), false, true),
            // Aceite: declaracao anual de quitacao na fatura de maio — D5: SO o mes; dia [A DEFINIR] (P-ED98-01).
            new ObligationRule(ObligationKind.ANNUAL_DISCHARGE_DECLARATION, Periodicity.ANNUAL,
                    Month.MAY, null, Set.of(), false, true),
            // D6: periodicidade do balanco de perdas NR 15 SEM fonte no aceite — ANNUAL provisorio MARCADO [A DEFINIR] (P-ED98-01).
            new ObligationRule(ObligationKind.WATER_LOSS_BALANCE_NR15, Periodicity.ANNUAL, null, null, Set.of(), false, false),
            // Aceite: remessa BIMESTRAL ao e-Sfinge (IN TC-28/2021) — jurisdicionados ao TC.
            new ObligationRule(ObligationKind.ESFINGE_BIMONTHLY, Periodicity.BIMONTHLY, null, null,
                    TenantLegalNature.PUBLIC_LAW, false, true),
            // D6: periodicidade do SISAGUA SEM fonte no aceite — ANNUAL provisorio MARCADO [A DEFINIR] (P-ED98-01).
            new ObligationRule(ObligationKind.SISAGUA, Periodicity.ANNUAL, null, null, Set.of(), false, false),
            // Aceite: publicacao do reajuste com antecedencia minima de 30 dias da data-base (ciclo da data-base anual do perfil).
            new ObligationRule(ObligationKind.TARIFF_READJUSTMENT_PUBLICATION, Periodicity.ANNUAL,
                    null, 30, Set.of(), false, true),
            // Aceite + LC 101/2000: RREO e RGF DO ENTE quando aplicavel — D2: natureza ADMINISTRACAO_DIRETA_ENTE E flag fiscal.
            new ObligationRule(ObligationKind.RREO, Periodicity.BIMONTHLY, null, null,
                    Set.of(TenantLegalNature.ADMINISTRACAO_DIRETA_ENTE), true, true),
            new ObligationRule(ObligationKind.RGF, Periodicity.QUADRIMESTRAL, null, null,
                    Set.of(TenantLegalNature.ADMINISTRACAO_DIRETA_ENTE), true, true),
            // RAS-17: MTR/CDF/DMR manual enquanto a ED-104 nao for liberada.
            // D6: periodicidade SEM fonte no aceite — ANNUAL provisorio MARCADO [A DEFINIR] (P-ED98-01).
            new ObligationRule(ObligationKind.WASTE_MTR_CDF_DMR_MANUAL, Periodicity.ANNUAL, null, null, Set.of(), false, false)
        );
    }

    /** A regra padrao de um tipo (uso de testes e da fiacao). */
    public static ObligationRule standardRuleFor(ObligationKind kind) {
        return standardRules().stream().filter(r -> r.kind() == kind).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("sem regra padrao para " + kind));
    }
}
