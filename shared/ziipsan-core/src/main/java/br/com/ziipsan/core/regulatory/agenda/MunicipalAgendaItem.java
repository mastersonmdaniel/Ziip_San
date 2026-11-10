package br.com.ziipsan.core.regulatory.agenda;

import java.util.Objects;

/**
 * MM-06 (decisao item 45): um item da agenda ED-98 com o seu escopo —
 * o {@link AgendaItem} vigente (NAO editado) mais o
 * {@code municipalityIbge} do SQL pendente
 * {@code MM-06__reg_obligation_items_municipality.sql}.
 *
 * <ul>
 *   <li>{@code municipalityIbge == null} = escopo "empresa": o item
 *       unico do tenant, exactamente como a ED-98 gera hoje;</li>
 *   <li>preenchido (7 digitos) = item daquele municipio, gerado a
 *       partir dos perfis regulatorios por municipio do tenant
 *       (P2.31), so nos kinds de {@link MunicipalObligationKinds}.</li>
 * </ul>
 *
 * <p>{@link #scopeKey()} e o espelho em memoria do
 * {@code COALESCE(municipality_ibge, '')} da unicidade refeita do
 * MM-06 (precedente V1_13/MM-02): empresa = {@code ""}, para a chave
 * de unicidade (tenant, kind, periodo, escopo) ser comparavel sem
 * tratar NULL como distinto de si mesmo.</p>
 */
public record MunicipalAgendaItem(AgendaItem item, String municipalityIbge) {

    public MunicipalAgendaItem {
        Objects.requireNonNull(item, "item da agenda e obrigatorio");
        if (municipalityIbge != null && !municipalityIbge.matches("[0-9]{7}"))
            throw new IllegalArgumentException(
                    "municipality_ibge deve ter 7 digitos ou ser null (escopo empresa)");
    }

    /** Item de escopo-empresa (municipio NULL — comportamento vigente da ED-98). */
    public static MunicipalAgendaItem companyScope(AgendaItem item) {
        return new MunicipalAgendaItem(item, null);
    }

    /** Item de um municipio (IBGE de 7 digitos). */
    public static MunicipalAgendaItem municipal(AgendaItem item, String municipalityIbge) {
        return new MunicipalAgendaItem(item, municipalityIbge);
    }

    public boolean isCompanyScope() { return municipalityIbge == null; }

    /** Escopo normalizado, como o COALESCE do SQL: empresa = "". */
    public String scopeKey() { return municipalityIbge != null ? municipalityIbge : ""; }

    /** Chave de unicidade por escopo: (tenant, kind, periodo, escopo normalizado). */
    public String uniquenessKey() {
        return item.tenantId() + "|" + item.kind() + "|" + item.periodRef() + "|" + scopeKey();
    }
}
