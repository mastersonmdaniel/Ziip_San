package br.com.ziipsan.operations.metering.field;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Portao do aparelho para a sincronizacao, como porta do metering: a implementacao vive
 * na plataforma ({@code PlatformFieldDeviceGate}, sobre {@code platform.app_device} da
 * core V11) e o metering nao depende do modulo de plataforma — so desta interface.
 * Recusa = excecao 403 (aparelho nao registrado, pendente, bloqueado ou retirado).
 */
public interface FieldDeviceGate {

    void assertCanSync(UUID tenantId, UUID deviceId, OffsetDateTime now);

    /** Diretiva atual do aparelho para o contato leve que precede a sincronizacao. */
    SyncDirective directiveFor(UUID tenantId, UUID deviceId);
}
