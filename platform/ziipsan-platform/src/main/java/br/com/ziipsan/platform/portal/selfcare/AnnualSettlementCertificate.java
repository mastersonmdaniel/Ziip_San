package br.com.ziipsan.platform.portal.selfcare;

import java.time.Year;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Certidao de quitacao ANUAL (POC-69, ED-38): emitida somente quando
 * os DOZE meses do ano estao presentes na lista da unidade (que vem da
 * porta, do historico da P3.20) E todas as faturas estao quitadas;
 * mes faltante ou fatura nao quitada recusa a emissao com o motivo. A certidao sai como documento autentico
 * ({@link AuthenticDocument}, tipo {@code ANNUAL_SETTLEMENT_CERTIFICATE})
 * — codigo verificador/QR da ED-34 e nivel da P3.34 incluidos.
 */
public final class AnnualSettlementCertificate {

    public record YearBill(YearMonth reference, boolean settled) { }

    public interface YearBillsPort {
        boolean isUnitLinkedToConsumer(UUID tenantId, String consumerSubject, UUID unitId);
        List<YearBill> billsOfYear(UUID tenantId, UUID unitId, Year year);
    }

    private final YearBillsPort port;
    private final AuthenticDocument documents;

    public AnnualSettlementCertificate(YearBillsPort port, AuthenticDocument documents) {
        if (port == null || documents == null) {
            throw new IllegalArgumentException("portas sao obrigatorias");
        }
        this.port = port;
        this.documents = documents;
    }

    public AuthenticDocument.Issued issue(UUID tenantId, String consumerSubject, UUID unitId,
                                          Year year, String documentNumber, java.time.LocalDate issuedOn) {
        if (tenantId == null || consumerSubject == null || consumerSubject.isBlank()
                || unitId == null || year == null) {
            throw new IllegalArgumentException("tenant, consumidor, unidade e ano sao obrigatorios");
        }
        if (!port.isUnitLinkedToConsumer(tenantId, consumerSubject, unitId)) {
            throw new IllegalStateException("Unidade nao vinculada a esta conta do portal.");
        }
        List<YearBill> bills = port.billsOfYear(tenantId, unitId, year);
        if (bills == null || bills.isEmpty()) {
            throw new IllegalStateException("sem faturas no ano " + year + ": certidao nao emitida");
        }
        // Completude do ano (correcao D1 da revisao cruzada): a certidao
        // anual exige os DOZE meses do ano presentes na lista; mes
        // faltante recusa a emissao, mesmo com todos os presentes quitados.
        Set<YearMonth> present = bills.stream().map(YearBill::reference).collect(Collectors.toSet());
        Set<YearMonth> missing = new TreeSet<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth expected = YearMonth.of(year.getValue(), month);
            if (!present.contains(expected)) {
                missing.add(expected);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "ano " + year + " incompleto, meses faltantes " + missing + ": certidao de quitacao anual recusada");
        }
        List<YearMonth> open = bills.stream().filter(b -> !b.settled()).map(YearBill::reference).sorted().toList();
        if (!open.isEmpty()) {
            throw new IllegalStateException("faturas nao quitadas em " + open + ": certidao de quitacao anual recusada");
        }
        return documents.issue(tenantId, AuthenticDocument.Kind.CERTIFICATE,
                "ANNUAL_SETTLEMENT_CERTIFICATE", documentNumber, issuedOn);
    }
}
