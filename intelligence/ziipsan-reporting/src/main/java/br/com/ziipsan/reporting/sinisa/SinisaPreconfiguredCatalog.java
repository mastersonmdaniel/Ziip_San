package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorPeriodicity;

import java.util.List;

/**
 * Catalogo SINISA vigente PRE-CONFIGURADO da ED-48, montado EXATAMENTE
 * sobre a semente do mapa da P4.2 ({@code sinisa-catalog-seed.json} do
 * modulo integration: 6 variaveis, todas com {@code officialCode: null}).
 * Nenhuma variavel alem da semente, nenhum codigo de glossario, nenhuma
 * unidade ou formulario alem dos da semente.
 *
 * <p>Escopo honesto: a semente da P4.2 cobre o modulo de AGUA (tecnico,
 * financeiro e balanco). O catalogo SINISA completo de um ciclo vigente —
 * inclusive o modulo de esgoto e os codigos do Glossario de Informacoes —
 * e CARGA de dado oficial na integracao (o catalogo da P4.2 e carregavel
 * justamente para isso), nao codigo: quando o glossario do ciclo for
 * carregado, novas entradas entram pelo mesmo {@link SinisaCatalogEntry}
 * sem mudar este desenho. Ate la, presumir variavel ou codigo seria
 * inventar dado oficial — recusado pelo metodo do projeto.
 *
 * <p>Periodicidade ANNUAL em todas: o SINISA e um ciclo anual (P4.2,
 * Portaria MCID 1.069/2025). A apuracao gerencial em outros periodos usa o
 * mesmo registro da P3.41 com a periodicidade que o tenant declarar na sua
 * propria definicao — nao e presumida aqui.
 */
public final class SinisaPreconfiguredCatalog {

    private static final String CONTEXT =
            "Catalogo SINISA pre-configurado da ED-48 sobre o mapa de variaveis da P4.2 (GES-16); "
                    + "valor apurado no registro unico de indicadores da P3.41 (JOR-19).";

    private SinisaPreconfiguredCatalog() {
    }

    public static List<SinisaCatalogEntry> entries() {
        return List.of(
                new SinisaCatalogEntry(
                        "SINISA.AGUA.VOLUME_PRODUZIDO", "AGUA.VOLUME_PRODUZIDO", SinisaPanelForm.TECNICO,
                        "Volume de agua produzido no ano-base",
                        "Volume de agua produzido (volume de entrada do sistema) no ano-base do ciclo SINISA",
                        "m3", IndicatorPeriodicity.ANNUAL, "volume_produzido",
                        List.of(new SinisaVariableRef("volume_produzido", "AGUA.VOLUME_PRODUZIDO",
                                SinisaPanelForm.TECNICO, SinisaVariableRef.SourceKind.METERING)),
                        CONTEXT),
                new SinisaCatalogEntry(
                        "SINISA.AGUA.VOLUME_FATURADO", "AGUA.VOLUME_FATURADO", SinisaPanelForm.TECNICO,
                        "Volume de agua faturado no ano-base",
                        "Volume de agua faturado no ano-base do ciclo SINISA",
                        "m3", IndicatorPeriodicity.ANNUAL, "volume_faturado",
                        List.of(new SinisaVariableRef("volume_faturado", "AGUA.VOLUME_FATURADO",
                                SinisaPanelForm.TECNICO, SinisaVariableRef.SourceKind.METERING)),
                        CONTEXT),
                new SinisaCatalogEntry(
                        "SINISA.AGUA.AGUA_NAO_FATURADA", "AGUA.AGUA_NAO_FATURADA", SinisaPanelForm.TECNICO,
                        "Agua nao faturada (volume de entrada menos volume faturado)",
                        "Agua nao faturada do ano-base: volume de entrada menos volume faturado "
                                + "(balanco hidrico da P4.2; caso da v1: 10.000 - 7.000 = 3.000 m3)",
                        "m3", IndicatorPeriodicity.ANNUAL, "volume_produzido - volume_faturado",
                        List.of(
                                new SinisaVariableRef("volume_produzido", "AGUA.VOLUME_PRODUZIDO",
                                        SinisaPanelForm.TECNICO, SinisaVariableRef.SourceKind.METERING),
                                new SinisaVariableRef("volume_faturado", "AGUA.VOLUME_FATURADO",
                                        SinisaPanelForm.TECNICO, SinisaVariableRef.SourceKind.METERING)),
                        CONTEXT),
                new SinisaCatalogEntry(
                        "SINISA.AGUA.LIGACOES_ATIVAS", "AGUA.LIGACOES_ATIVAS", SinisaPanelForm.TECNICO,
                        "Quantidade de ligacoes ativas de agua no fim do ano-base",
                        "Quantidade de ligacoes ativas de agua em 31/12 do ano-base do ciclo SINISA",
                        "ligacoes", IndicatorPeriodicity.ANNUAL, "ligacoes_ativas",
                        List.of(new SinisaVariableRef("ligacoes_ativas", "AGUA.LIGACOES_ATIVAS",
                                SinisaPanelForm.TECNICO, SinisaVariableRef.SourceKind.REGISTRY)),
                        CONTEXT),
                new SinisaCatalogEntry(
                        "SINISA.FIN.RECEITA_OPERACIONAL_AGUA", "FIN.RECEITA_OPERACIONAL_AGUA",
                        SinisaPanelForm.FINANCEIRO,
                        "Receita operacional direta de abastecimento de agua no ano-base",
                        "Receita operacional direta de abastecimento de agua no ano-base "
                                + "(formulario Receita e Cobranca do Manual oficial)",
                        "R$", IndicatorPeriodicity.ANNUAL, "receita_operacional_agua",
                        List.of(new SinisaVariableRef("receita_operacional_agua",
                                "FIN.RECEITA_OPERACIONAL_AGUA",
                                SinisaPanelForm.FINANCEIRO, SinisaVariableRef.SourceKind.BILLING)),
                        CONTEXT),
                new SinisaCatalogEntry(
                        "SINISA.BALANCO.ATIVO_TOTAL", "BALANCO.ATIVO_TOTAL", SinisaPanelForm.BALANCO,
                        "Ativo total do balanco patrimonial no fim do ano-base",
                        "Ativo total do balanco patrimonial do prestador no fim do ano-base; "
                                + "origem manual justificada no mapa da P4.2 (contabilidade do "
                                + "prestador, modulo da ED-32)",
                        "R$", IndicatorPeriodicity.ANNUAL, "ativo_total",
                        List.of(new SinisaVariableRef("ativo_total", "BALANCO.ATIVO_TOTAL",
                                SinisaPanelForm.BALANCO, SinisaVariableRef.SourceKind.MANUAL)),
                        CONTEXT));
    }

    /** As ligacoes indicador -> variavel do mapa, para um tenant que materializou o catalogo. */
    public static List<SinisaIndicatorLink> links(java.util.UUID tenantId) {
        return entries().stream()
                .map(e -> new SinisaIndicatorLink(tenantId, e.code(), e.sinisaVariableKey(), e.form()))
                .toList();
    }
}
