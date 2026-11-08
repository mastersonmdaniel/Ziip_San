package br.com.ziipsan.reporting.sheet;

import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;
import br.com.ziipsan.reporting.sheet.SheetCatalogEntry.DataOrigin;
import br.com.ziipsan.reporting.sheet.SheetCatalogEntry.SheetVariable;

import java.util.List;

/**
 * Catalogo de fabrica das fichas da ED-77 - SOMENTE o que tem fonte no
 * acceptance ou nas deps executadas:
 * <ul>
 *   <li>IAA e ICA por municipio sobre a base do IBGE (texto do acceptance);</li>
 *   <li>NR 9 I-04 (intermitencia) e I-05 (extravasamento/km), cujas bases a
 *       ED-92 entrega em {@code IncidentIndicators} (OPE-16);</li>
 *   <li>energia/emissoes/pessoal como ficha de entrada manual de confianca
 *       baixa (GES-21), substituida pela fonte automatica da ED-90.</li>
 * </ul>
 * As fichas das agencias de SC (ARIS RN 57/2026, AGIR RN 025/2026 e ARIS
 * RN 36/2022 - RAS-07) constam como PERFIS com o conteudo [A DEFINIR]:
 * as resolucoes sao nomeadas no acceptance, mas suas fichas (variavel,
 * formula e unidade de cada uma) nao estao nesta VM nem sao reproduziveis
 * sem o texto conferido - inventa-las violaria a regra de nenhum dado
 * inventado. Pendencia P-ED77-01. Idem o detalhamento clausula a clausula
 * das ISO 24510:2024/24512:2024 e dos niveis I/II da NR 9 alem de I-04/I-05.
 */
public final class FactorySheetCatalog {

    private static final String CONTEXT =
            "Ficha da ED-77 sobre o registro unico de indicadores da P3.41 (JOR-19); "
                    + "definicao em palavras proprias, sem reproduzir texto de norma.";

    /** Perfil de agencia SC (RAS-07): referencia com fonte, conteudo por carregar. */
    public record ScSheetProfile(String agencyCode, String normativeReference, String contentStatus) {
    }

    public static final String SC_CONTENT_PENDING =
            "[A DEFINIR] fichas da resolucao nao carregadas: variavel, formula e unidade "
                    + "de cada ficha exigem o texto conferido da resolucao (P-ED77-01)";

    private FactorySheetCatalog() {
    }

    public static List<ScSheetProfile> scProfiles() {
        return List.of(
                new ScSheetProfile("ARIS", "ARIS RN 57/2026", SC_CONTENT_PENDING),
                new ScSheetProfile("AGIR", "AGIR RN 025/2026", SC_CONTENT_PENDING),
                new ScSheetProfile("ARIS", "ARIS RN 36/2022", SC_CONTENT_PENDING));
    }

    public static List<SheetCatalogEntry> entries() {
        return List.of(
                new SheetCatalogEntry(
                        "COBERTURA.IAA", "Indice de atendimento de agua por municipio",
                        SheetFramework.NR9_ANA, SheetLevel.NA,
                        "Percentual de domicilios do municipio atendidos com abastecimento de agua, "
                                + "sobre a base de domicilios do IBGE (calculo nomeado no acceptance da ED-77)",
                        "%", IndicatorPeriodicity.ANNUAL,
                        "domicilios_atendidos_agua * 100 / domicilios_totais",
                        List.of(new SheetVariable("domicilios_atendidos_agua", DataOrigin.REPORT_VIEW_P322),
                                new SheetVariable("domicilios_totais", DataOrigin.IBGE_BASE)),
                        CONTEXT),
                new SheetCatalogEntry(
                        "COBERTURA.ICA", "Indice de coleta de esgoto por municipio",
                        SheetFramework.NR9_ANA, SheetLevel.NA,
                        "Percentual de domicilios do municipio com coleta de esgoto, sobre a base "
                                + "de domicilios do IBGE (calculo nomeado no acceptance da ED-77)",
                        "%", IndicatorPeriodicity.ANNUAL,
                        "domicilios_coleta_esgoto * 100 / domicilios_totais",
                        List.of(new SheetVariable("domicilios_coleta_esgoto", DataOrigin.REPORT_VIEW_P322),
                                new SheetVariable("domicilios_totais", DataOrigin.IBGE_BASE)),
                        CONTEXT),
                new SheetCatalogEntry(
                        "NR9.I04.INTERMITENCIA", "Intermitencia por economias afetadas (NR 9, Nivel I-04)",
                        SheetFramework.NR9_ANA, SheetLevel.LEVEL_I,
                        "Economias afetadas em paralisacoes iguais ou maiores que o limiar do tenant "
                                + "(outage.min_duration_hours da ED-92, padrao 6h, so reduz) sobre o total "
                                + "de economias; base calculada pela ED-92 (IncidentIndicators)",
                        "%", IndicatorPeriodicity.MONTHLY,
                        "economias_afetadas * 100 / economias_totais",
                        List.of(new SheetVariable("economias_afetadas", DataOrigin.NETWORK_INCIDENT_ED92),
                                new SheetVariable("economias_totais", DataOrigin.REPORT_VIEW_P322)),
                        CONTEXT),
                new SheetCatalogEntry(
                        "NR9.I05.EXTRAVASAMENTO", "Extravasamento por quilometro de rede (NR 9, Nivel I-05)",
                        SheetFramework.NR9_ANA, SheetLevel.LEVEL_I,
                        "Ocorrencias de extravasamento por quilometro de rede de esgoto; base da ED-92 "
                                + "(IncidentIndicators.OverflowBase, denominador dos retratos do inicio)",
                        "ocorrencias/km", IndicatorPeriodicity.MONTHLY,
                        "extravasamentos / extensao_rede_km",
                        List.of(new SheetVariable("extravasamentos", DataOrigin.NETWORK_INCIDENT_ED92),
                                new SheetVariable("extensao_rede_km", DataOrigin.NETWORK_INCIDENT_ED92)),
                        CONTEXT),
                new SheetCatalogEntry(
                        "MANUAL.ENERGIA_EMISSOES_PESSOAL", "Energia, emissoes e pessoal (entrada manual)",
                        SheetFramework.ISO_24512, SheetLevel.NA,
                        "Entradas manuais de energia, emissoes e pessoal com grau de confianca BAIXO "
                                + "(GES-21/OPE-08), ate a fonte automatica da ED-90 as substituir; "
                                + "a ficha materializada usa ManualIndicatorInputs.FORCED_GRADE",
                        "declarado pelo tenant", IndicatorPeriodicity.ANNUAL,
                        "valor_manual",
                        List.of(new SheetVariable("valor_manual", DataOrigin.MANUAL_LOW_CONFIDENCE)),
                        CONTEXT));
    }
}
