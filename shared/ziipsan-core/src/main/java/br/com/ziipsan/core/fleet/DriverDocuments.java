package br.com.ziipsan.core.fleet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Habilitacao do motorista, licenciamento do veiculo, alertas de
 * vencimento ({@code fleet.docs_alert_days}) e multas por veiculo e
 * condutor (aceite da ED-59, OPE-28).
 *
 * <p>Regras:
 * <ul>
 *   <li>documento VENCIDO e comparacao de datas (validade anterior a
 *       data de referencia) — nao depende de parametro;</li>
 *   <li>VENCENDO (alerta) so existe com {@code fleet.docs_alert_days}
 *       do tenant: validade dentro da janela (hoje .. hoje+dias) e
 *       ainda nao vencida. Sem parametro, nenhum alerta e emitido
 *       (declarado em {@link FleetParameters});</li>
 *   <li>a habilitacao e do CONDUTOR; o licenciamento e do VEICULO
 *       ({@link FleetRegistry.Vehicle});</li>
 *   <li>a habilitacao/seguranca do trabalho do EXECUTOR segue a OS da
 *       P1.31 (OPE-14) — NAO e reimplementada aqui; esta classe cobre
 *       a habilitacao de transito (CNH) do motorista, que e o
 *       documento nomeado no aceite da ED-59.</li>
 * </ul>
 */
public final class DriverDocuments {

    private DriverDocuments() {
    }

    /** Habilitacao de transito do motorista (CNH): validade e categoria declarada. */
    public record DriverLicense(UUID personId, LocalDate validUntil, String category) {
        public DriverLicense {
            Objects.requireNonNull(personId, "habilitacao sem condutor");
            Objects.requireNonNull(validUntil, "habilitacao sem validade");
        }

        public boolean expiredOn(LocalDate date) {
            return validUntil.isBefore(date);
        }
    }

    public enum DocumentStatus { VALID, EXPIRING_SOON, EXPIRED }

    /** Estado de um documento numa data, com a janela de alerta do tenant (vazia = sem "vencendo"). */
    public static DocumentStatus statusOf(LocalDate validUntil, LocalDate referenceDate,
                                          Optional<Integer> docsAlertDays) {
        Objects.requireNonNull(validUntil, "validade e obrigatoria");
        Objects.requireNonNull(referenceDate, "data de referencia e obrigatoria");
        if (validUntil.isBefore(referenceDate)) return DocumentStatus.EXPIRED;
        if (docsAlertDays != null && docsAlertDays.isPresent()
                && !validUntil.isAfter(referenceDate.plusDays(docsAlertDays.get())))
            return DocumentStatus.EXPIRING_SOON;
        return DocumentStatus.VALID;
    }

    /** Alerta de vencimento: par (documento, estado) apenas para EXPIRING_SOON/EXPIRED. */
    public record ExpiryAlert(String documentKind, UUID subjectId, DocumentStatus status, LocalDate validUntil) {
    }

    public static List<ExpiryAlert> alerts(List<DriverLicense> licenses,
                                           List<FleetRegistry.Vehicle> vehicles,
                                           LocalDate referenceDate, Optional<Integer> docsAlertDays) {
        java.util.ArrayList<ExpiryAlert> out = new java.util.ArrayList<>();
        for (DriverLicense l : licenses) {
            DocumentStatus s = statusOf(l.validUntil(), referenceDate, docsAlertDays);
            if (s != DocumentStatus.VALID) out.add(new ExpiryAlert("DRIVER_LICENSE", l.personId(), s, l.validUntil()));
        }
        for (FleetRegistry.Vehicle v : vehicles) {
            DocumentStatus s = statusOf(v.licensingValidUntil(), referenceDate, docsAlertDays);
            if (s != DocumentStatus.VALID) out.add(new ExpiryAlert("VEHICLE_LICENSING", v.assetId(), s, v.licensingValidUntil()));
        }
        return List.copyOf(out);
    }

    /**
     * Multa de transito registrada por veiculo e condutor (aceite:
     * "multas registradas por veiculo e condutor"). O condutor pode
     * ser nulo quando a autuacao e do veiculo/proprietario sem
     * identificacao do condutor — o registro continua valido e a
     * totalizacao por condutor so soma as identificadas.
     */
    public record TrafficFine(UUID id, UUID vehicleAssetId, UUID driverPersonId,
                              BigDecimal amount, LocalDateTime occurredAt, String description) {
        public TrafficFine {
            Objects.requireNonNull(id, "multa sem id");
            Objects.requireNonNull(vehicleAssetId, "multa sem veiculo");
            if (amount == null || amount.signum() <= 0)
                throw new IllegalArgumentException("multa exige valor positivo: " + amount);
            Objects.requireNonNull(occurredAt, "multa sem data/hora da autuacao");
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("multa sem descricao da autuacao");
        }
    }

    public static BigDecimal totalByVehicle(List<TrafficFine> fines, UUID vehicleAssetId) {
        return fines.stream().filter(f -> f.vehicleAssetId().equals(vehicleAssetId))
                .map(TrafficFine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal totalByDriver(List<TrafficFine> fines, UUID driverPersonId) {
        Objects.requireNonNull(driverPersonId, "condutor e obrigatorio na totalizacao por condutor");
        return fines.stream().filter(f -> driverPersonId.equals(f.driverPersonId()))
                .map(TrafficFine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
