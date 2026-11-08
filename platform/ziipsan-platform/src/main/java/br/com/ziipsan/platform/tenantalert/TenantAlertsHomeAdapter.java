package br.com.ziipsan.platform.tenantalert;

import br.com.ziipsan.platform.home.HomePorts;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-07: a extensão da página inicial da P3.39 prometida no aceite dela
 * ("os alertas do tenant entram quando a ED-07 existir, que estende esta
 * página"). Implementa a porta REAL {@link HomePorts.TenantAlerts} que a
 * P3.39 deixou com a implementação {@code UnavailableTenantAlerts}: a
 * fiação troca a implementação e a composição da página não muda.
 *
 * <p>Mapeamento: {@code code} = nome do evento do catálogo (ou
 * {@code batch-failure} na falha de lote), {@code title} = o código de
 * título do alerta (a camada de mensagens resolve código → texto, como nos
 * avisos da P3.36), {@code recordAddress} = o endereço do alerta na área de
 * alertas do tenant. Fonte presente com zero alertas devolve
 * {@code available=true} e lista vazia — diferente de fonte inexistente.
 */
public final class TenantAlertsHomeAdapter implements HomePorts.TenantAlerts {

    public static final int HOME_LIMIT = 5;
    public static final String BATCH_FAILURE_CODE = "batch-failure";

    private final TenantAlertService service;

    public TenantAlertsHomeAdapter(TenantAlertService service) {
        this.service = Objects.requireNonNull(service, "o serviço de alertas é obrigatório");
    }

    @Override
    public HomePorts.AlertsResult alertsOf(UUID tenantId) {
        List<HomePorts.TenantAlert> mapped = service.listAlerts(tenantId, HOME_LIMIT).stream()
                .map(a -> new HomePorts.TenantAlert(
                        a.kind() == TenantAlert.Kind.BATCH_FAILURE ? BATCH_FAILURE_CODE : a.eventName(),
                        a.titleCode(),
                        "/inicio/alertas/" + a.id()))
                .toList();
        return new HomePorts.AlertsResult(true, mapped);
    }
}
