package br.com.ziipsan.core.regulatory;

import java.util.List;

/**
 * Registro de cobertura da ED-96 (GES-02): item do aceite ->
 * componente desta entrega. Fronteiras declaradas: metas por
 * municipio na ED-77; pleito de reequilibrio na P2.34 (GES-13) —
 * registrados aqui como REMISSAO, nao como implementacao.
 */
public final class ContractCoverage {

    private ContractCoverage() {
    }

    public record CoverageItem(String code, String component, String note) {
    }

    public static final List<CoverageItem> ITEMS = List.of(
            new CoverageItem("GES-02", "core.regulatory (pacote inteiro)",
                    "Instrumento contratual regulatorio do prestador (ED-96)"),
            new CoverageItem("CADASTRO-POR-MUNICIPIO-E-SERVICO", "RegulatoryContract.Contract",
                    "Registro por municipio (IBGE 7) e por servico; sobreposicao de vigencia no mesmo recorte recusada"),
            new CoverageItem("NR 8/ANA (aditivos de metas)", "RegulatoryContract.ContractAmendment (GOAL_UPDATE_NR8)",
                    "Aditivo de metas registrado como fato; metas de universalizacao por municipio sao da ED-77"),
            new CoverageItem("NR 5/ANA (matriz de riscos)", "ContractRiskMatrix + RegulatoryContractService.addRiskEntry",
                    "Primeiro uso sobre o registro de riscos da P3.31 (cert_risks); conteudo da matriz e do instrumento [SEM FONTE para conteudo presumido]"),
            new CoverageItem("NR 3/ANA (indenizacao)", "RegulatoryContract.UnamortizedIndemnity",
                    "Referencia e metodo como declarados no instrumento; nenhuma formula presumida"),
            new CoverageItem("REEQUILIBRIO (clausula)", "RegulatoryContract.RebalancingClause",
                    "So a clausula e registrada; o pleito e da P2.34 (GES-13), fora desta entrega"),
            new CoverageItem("BENS REVERSIVEIS (ED-56)", "RegulatoryContractService.linkReversibleAsset",
                    "Vinculo so com ativo existente e reversivel do inventario da ED-56; so o id viaja (DAD-26)"),
            new CoverageItem("Decreto 11.598/2023", "FinancialCapacity",
                    "Quatro indicadores com os limiares do aceite, da exportacao do ERP; painel so sob contrato"),
            new CoverageItem("reg.contract_type / reg.risk_matrix_required", "ContractParameters",
                    "Fatos declarados, classe d, REG; ausencia anotada na completude, nunca presumida"));

    public static List<String> codes() {
        return ITEMS.stream().map(CoverageItem::code).toList();
    }
}
