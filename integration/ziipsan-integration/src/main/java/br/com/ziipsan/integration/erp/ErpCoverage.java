package br.com.ziipsan.integration.erp;

import java.util.List;

/**
 * ED-62: registro de cobertura do aceite (padrao das entregas vizinhas).
 * Cada linha liga a exigencia a implementacao desta entrega OU a
 * pendencia externa nomeada — ensaio contra fornecedor real NAO e
 * afirmado aqui (pendencia B1 do relatorio).
 */
public final class ErpCoverage {
    private ErpCoverage() {}

    public record CoverageItem(String requirement, String implementation) {}

    public static List<CoverageItem> items() {
        return List.of(
            new CoverageItem("GES-23 (finding dono): integracao ERP testavel",
                    "ErpConnector sobre ExternalConnector da P3.37 + ErpExportService com recusas nomeadas; nucleo provado com duble honesto (FakeErpTransport em testes)"),
            new CoverageItem("Conectores Betha/IPM/Fiorilli por API ou arquivo sobre o hub P3.37 e a API publica P2.26",
                    "ErpProvider + ErpConnector (kind ERP); canal API/FILE e parametro declarado erp.<p>.transport; nome do conector segue o catalogo de parceiros (GES-11/P2.26)"),
            new CoverageItem("Testado contra leiaute publicado ou ambiente do fornecedor",
                    "PENDENCIA EXTERNA B1: nenhum leiaute de Betha/IPM/Fiorilli esta nas fontes locais; leiaute entra como ErpLayoutMapping declarado e a prova em homologacao do fornecedor fecha na implantacao do piloto"),
            new CoverageItem("Previsao de materiais da OS ao estoque do cliente (SG-476, TR-521)",
                    "ErpExportService.sendMaterialForecast sobre PlannedMaterials.Line REAL da ED-36"),
            new CoverageItem("Movimentos exportados quando o estoque oficial for o do ERP (ADR-0032)",
                    "ErpExportService.exportStockMovements com porta erp.<p>.stock_official; com ZIIPSAN a ED-58 segue dona e a exportacao recusa"),
            new CoverageItem("Lancamentos contabeis da ED-32 exportados pelo conector",
                    "ErpExportService.exportAccounting sobre JournalEntry REAL da ED-32 (linhas D e C)"),
            new CoverageItem("Indicadores compras/RH/orcamento LIDOS para o painel, sem construir os modulos (ADR-0028 i)",
                    "ErpIndicators (somente leitura); nenhum modulo de compras/RH/orcamento criado"),
            new CoverageItem("Centro de custo compativel com a unidade organizacional da P3.30",
                    "ErpHooks.CostCenterLink por OrgUnit.code + campo centro_custo na carga contabil"),
            new CoverageItem("Natureza de despesa nos materiais da ED-58",
                    "ErpHooks.MaterialErpHook exigido na previsao e nos movimentos; ausencia = recusa nomeada"),
            new CoverageItem("Empenho/ordem de compra externa e numero PNCP nos contratos de terceiros (incl. empreiteiras ED-37)",
                    "ErpHooks.ThirdPartyContractHook + ErpPayloads.contractHookLine (a ED-37 remete estes campos a ED-62)"),
            new CoverageItem("SAMAE 2.16.18 e 2.17 sem falha na parte de integracao",
                    "Parte de integracao entregue (estoque/contabilidade via ERP); o ENSAIO SAMAE e externo — nao afirmado (pendencia B2)"),
            new CoverageItem("Indicadores disponiveis para a alinea t do roteiro VISAN",
                    "ErpIndicators.visanItemT (mesma leitura do painel, sem calculo novo)"),
            new CoverageItem("Cruzamento com cadastro imobiliario municipal",
                    "FORA desta entrega por desenho do aceite (ED-95) — nada implementado")
        );
    }
}
