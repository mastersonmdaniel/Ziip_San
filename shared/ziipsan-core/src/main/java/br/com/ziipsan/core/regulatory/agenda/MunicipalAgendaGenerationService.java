package br.com.ziipsan.core.regulatory.agenda;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * MM-06 (decisao item 45): geracao da agenda regulatoria POR MUNICIPIO,
 * ao lado do gerador vigente {@link ObligationCalendarService} da ED-98
 * (nenhum ficheiro da ED-98 e editado — este servico DELEGA nele).
 *
 * <p>Semantica:</p>
 * <ul>
 *   <li>a entrada {@code tenantMunicipalities} e o conjunto de IBGEs
 *       dos perfis regulatorios POR MUNICIPIO do tenant
 *       ({@code regulatory_profiles.municipality_ibge}, platform
 *       V1_13/P2.31 — distintos, nao nulos; perfis com IBGE NULL sao
 *       escopo-empresa e nao entram aqui). Quem chama le os perfis;
 *       este servico nao persiste nem inventa municipios: valida o
 *       formato, deduplica e ordena (determinismo);</li>
 *   <li>para cada item gerado pela ED-98 de um kind MUNICIPAL
 *       ({@link MunicipalObligationKinds}), havendo municipios, nascem
 *       UM item por municipio (copia do item com id novo e
 *       {@code municipalityIbge} preenchido) — e NENHUM item de
 *       empresa desse kind/periodo (a unicidade por escopo do SQL
 *       pendente do MM-06 e o que permite a coexistencia);</li>
 *   <li>kinds de EMPRESA geram exactamente como hoje: o proprio item
 *       da ED-98, com municipio NULL;</li>
 *   <li>SEM perfis municipais (lista vazia), o resultado e o
 *       calendario vigente item a item — o tenant comporta-se como
 *       hoje;</li>
 *   <li>{@code unresolvedKinds} passa intacto do gerador vigente:
 *       regra sem norma no catalogo continua nao resolvida, em
 *       qualquer escopo — nada e inventado.</li>
 * </ul>
 *
 * <p>Compatibilidade de consulta: {@link Generation#allItems()} e a
 * consulta SEM filtro de municipio — devolve itens de empresa e
 * municipais coexistindo; para um tenant sem itens municipais, e
 * exactamente o conjunto que a ED-98 devolve hoje.</p>
 */
public final class MunicipalAgendaGenerationService {

    /** Calendario com escopo + os tipos nao resolvidos do gerador vigente. */
    public record Generation(UUID tenantId, int year, List<MunicipalAgendaItem> items,
                             List<ObligationKind> unresolvedKinds) {
        public Generation {
            items = List.copyOf(items);
            unresolvedKinds = List.copyOf(unresolvedKinds);
        }

        /** Consulta sem filtro de municipio: empresa + municipais (coexistencia). */
        public List<MunicipalAgendaItem> allItems() { return items; }

        public List<MunicipalAgendaItem> itemsOf(ObligationKind kind) {
            return items.stream().filter(i -> i.item().kind() == kind).toList();
        }

        /** Recorte de um municipio (IBGE de 7 digitos): so os itens municipais dele. */
        public List<MunicipalAgendaItem> itemsOfMunicipality(String municipalityIbge) {
            return items.stream()
                    .filter(i -> municipalityIbge != null && municipalityIbge.equals(i.municipalityIbge()))
                    .toList();
        }

        /** So os itens de escopo-empresa (municipio NULL). */
        public List<MunicipalAgendaItem> companyItems() {
            return items.stream().filter(MunicipalAgendaItem::isCompanyScope).toList();
        }
    }

    private final ObligationCalendarService calendar;

    public MunicipalAgendaGenerationService(ObligationCalendarService calendar) {
        this.calendar = Objects.requireNonNull(calendar, "gerador vigente da ED-98 e obrigatorio");
    }

    /**
     * Gera o calendario com escopo. {@code tenantMunicipalities}: os
     * IBGEs distintos dos perfis regulatorios por municipio do tenant
     * (P2.31); vazio/nulo = tenant sem perfis municipais.
     */
    public Generation generate(TenantObligationProfile profile, int year,
                               List<String> tenantMunicipalities) {
        Objects.requireNonNull(profile, "perfil do tenant e obrigatorio");
        List<String> municipalities = normalize(tenantMunicipalities);
        ObligationCalendarService.Generation base = calendar.generate(profile, year);
        List<MunicipalAgendaItem> out = new ArrayList<>();
        for (AgendaItem item : base.items()) {
            if (!municipalities.isEmpty() && MunicipalObligationKinds.isMunicipal(item.kind())) {
                for (String ibge : municipalities) {
                    out.add(MunicipalAgendaItem.municipal(copyWithNewId(item), ibge));
                }
            } else {
                out.add(MunicipalAgendaItem.companyScope(item));
            }
        }
        validateNoDuplicateScope(out);
        return new Generation(profile.tenantId(), year, out, base.unresolvedKinds());
    }

    /**
     * Anti-duplicado POR ESCOPO em memoria — o espelho da unicidade
     * refeita do SQL pendente do MM-06 ((tenant, kind, periodo,
     * COALESCE(municipality_ibge, ''))): duplicado de empresa colide;
     * mesmo kind/periodo em municipios distintos coexiste; duplicado
     * municipal colide.
     */
    public static void validateNoDuplicateScope(List<MunicipalAgendaItem> items) {
        Set<String> seen = new LinkedHashSet<>();
        for (MunicipalAgendaItem i : items) {
            if (!seen.add(i.uniquenessKey()))
                throw new IllegalStateException(
                        "duplicado por escopo na agenda: " + i.uniquenessKey()
                                + " (a unicidade uq_reg_obligation_item_period do MM-06 o recusaria)");
        }
    }

    /** Valida formato (7 digitos), deduplica e ordena — nenhum IBGE e inventado. */
    static List<String> normalize(List<String> tenantMunicipalities) {
        if (tenantMunicipalities == null || tenantMunicipalities.isEmpty()) return List.of();
        Set<String> distinct = new LinkedHashSet<>();
        for (String ibge : tenantMunicipalities) {
            if (ibge == null || !ibge.matches("[0-9]{7}"))
                throw new IllegalArgumentException(
                        "municipality_ibge de perfil regulatorio invalido: " + ibge);
            distinct.add(ibge);
        }
        List<String> sorted = new ArrayList<>(distinct);
        sorted.sort(null);
        return List.copyOf(sorted);
    }

    /** Copia municipal: mesmos campos do item gerado, id novo (um item persistivel por municipio). */
    private static AgendaItem copyWithNewId(AgendaItem i) {
        return new AgendaItem(UUID.randomUUID(), i.tenantId(), i.kind(), i.title(),
                i.normCatalogKey(), i.periodRef(), i.dueDate(), i.dueLabel(),
                i.responsibleUserId(), i.evidenceRef(), i.status(), i.manual(),
                i.occurrenceRef(), i.actionPlan(), i.reminderRef(), i.completionNote());
    }
}
