package br.com.ziipsan.commercial.registry.recadastramento;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Portao do aparelho para a coleta do recadastramento (ED-16), como
 * porta do registry: espelha o {@code FieldDeviceGate} da ED-18
 * (metering). A implementacao vive na plataforma (sobre
 * {@code platform.app_device} da core V11 da ED-18) e o registry nao
 * depende do modulo de plataforma nem do metering — so desta
 * interface. Recusa = excecao (aparelho nao registrado, pendente,
 * bloqueado ou retirado nao sincroniza coleta nenhuma).
 */
public interface CollectionDeviceGate {

    void assertCanSync(UUID tenantId, UUID deviceId, OffsetDateTime now);
}
