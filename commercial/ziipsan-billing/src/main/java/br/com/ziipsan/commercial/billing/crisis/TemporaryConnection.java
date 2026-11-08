package br.com.ziipsan.commercial.billing.crisis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Ligacao temporaria (CAD-72): obra, feira ou evento - TIPO de ligacao com
 * vigencia, tarifa ou taxa propria, aviso de vencimento e encerramento
 * por OS. DISTINTA da situacao PROVISIONAL do ADR-0039, que indica
 * cadastro incompleto: aqui o cadastro e completo e a vigencia e curta
 * por natureza. Os dois conceitos nao se misturam (teste dedicado).
 *
 * Permissao de uso de hidrante com medicao: volume medido obrigatorio.
 */
public final class TemporaryConnection {

    public enum Purpose { WORK, FAIR, EVENT }
    /** Situacao cadastral do ADR-0039 - conceito separado, so para o teste de distincao. */
    public enum RegistrationSituation { COMPLETE, PROVISIONAL }

    private final UUID id;
    private final Purpose purpose;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final String tariffOrFeeCode;
    private final RegistrationSituation situation;
    private boolean expiryNoticeSent;
    private UUID closureOrderId;

    public TemporaryConnection(UUID id, Purpose purpose, LocalDate validFrom, LocalDate validTo,
                               String tariffOrFeeCode, int temporaryMaxDays) {
        this.id = Objects.requireNonNull(id, "ligacao obrigatoria");
        this.purpose = Objects.requireNonNull(purpose, "finalidade obrigatoria");
        this.validFrom = Objects.requireNonNull(validFrom, "inicio obrigatorio");
        this.validTo = Objects.requireNonNull(validTo, "fim obrigatorio");
        if (validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia invertida");
        long days = ChronoUnit.DAYS.between(validFrom, validTo) + 1;
        if (temporaryMaxDays < 1) throw new IllegalArgumentException("connection.temporary_max_days invalido");
        if (days > temporaryMaxDays)
            throw new IllegalArgumentException("vigencia de " + days + " dias acima de connection.temporary_max_days=" + temporaryMaxDays);
        if (tariffOrFeeCode == null || tariffOrFeeCode.isBlank())
            throw new IllegalArgumentException("tarifa ou taxa propria obrigatoria (codigo do catalogo)");
        this.tariffOrFeeCode = tariffOrFeeCode;
        this.situation = RegistrationSituation.COMPLETE;
    }

    /** Ligacao temporaria NUNCA e a situacao PROVISIONAL do ADR-0039. */
    public boolean isProvisionalSituation() { return situation == RegistrationSituation.PROVISIONAL; }
    public RegistrationSituation situation() { return situation; }

    public boolean activeOn(LocalDate date) {
        return closureOrderId == null && !date.isBefore(validFrom) && !date.isAfter(validTo);
    }
    /** Aviso de vencimento devido quando hoje entra na janela final informada pelo chamador. */
    public boolean expiryNoticeDue(LocalDate today, int noticeWindowDays) {
        if (noticeWindowDays < 0) throw new IllegalArgumentException("janela de aviso invalida");
        return closureOrderId == null && !expiryNoticeSent
                && !today.isBefore(validTo.minusDays(noticeWindowDays)) && !today.isAfter(validTo);
    }
    public void markExpiryNoticeSent() { this.expiryNoticeSent = true; }

    /** Encerramento por OS (obrigatoria e unica). */
    public void closeByOrder(UUID serviceOrderId) {
        Objects.requireNonNull(serviceOrderId, "OS de encerramento obrigatoria");
        if (closureOrderId != null) throw new IllegalStateException("ligacao temporaria ja encerrada");
        this.closureOrderId = serviceOrderId;
    }

    public UUID id() { return id; }
    public Purpose purpose() { return purpose; }
    public LocalDate validTo() { return validTo; }
    public UUID closureOrderId() { return closureOrderId; }

    public record HydrantUsePermit(UUID id, String hydrantCode, String permitRef, BigDecimal meteredVolumeM3) {
        public HydrantUsePermit {
            Objects.requireNonNull(id, "permissao obrigatoria");
            if (hydrantCode == null || hydrantCode.isBlank())
                throw new IllegalArgumentException("hidrante obrigatorio");
            if (permitRef == null || permitRef.isBlank())
                throw new IllegalArgumentException("referencia da permissao obrigatoria");
            Objects.requireNonNull(meteredVolumeM3, "medicao obrigatoria: uso de hidrante so com medicao");
            if (meteredVolumeM3.signum() <= 0) throw new IllegalArgumentException("volume medido deve ser positivo");
        }
    }
}
