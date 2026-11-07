package br.com.ziipsan.platform.notification;

import java.util.Set;
import java.util.UUID;

/**
 * Porta para os parâmetros {@code notify.*} do tenant (catálogo global de parâmetros):
 * {@code notify.digest_default} (classe d) e {@code notify.mandatory_categories}
 * (classe c, piso da plataforma). Ausência de valor vigente é erro explícito do chamador
 * (ver {@code MissingParameterException} do core), nunca um padrão inventado aqui.
 */
public interface NotificationParameters {
    DigestMode digestDefault(UUID tenantId);

    /** Categorias que o TENANT declarou obrigatórias; o piso da plataforma é somado pela política. */
    Set<NotificationCategory> tenantMandatoryCategories(UUID tenantId);
}
