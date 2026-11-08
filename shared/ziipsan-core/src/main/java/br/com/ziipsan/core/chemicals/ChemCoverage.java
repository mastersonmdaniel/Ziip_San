package br.com.ziipsan.core.chemicals;

import java.util.List;

/**
 * Mapa de cobertura da ED-89 (padrao das classes *Coverage das
 * entregas vizinhas): o que o acceptance pede, onde esta, e o que e
 * EXTERNO/pendencia declarada — registro, nao afirmacao de ensaio.
 */
public final class ChemCoverage {
    private ChemCoverage() {}

    public record Entry(String requirement, String deliveredBy, String status) {}

    public static List<Entry> entries() {
        return List.of(
            new Entry("OPE-06 cadastro de produto quimico com lote, laudo do fornecedor, FDS, validade e local (silo/tanque/cilindro) sobre a ED-58",
                    "ChemProduct + ChemReceipt.LotReceipt + ChemService.registerProduct", "ENTREGUE (nucleo)"),
            new Entry("OPE-06 recebimento por nota fiscal com conferencia",
                    "ChemReceipt.receive sobre NfeEntry REAL da ED-58; divergencia nao posta; idempotente por chave", "ENTREGUE (nucleo)"),
            new Entry("OPE-06 consumo por dia e por m3 tratado; conciliacao teorico (dosagem) x real",
                    "DailyConsumption + ChemReports.dailyConsumption; dosagem declarada por lancamento, sem fonte presumida", "ENTREGUE (nucleo)"),
            new Entry("OPE-06 alerta de estoque minimo (chem.min_stock_days)",
                    "ChemReports.coverage: cobertura em dias pelo consumo medio real; sem fabrica", "ENTREGUE (nucleo)"),
            new Entry("OPE-06 controle opcional PF (Lei 10.357/2001; Portaria MJSP 204/2022 alt. 223/2022): cadastro, licenca com renovacao, informacoes periodicas",
                    "PfControl + ControlledCatalog; prazos/periodos declarados [SEM FONTE]", "ENTREGUE (nucleo)"),
            new Entry("OPE-06 lista chem.controlled_products_list ligada a norma, atualizada pelo Ziip; conferencia do tenant ANTES de ligar",
                    "ControlledCatalog (global, versionado, sem seed) + PfControl.runPreEnableCheck/enable", "ENTREGUE (nucleo); carga da lista = pendencia"),
            new Entry("OPE-07 plano de limpeza/desinfeccao por reservatorio, ops.reservoir_cleaning_interval_months (agencia), OS recorrente, laudo, produto usado, volume descartado",
                    "ReservoirCleaning + ChemPorts.CleaningOrderPort", "ENTREGUE (nucleo)"),
            new Entry("OPE-07 registro publico no portal (Decreto 5.440/2005 Anexo arts. 16-17; Portaria 888/2021 art. 14 XVII); lido pela ED-79 e pela ED-85",
                    "ChemReports.publicCleaningRecords / annualReportSource / discardedVolumes", "ENTREGUE (fontes); portal/ED-79/ED-85 sao dos donos"),
            new Entry("SG-811 (SAMAE 2.26, fatia de insumos/reservatorios remetida da ED-50)",
                    "coberta no nucleo pelos itens OPE-06/OPE-07 acima", "ENTREGUE (nucleo); ensaio com roteiro real = pendencia externa"),
            new Entry("Item 32 das decisoes do dono (arquitetura tarifaria-alvo)",
                    "a ED-89 nao e consumidora de conta: nenhum preco, tarifa ou item de fatura e criado aqui; o produto usado na limpeza e REFERENCIA ao item da ED-58, sem valor proprio", "RESPEITADO (nenhuma cobranca criada)")
        );
    }
}
