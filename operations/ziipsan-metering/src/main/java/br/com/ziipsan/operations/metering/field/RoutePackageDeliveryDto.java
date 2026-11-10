package br.com.ziipsan.operations.metering.field;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO de entrega do pacote de rota (P-03 / RG2-C-P2): o formato em que o
 * servidor ENVIA o pacote ao aparelho (correcao do dono, 10/11/2026:
 * nao e "download" — o servidor entrega o pacote de rotas/leituras/OSs
 * ao aparelho; o sentido aparelho -> servidor e a sincronizacao, que ja
 * existe). Espelho, campo a campo, do {@code RoutePackage} TS de
 * {@code campo/src/readings/routePackage.ts}, incluindo o bloco opcional
 * de emissao por tarefa ({@code RouteTaskEmissionData}) que espelha o
 * {@code FieldBillInput}/{@code RouteTask} Java do ziipsan-billing-rules.
 * (O nome herdado {@code SyncTransport.downloadRoute} da ED-18, na main,
 * e facto historico; o alinhamento dele pertence a pendencia
 * P-MOB-INT-01. O campo {@code downloadedAt} do espelho TS tambem e
 * nome herdado da main e e mantido pela fidelidade do espelho —
 * carrega o {@code issuedAt} do pacote.)
 *
 * <p>Formato (nomes e tipos do espelho TS):
 * <ul>
 *   <li>ids como string (UUID em texto), {@code reference} como
 *       AAAA-MM, {@code downloadedAt} como instante ISO com offset
 *       (o {@code issuedAt} do pacote Java), {@code dueDate} como
 *       AAAA-MM-DD (o {@code LocalDate} em ISO);</li>
 *   <li>valores monetarios e percentuais como numero
 *       ({@link BigDecimal}), como no fixture de contrato
 *       {@code billing-contract-fixtures.json} (12.5, 80, 1.5);</li>
 *   <li>{@code emission} e {@code unitAddress} sao os campos opcionais do
 *       TS: {@code emission} vem null quando a tarefa nao tem emissao
 *       resolvida; {@code unitAddress} NAO e emitido por este DTO — o
 *       endereco nao tem fonte no servidor (pendencia RG2-C-P2, parte do
 *       endereco) e o TS o trata como opcional, exibindo os IDs.</li>
 * </ul>
 */
public record RoutePackageDeliveryDto(String routeId, String reference, String downloadedAt,
                                      List<OccurrenceDto> occurrences,
                                      List<TaskDto> tasks) {

    /** Espelho do {@code OccurrenceSnapshot} TS. */
    public record OccurrenceDto(String code, boolean requiresPhoto, boolean requiresReading) {
    }

    /** Espelho da {@code RouteTask} TS (sem unitAddress: sem fonte, ver acima). */
    public record TaskDto(String taskId, String unitId, String installationId,
                          String kind, String reference,
                          Integer previousReading, int meterDigits, int sequence,
                          EmissionDto emission) {
    }

    /**
     * Espelho do {@code RouteTaskEmissionData} TS: retrato + economias +
     * debitos/creditos + vencimento do {@code FieldBillInput}, e o
     * {@code barcodeValue} do {@code RouteTask} Java (null permitido:
     * tarefa sem cobranca resolvida imprime sem barras).
     */
    public record EmissionDto(SnapshotDto snapshot, int economies,
                              List<AmountLineDto> debits, List<AmountLineDto> credits,
                              String dueDate, String barcodeValue) {
    }

    /** Espelho do {@code FieldTariffSnapshotJson} TS / {@code FieldTariffSnapshot} Java. */
    public record SnapshotDto(String tariffVersionId, String categoryId,
                              int minimumM3PerEconomy, BigDecimal fixedChargePerEconomy,
                              List<BracketDto> brackets,
                              boolean sewerEnabled, BigDecimal sewerPct,
                              Map<String, String> parameterVersions) {
    }

    /** Espelho do {@code TariffBracketJson} TS / {@code TariffBracket} Java. */
    public record BracketDto(BigDecimal pricePerM3, Integer widthM3) {
    }

    /** Espelho do {@code BillAdjustmentJson} TS / {@code AmountLine} Java. */
    public record AmountLineDto(BigDecimal amount, String code) {
    }
}
