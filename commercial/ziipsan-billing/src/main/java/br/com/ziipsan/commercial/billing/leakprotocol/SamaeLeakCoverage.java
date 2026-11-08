package br.com.ziipsan.commercial.billing.leakprotocol;

import java.util.List;

/**
 * Cobertura dos itens 2.12 e 2.12.5 do SAMAE no recorte da ED-23
 * (revisao de consumo e desconto por vazamento), no mesmo desenho do
 * {@code SamaeFieldCoverage} da ED-20: cada exigencia aponta a peca
 * desta entrega (ou da dependencia dona) que a atende.
 *
 * <p>NENHUM codigo SG e inventado aqui: o mapa e por exigencia do
 * aceite, e a conferencia dos codigos SG contra o roteiro SAMAE e a
 * pendencia P-ED23-04 (padrao ED-30/ED-20). A fatia 2.12.5 de execucao
 * em campo (janela, aviso, fotos, aceite) e da ED-20 e aqui apenas
 * referenciada onde a vistoria a consome.
 */
public final class SamaeLeakCoverage {

    private SamaeLeakCoverage() {
    }

    /** Uma linha de cobertura: item do SAMAE, exigencia e onde ela e atendida. */
    public record Entry(String item, String requirement, String coveredBy) {
    }

    public static List<Entry> entries() {
        return List.of(
                new Entry("2.12", "Revisao de consumo por vazamento com protocolo em fases",
                        "LeakReviewProtocol (OPENED..CLOSED_GRANTED/CLOSED_DENIED) sobre o pedido da P3.28"),
                new Entry("2.12", "Desconto por vazamento pelas reguas e parametros do tenant (leak.*)",
                        "P3.28 (LeakDiscountRule/LeakMeterParameters, perfis da P2.31) -- consumido, nao duplicado"),
                new Entry("2.12", "Fatura alterada pela revisao somente por retificacao formal",
                        "LeakDecisionFlow -> RectificationPort -> P2.20 (BillMaintenanceService.rectify)"),
                new Entry("2.12", "Conta em revisao fora de corte durante a revisao",
                        "P2.20 ReviewSuspensionRule sobre as reguas da P3.4 -- consultado por CutoffSuspensionPort"),
                new Entry("2.12.5", "Vistoria e laudo no celular, sem papel",
                        "FieldInspection + LeakReport sobre a execucao de campo da ED-20 (fotos e identidade dela)"),
                new Entry("2.12.5", "Laudo e decisao assinados (Lei 14.063/2020)",
                        "LeakDecisionFlow -> SigningPort -> P3.34 (LAB_REPORT e SERVICE_ORDER_ACCEPTANCE)"),
                new Entry("2.12.5", "Comunicacao das fases ao usuario",
                        "NotificationPort -> P3.5/ED-41 (modelos e canais delas)"));
    }
}
