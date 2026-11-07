package br.com.ziipsan.commercial.registry.application;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Contratos da ligação (GIS-05, P2.23). A ficha tem duas formas: a
 * completa (ligação, imóvel, endereço normalizado/geocodificado da
 * P1.3, unidades atendidas e hidrômetro vigente) e a RESTRITA — só
 * identificador e situação — para quem não tem {@code gis.view-personal}
 * (§3.11/§12 do GIS-CADASTRO-TECNICO: coordenada ligada a pessoa é dado
 * pessoal).
 */
public final class ServiceConnectionDtos {

    private ServiceConnectionDtos() {
    }

    /** Criação de ligação para o imóvel âncora de uma unidade. */
    public record CreateInput(
            @NotNull UUID unitId,
            @NotNull String kind,
            String code,
            @NotNull UUID statusId,
            @NotNull LocalDate connectedOn,
            String connectedOnSource,
            List<UUID> additionalUnitIds) {
    }

    /** Mudança de situação: sempre por OS, como todo o cadastro (ADR-0039). */
    public record ChangeStatusInput(
            @NotNull UUID statusId,
            @NotNull UUID serviceOrderId,
            String reason,
            List<UUID> parameterVersionIds) {
    }

    public record ConnectionView(
            UUID id,
            UUID propertyUnitId,
            String kind,
            String code,
            UUID statusId,
            String statusCode,
            String statusName,
            String systemRole,
            LocalDate connectedOn,
            String connectedOnSource,
            LocalDate disconnectedOn,
            LocalDate endedOn,
            List<UUID> unitIds,
            Long version) {
    }

    /** Forma restrita da ficha: só identificador e situação. */
    public record RestrictedConnectionView(
            UUID id,
            String kind,
            String statusCode,
            String statusName,
            String systemRole) {
    }

    /** Endereço normalizado e geocodificação da P1.3 (CAD-83) do imóvel. */
    public record PropertyAddressView(
            UUID addressId,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String cep,
            String municipalityIbge,
            String geocodePrecision,
            Double longitude,
            Double latitude) {
    }

    /** Unidade consumidora atendida pela ligação (ficha completa). */
    public record ServedUnitView(
            UUID unitId,
            String registrationNumber,
            String checkDigit,
            boolean propertyAnchor) {
    }

    /** Hidrômetro vigente de uma unidade atendida (meter_installations com removed_on nulo). */
    public record CurrentMeterView(
            UUID unitId,
            UUID meterId,
            String meterNumber,
            String brand,
            String model,
            LocalDate installedOn) {
    }

    /** Ficha completa da ligação. */
    public record ConnectionCardView(
            ConnectionView connection,
            UUID propertyUnitId,
            String propertyRegistrationNumber,
            PropertyAddressView address,
            List<ServedUnitView> servedUnits,
            List<CurrentMeterView> currentMeters,
            List<UUID> serviceLineIds) {
    }

    public record HistoryView(
            UUID id,
            UUID fromStatusId,
            UUID toStatusId,
            String fromStatusCode,
            String toStatusCode,
            OffsetDateTime changedAt,
            String reason,
            UUID serviceOrderId,
            List<UUID> parameterVersionIds) {
    }
}
