package br.com.ziipsan.commercial.billing.altsolution;

import br.com.ziipsan.commercial.billing.central.CasualUserUnit;
import br.com.ziipsan.commercial.billing.math.Money;
import br.com.ziipsan.commercial.billing.servicecatalog.PriceTable;
import br.com.ziipsan.commercial.billing.servicecatalog.ServicePriceItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Execução do esgotamento (limpa-fossa) da ED-88: OS com caminhão e roteiro,
 * ticket de descarga na ETE com volume, preço pelo catálogo da ED-34 e tarifa
 * pela fonte da agência ({@code sewer.alt_solution_tariff_source}); usuário
 * avulso pela ED-22 ({@link CasualUserUnit}).
 *
 * <p>Regras:
 * <ul>
 *   <li>sem serviço habilitado ({@code sewer.alt_solution_service_enabled}
 *       falso), NÃO há execução — a solução é só cadastrada;</li>
 *   <li>solução desativada pela ligação não é esgotada;</li>
 *   <li>o ticket de descarga é obrigatório e o volume descarregado na ETE
 *       tem de ser o volume coletado: o ticket é a prova da descarga; volume
 *       divergente é recusado (divergência real exige OS de ocorrência, não
 *       lançamento silencioso — decisão de desenho declarada no relatório);</li>
 *   <li>o preço vem SEMPRE da {@link PriceTable} da ED-34 vigente na data da
 *       execução, com a mesma trava de homologação do
 *       {@code ServiceChargeOnClose} da ED-34; a fonte tarifária da agência é
 *       registrada no lançamento, não altera o preço resolvido aqui;</li>
 *   <li>avulso (ED-22): tipo {@code CASUAL} não recebe fatura regular —
 *       o lançamento sai com rota de guia avulsa; centralizador sai como
 *       agrupadora; regular sai na fatura.</li>
 * </ul>
 */
public final class CleaningServiceExecution {

    /** OS de esgotamento: caminhão identificado e posição no roteiro. */
    public record CleaningOrder(UUID orderId, UUID altSolutionId, String truckId, int routeSequence,
                                LocalDate scheduledOn) {
        public CleaningOrder {
            Objects.requireNonNull(orderId, "OS de esgotamento e obrigatoria");
            Objects.requireNonNull(altSolutionId, "solucao individual da OS e obrigatoria");
            if (truckId == null || truckId.isBlank())
                throw new IllegalArgumentException("OS de esgotamento exige o caminhao identificado");
            if (routeSequence < 1)
                throw new IllegalArgumentException("posicao no roteiro deve ser ao menos 1: " + routeSequence);
            Objects.requireNonNull(scheduledOn, "data agendada da OS e obrigatoria");
        }
    }

    /** Ticket de descarga na ETE, com o volume descarregado. */
    public record DischargeTicket(String ticketCode, String treatmentPlantId, BigDecimal dischargedVolumeLiters,
                                  LocalDate dischargedOn) {
        public DischargeTicket {
            if (ticketCode == null || ticketCode.isBlank())
                throw new IllegalArgumentException("ticket de descarga exige codigo");
            if (treatmentPlantId == null || treatmentPlantId.isBlank())
                throw new IllegalArgumentException("ticket de descarga exige a ETE identificada");
            Objects.requireNonNull(dischargedVolumeLiters, "volume do ticket de descarga e obrigatorio");
            if (dischargedVolumeLiters.signum() <= 0)
                throw new IllegalArgumentException("volume do ticket de descarga deve ser positivo: "
                        + dischargedVolumeLiters);
            Objects.requireNonNull(dischargedOn, "data da descarga e obrigatoria");
        }
    }

    /** Rota de cobrança do lançamento, conforme o tipo da unidade (ED-22). */
    public enum BillingRoute { REGULAR_BILL, CASUAL_GUIDE, GROUPED_BILL }

    public record CleaningCompletion(String idempotencyKey, UUID serviceOrderId, UUID altSolutionId,
                                     UUID consumerUnitId, String priceItemCode, String tariffSource,
                                     BigDecimal collectedVolumeLiters, BigDecimal amount,
                                     DischargeTicket dischargeTicket, BillingRoute billingRoute,
                                     LocalDate executedOn) {}

    private CleaningServiceExecution() {}

    public static CleaningCompletion complete(AltSolution solution, CleaningOrder order, boolean serviceEnabled,
                                              PriceTable priceTable, String priceItemCode, BigDecimal billedQuantity,
                                              BigDecimal collectedVolumeLiters, DischargeTicket ticket,
                                              String tariffSource, CasualUserUnit.UnitKind unitKind,
                                              LocalDate executedOn) {
        Objects.requireNonNull(solution, "solucao individual e obrigatoria");
        Objects.requireNonNull(order, "OS de esgotamento e obrigatoria");
        Objects.requireNonNull(priceTable, "tabela de precos da ED-34 e obrigatoria");
        Objects.requireNonNull(ticket, "ticket de descarga na ETE e obrigatorio");
        Objects.requireNonNull(executedOn, "data da execucao e obrigatoria");
        Objects.requireNonNull(unitKind, "tipo da unidade (ED-22) e obrigatorio");
        if (!serviceEnabled)
            throw new IllegalStateException("sewer.alt_solution_service_enabled desligado: a solucao e so "
                    + "cadastrada e nao ha servico de esgotamento a executar");
        if (!solution.active())
            throw new IllegalStateException("solucao desativada pela ligacao a rede nao e esgotada: " + solution.id());
        if (!order.altSolutionId().equals(solution.id()))
            throw new IllegalArgumentException("OS " + order.orderId() + " nao e da solucao " + solution.id());
        if (executedOn.isBefore(order.scheduledOn()))
            throw new IllegalArgumentException("execucao anterior a data agendada da OS: " + executedOn);
        if (billedQuantity == null || billedQuantity.signum() <= 0)
            throw new IllegalArgumentException("quantidade faturada do servico deve ser positiva");
        if (collectedVolumeLiters == null || collectedVolumeLiters.signum() <= 0)
            throw new IllegalArgumentException("volume coletado deve ser positivo");
        if (ticket.dischargedVolumeLiters().compareTo(collectedVolumeLiters) != 0)
            throw new IllegalArgumentException("volume do ticket de descarga (" + ticket.dischargedVolumeLiters()
                    + ") difere do volume coletado (" + collectedVolumeLiters
                    + "): o ticket e a prova da descarga na ETE");
        if (ticket.dischargedOn().isBefore(executedOn))
            throw new IllegalArgumentException("descarga na ETE anterior a execucao do esgotamento");
        if (tariffSource == null || tariffSource.isBlank())
            throw new IllegalArgumentException("fonte tarifaria da agencia (sewer.alt_solution_tariff_source) "
                    + "e obrigatoria no lancamento");
        ServicePriceItem item = priceTable.service(priceItemCode, executedOn);
        if (!item.billable())
            throw new IllegalStateException("item " + priceItemCode + " exige homologacao regulatoria "
                    + "sem registro de homologacao: nao faturavel (ED-34)");
        BigDecimal amount = Money.roundHalfUp(item.unitPrice().multiply(billedQuantity));
        BillingRoute route = switch (unitKind) {
            case REGULAR -> BillingRoute.REGULAR_BILL;
            case CASUAL -> BillingRoute.CASUAL_GUIDE;
            case CENTRALIZER -> BillingRoute.GROUPED_BILL;
        };
        return new CleaningCompletion(order.orderId() + ":" + priceItemCode, order.orderId(), solution.id(),
                solution.consumerUnitId(), priceItemCode, tariffSource.trim(), collectedVolumeLiters,
                amount, ticket, route, executedOn);
    }

    /**
     * Item da agenda da ED-98 para o MTR do tipo romaneio, onde exigido
     * (OPE-03): enquanto a ED-104 não for liberada, o MTR NÃO é emitido aqui —
     * nasce apenas o item manual de agenda ligado ao ticket de descarga.
     */
    public record MtrAgendaItem(String itemType, UUID serviceOrderId, String dischargeTicketCode,
                                String status, String note) {}

    public static MtrAgendaItem mtrAgendaItem(CleaningCompletion completion, boolean mtrRequired) {
        Objects.requireNonNull(completion, "execucao do esgotamento e obrigatoria");
        if (!mtrRequired) return null;
        return new MtrAgendaItem("MTR_ROMANEIO", completion.serviceOrderId(),
                completion.dischargeTicket().ticketCode(), "PENDING_ED98_AGENDA",
                "MTR romaneio como item manual da agenda da ED-98 enquanto a ED-104 nao for liberada (OPE-03)");
    }
}
