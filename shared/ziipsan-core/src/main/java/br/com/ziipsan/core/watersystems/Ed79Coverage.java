package br.com.ziipsan.core.watersystems;

import java.util.List;

/**
 * Cobertura do aceite da ED-79, item a item (padrao das entregas-irma):
 * o que o nucleo entrega, o que e porta para a fiacao e o que ficou
 * pendente nomeado — sem ✅ onde e porta ou pendencia.
 */
public final class Ed79Coverage {
    private Ed79Coverage() {}

    public record CoverageItem(String acceptanceItem, String implementation, String status) {}

    public static final String NUCLEO = "ENTREGUE_NO_NUCLEO";
    public static final String PORTA = "PORTA_SEM_FIACAO";
    public static final String PENDENTE = "PENDENTE_NOMEADO";

    public static List<CoverageItem> items() {
        return List.of(
                new CoverageItem(
                        "entidade de sistema de abastecimento e de esgoto com componentes, populacao atendida, manancial e tratamento",
                        "WaterSystem + SystemComponent + SystemRegistryService (validacoes de forma no record)",
                        NUCLEO),
                new CoverageItem(
                        "sobre o catalogo da P1.27",
                        "SystemsPorts.FactoryCatalogPort (typeExists/typeActive); tipos sao chaves referenciadas, nunca criados aqui",
                        PORTA),
                new CoverageItem(
                        "e as feicoes da ED-53",
                        "SystemsPorts.GisFeaturePort (featureExists) + codigos de unidade de tratamento da ED-53 no TreatmentInfo",
                        PORTA),
                new CoverageItem(
                        "Plano de Seguranca da Agua ligado aos riscos e ao registro de incidente operacional e de resiliencia climatica da P3.31 (GES-20)",
                        "WaterSafetyPlan + WaterSafetyPlanService: HazardLink para RiskRecord REAL da P3.31; PlanCoverage por categoria (OPERATIONAL com occurrenceId; CLIMATE_RESILIENCE)",
                        NUCLEO),
                new CoverageItem(
                        "relatorio anual de qualidade da agua a cada ligacao (Decreto 5.440/2005, art. 3o, III)",
                        "AnnualQualityReport (por consumerUnitId) + AnnualReportBuilder (READY so com os 12 meses; faltante nomeado)",
                        NUCLEO),
                new CoverageItem(
                        "com os resultados da ED-50",
                        "MonthlyQualitySummary.fromClosingLine sobre MonthlyClosing.ParameterLine REAL da ED-50",
                        NUCLEO),
                new CoverageItem(
                        "o bloco de qualidade da P2.24",
                        "MonthlyBlockLine.fromBillBlockLine transporta a linha do bloco sem remontar; a montagem segue da P2.24",
                        NUCLEO),
                new CoverageItem(
                        "e a limpeza e desinfeccao de reservatorios da ED-89 (OPE-07)",
                        "CleaningLine.fromPublicRecord sobre ChemReports.PublicCleaningRecord REAL da ED-89 (so com laudo)",
                        NUCLEO),
                new CoverageItem(
                        "Carta de Servicos (Lei 13.460/2017) publicada pela P3.20",
                        "ServiceCharter + ServiceCharterService: publicacao so via SystemsPorts.PortalPublicationPort, com a referencia da P3.20 gravada",
                        NUCLEO),
                new CoverageItem(
                        "envio do relatorio e mensagens legais da fatura pelo motor de mensagens legais com prova de emissao da P2.32 (CAD-96, RAS-04), sem motor proprio",
                        "AnnualReportIssuanceService via SystemsPorts.LegalMessagePort: sem prova da P2.32 nao ha envio; nenhum motor, catalogo ou texto de mensagem nesta tarefa",
                        PORTA),
                new CoverageItem(
                        "chave do relatorio anual no catalogo da P2.32",
                        "catalogo v1 da P2.32 nao contem a chave; proposta marcada em SystemsPorts.ANNUAL_REPORT_CATALOG_KEY_PROPOSED — registro e ato da P2.32 na integracao",
                        PENDENTE),
                new CoverageItem(
                        "e entrega pelas notificacoes da P3.5",
                        "AnnualReportIssuanceService via SystemsPorts.NotificationPort: ISSUED so com a tentativa da P3.5; motivo nomeado gravado quando nao despacha",
                        PORTA),
                new CoverageItem(
                        "LOS servico/conjunto [A DEFINIR]",
                        "NAO modelado nesta tarefa (correcao D3 da revisao, 08/11/2026): nenhuma fonte "
                                + "consultada define o LOS servico/conjunto nem seus valores — nada foi "
                                + "inventado; o item fica MARCADO [A DEFINIR] e nomeado na pendencia "
                                + "P-ED79-10 (registrada pela correcao documental ED-79-CORR-1 e conciliada "
                                + "pela correcao de codigo ED-79-CORR-2): confirmar a exigencia e, se ela "
                                + "existir, modelar ou marcar formalmente como fronteira",
                        PENDENTE));
    }
}
