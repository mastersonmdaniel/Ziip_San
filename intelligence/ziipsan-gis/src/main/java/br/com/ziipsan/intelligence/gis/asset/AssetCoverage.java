package br.com.ziipsan.intelligence.gis.asset;

import java.util.List;

/**
 * Registro de cobertura da GIS-19 (ED-56): codigos do aceite ->
 * componente desta entrega. O registro NAO afirma ensaio externo:
 * TR-155, TR-1010 e o item do SAMAE sao conferidos em ensaio do
 * orgao (pendencia nomeada); aqui fica o mapeamento auditavel.
 */
public final class AssetCoverage {

    private AssetCoverage() {
    }

    public record CoverageItem(String code, String component, String note) {
    }

    public static final List<CoverageItem> ITEMS = List.of(
            new CoverageItem("GIS-19", "gis.asset (pacote inteiro)",
                    "Ativos, manutencao de rede e inventario regulatorio sobre a P1.35"),
            new CoverageItem("OPE-09", "ValveHydrantProgram",
                    "Exercicio de valvulas, teste de hidrantes e descarga por criticidade; alimenta a P-ED54-05"),
            new CoverageItem("OPE-12", "CctvInspectionService",
                    "Inspecao por video, grau 1..5, PACP sob confirmacao do padrao; reaproveitado pela drenagem (ED-64)"),
            new CoverageItem("OPE-20", "PmPlanService",
                    "Preventiva de nao lineares por tempo e uso; backlog/MTBF/MTTR; rotas e checklists por parametro"),
            new CoverageItem("OPE-38", "ConditionMaintenance",
                    "Manutencao por condicao (horimetro/corrente/vibracao/temperatura) e pecas criticas"),
            new CoverageItem("GES-14", "ReconciliationService",
                    "Conciliacao GIS x patrimonio exportada a ED-32 com o de-para MCRSB"),
            new CoverageItem("COE-05", "AssetInventoryModel",
                    "Hierarquia de seis niveis da P1.35 validada (pai, nivel, sem ciclo)"),
            new CoverageItem("DAD-26", "AssetLinkRules",
                    "Codigo patrimonial so no ativo; feicao/hidrometro so com FK"),
            new CoverageItem("TR-155", "AssetCoverage",
                    "Registro entregue; ensaio externo pendente (P-ED56-04)"),
            new CoverageItem("TR-1010", "AssetCoverage",
                    "Registro entregue; ensaio externo pendente (P-ED56-04)"),
            new CoverageItem("SG-1180", "AssetCoverage",
                    "Registro entregue; ensaio externo pendente (P-ED56-04)"),
            new CoverageItem("NR 16/2026 (Res. ANA 302/2026)", "McpsbCodes",
                    "Formatos da P1.35; lista nts_sds e vida util dependem da tabela original ([SEM FONTE], P-ED56-01)"),
            new CoverageItem("NR 3/2023", "AssetInventoryModel.AssetRecord",
                    "Bem reversivel exige origem do investimento; data de origem so em bem reversivel"),
            new CoverageItem("GES-02", "AssetCoverage",
                    "Instrumento contratual regulatorio fica na ED-96 (fora desta entrega, como manda o aceite)"));

    public static List<String> codes() {
        return ITEMS.stream().map(CoverageItem::code).toList();
    }
}
