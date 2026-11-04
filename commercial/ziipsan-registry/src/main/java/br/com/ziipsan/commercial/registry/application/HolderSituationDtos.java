package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.Customer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Corpos e respostas das situações do titular (P1.41). */
public final class HolderSituationDtos {

    private HolderSituationDtos() {
    }

    public record RepresentationInput(
            @NotNull(message = "Informe o representado.") UUID representedId,
            @NotNull(message = "Informe o representante.") UUID representativeId,
            @NotNull @Pattern(regexp = "ATTORNEY|GUARDIAN|CURATOR|CONDO_MANAGER|ESTATE_ADMINISTRATOR|LEGAL_REPRESENTATIVE|AUTHORIZED_THIRD_PARTY",
                    message = "Tipo de representação inválido.") String type,
            @NotNull @Size(min = 1, message = "Informe ao menos um poder.")
            List<@Pattern(regexp = "VIEW|REQUEST|PAY|CHANGE_BILLING|OWNERSHIP",
                    message = "Poder inválido.") String> powers,
            UUID unitId,
            LocalDate validFrom,
            LocalDate validTo,
            @NotNull(message = "O documento comprobatório é obrigatório.") UUID proofDocumentId) {
    }

    public record RepresentationView(UUID id, UUID representedId, UUID representativeId, String type,
                                     List<String> powers, UUID unitId, LocalDate validFrom, LocalDate validTo,
                                     UUID proofDocumentId, String status, UUID serviceOrderId,
                                     boolean validNow) {
    }

    public record DeathAlertInput(
            @NotNull(message = "Informe o cliente.") UUID customerId,
            UUID unitId) {
    }

    public record DeathReviewInput(
            @NotNull @Pattern(regexp = "CONFIRM|REJECT", message = "Decisão inválida.") String decision,
            UUID provisionalCustomerId,
            @Pattern(regexp = "HEIR|ESTATE_ADMINISTRATOR|OCCUPANT",
                    message = "Tipo de responsável provisório inválido.") String provisionalKind,
            UUID endReasonId,
            UUID deliveryModeId) {
    }

    public record DeathAlertView(UUID id, UUID customerId, UUID unitId, String source, String status,
                                 UUID serviceOrderId, OffsetDateTime detectedAt, UUID reviewedBy,
                                 OffsetDateTime reviewedAt) {
    }

    public record ValidationInput(
            @NotNull(message = "Informe o cliente.") UUID customerId,
            @NotNull @Pattern(regexp = "CPF|CNPJ", message = "Espécie inválida.") String documentKind,
            @NotNull @Pattern(regexp = "REGULAR|IRREGULAR|NOT_FOUND|UNABLE_TO_VERIFY|DECEASED",
                    message = "Resultado inválido.") String result,
            @Size(max = 100) String provider,
            Boolean biometricVerified) {
    }

    public record ValidationView(UUID id, UUID customerId, String documentKind, String result, String provider,
                                 String legalBasis, boolean biometricVerified, OffsetDateTime validatedAt) {
    }

    public record ValidationRecordedView(ValidationView validation, List<UUID> deathAlertIds, boolean irregularAlert) {
    }

    public record TransferValidationView(UUID customerId, String mode, boolean satisfied, String latestResult) {
    }

    public record MergeInput(
            @NotNull(message = "Informe o cliente de origem.") UUID sourceCustomerId,
            @NotNull(message = "Informe o cliente de destino.") UUID targetCustomerId,
            @NotNull(message = "Informe o motivo de fim de vínculo para os vínculos encerrados pela fusão.")
            UUID mergeEndReasonId) {
    }

    public record MergeView(UUID id, UUID sourceCustomerId, UUID targetCustomerId, String status,
                            UUID serviceOrderId, UUID reversedByServiceOrderId, String preview, String snapshot) {
    }

    public record SplitInput(
            @NotNull(message = "Informe o cliente de origem.") UUID sourceCustomerId,
            @NotNull(message = "Informe o cliente novo (já cadastrado).") UUID newCustomerId,
            @NotNull @Size(min = 1, message = "Informe ao menos um vínculo a mover.") List<UUID> linkIds) {
    }

    public record SplitView(UUID id, UUID sourceCustomerId, UUID newCustomerId, String status,
                            UUID serviceOrderId, UUID reversedByServiceOrderId, String preview, String snapshot) {
    }

    public record OccupancyInput(
            @NotNull @Pattern(regexp = "OCCUPIED|VACANT|UNKNOWN",
                    message = "Ocupação inválida.") String occupancyStatus) {
    }

    public record SocialNameInput(
            @Size(max = 200, message = "O nome social aceita no máximo 200 caracteres.") String socialName) {
    }

    public record ChangeRequestView(UUID id, String kind, String targetType, UUID targetId,
                                    String oldValue, String newValue, UUID serviceOrderId, String status) {
    }

    public record HolderStatusView(UUID unitId, String occupancyStatus, UUID currentUserId, UUID currentOwnerId,
                                   List<UUID> provisionalHolderIds, String billHolderName, boolean billHeld,
                                   long pendingDeathAlerts) {
    }

    public record CandidateView(UUID id, String name, Customer.PersonType personType, String documentMasked) {
    }
}
