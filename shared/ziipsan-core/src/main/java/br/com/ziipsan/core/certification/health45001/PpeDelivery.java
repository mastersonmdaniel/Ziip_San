package br.com.ziipsan.core.certification.health45001;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * ENTREGA DE EPI pelo almoxarifado da ED-58, com vínculo a trabalhador e
 * função (ED-71).
 *
 * <p>FRONTEIRA com a ED-58: o estoque, a requisição, a reserva e a baixa
 * são da ED-58 ({@code inv_items}, {@code inv_requisitions},
 * {@code inv_lots}); o item de EPI lá já é controlado por lote com
 * validade e EXIGE o número do Certificado de Aprovação (CA) no lote
 * ({@code LotControl} da ED-58). Esta classe NÃO duplica o estoque: é o
 * registro de SST da entrega — quem recebeu (trabalhador), para qual
 * função (código de função do catálogo do tenant — nenhuma lista de EPI
 * por função é presumida aqui), qual item/lote/CA saiu, por qual
 * requisição da ED-58 e com ciência do trabalhador.
 *
 * <p>Regras: CA em branco é recusado (EPI sem CA não é EPI entregue);
 * lote vencido na entrega é recusado e {@link #requireUsableOn} bloqueia
 * o uso depois do vencimento; entrega sem ciência do trabalhador não
 * nasce (a ficha de EPI é o registro assinado).
 */
public record PpeDelivery(Iso45001Activation activation, UUID workerId, String functionCode,
                          String itemCode, String lotCode, String approvalCertificate,
                          int quantity, String requisitionRef, String warehouseLocationCode,
                          UUID deliveredBy, LocalDate deliveredOn, LocalDate lotValidUntil,
                          boolean workerAcknowledged) {

    /** Tetos compatíveis com o SQL pendente da ED-71. */
    public static final int CODE_MAX = 60;
    public static final int REF_MAX = 200;

    public PpeDelivery {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        Objects.requireNonNull(workerId, "workerId (trabalhador que recebe o EPI) é obrigatório");
        functionCode = requireText(functionCode, "functionCode", CODE_MAX);
        itemCode = requireText(itemCode, "itemCode", CODE_MAX);
        lotCode = requireText(lotCode, "lotCode", CODE_MAX);
        approvalCertificate = requireText(approvalCertificate, "approvalCertificate (CA)", REF_MAX);
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity deve ser >= 1: " + quantity);
        }
        requisitionRef = requireText(requisitionRef, "requisitionRef (requisição da ED-58)",
                REF_MAX);
        warehouseLocationCode = requireText(warehouseLocationCode, "warehouseLocationCode",
                CODE_MAX);
        Objects.requireNonNull(deliveredBy, "deliveredBy (almoxarifado) é obrigatório");
        Objects.requireNonNull(deliveredOn, "deliveredOn é obrigatório");
        Objects.requireNonNull(lotValidUntil, "lotValidUntil (validade do lote) é obrigatório");
        if (lotValidUntil.isBefore(deliveredOn)) {
            throw new IllegalArgumentException("lote vencido não é entregue: lotValidUntil "
                    + lotValidUntil + " anterior à entrega " + deliveredOn);
        }
        if (!workerAcknowledged) {
            throw new IllegalArgumentException("entrega de EPI exige a ciência do trabalhador "
                    + "(ficha de EPI): workerAcknowledged deve ser verdadeiro");
        }
    }

    /** Nascimento da entrega: só com a norma ligada (a ativação é componente e reconfere). */
    public static PpeDelivery deliver(Iso45001Activation activation, UUID workerId,
                                      String functionCode, String itemCode, String lotCode,
                                      String approvalCertificate, int quantity,
                                      String requisitionRef, String warehouseLocationCode,
                                      UUID deliveredBy, LocalDate deliveredOn,
                                      LocalDate lotValidUntil, boolean workerAcknowledged) {
        return new PpeDelivery(activation, workerId, functionCode, itemCode, lotCode,
                approvalCertificate, quantity, requisitionRef, warehouseLocationCode, deliveredBy,
                deliveredOn, lotValidUntil, workerAcknowledged);
    }

    /** Uso do EPI na data: lote vencido bloqueia (validade inclusiva). */
    public void requireUsableOn(LocalDate date) {
        Objects.requireNonNull(date, "date é obrigatória");
        if (lotValidUntil.isBefore(date)) {
            throw new IllegalStateException("EPI do lote " + lotCode + " vencido em "
                    + lotValidUntil + ": uso bloqueado em " + date);
        }
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException(field + " é obrigatório e tem no máximo "
                    + max + " caracteres");
        }
        return value.trim();
    }
}
