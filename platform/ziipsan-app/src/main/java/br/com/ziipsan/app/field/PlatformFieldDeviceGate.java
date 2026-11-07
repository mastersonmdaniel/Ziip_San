package br.com.ziipsan.app.field;

import br.com.ziipsan.operations.metering.field.FieldDeviceGate;
import br.com.ziipsan.operations.metering.field.SyncDirective;
import br.com.ziipsan.platform.field.FieldDeviceService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Adaptador da plataforma para o portao de sincronizacao do metering: delega ao
 * {@link FieldDeviceService} (estado em {@code platform.app_device}, core V11). Vive no modulo de montagem
 * (ziipsan-app, que depende de plataforma e de metering) para nao criar dependencia
 * nova entre os dois modulos; o metering conhece so a porta {@code FieldDeviceGate}.
 */
@Component
public class PlatformFieldDeviceGate implements FieldDeviceGate {

    private final FieldDeviceService devices;

    public PlatformFieldDeviceGate(FieldDeviceService devices) {
        this.devices = devices;
    }

    @Override
    public void assertCanSync(UUID tenantId, UUID deviceId, OffsetDateTime now) {
        devices.assertCanSync(tenantId, deviceId, now);
    }

    @Override
    public SyncDirective directiveFor(UUID tenantId, UUID deviceId) {
        return SyncDirective.valueOf(devices.directiveFor(tenantId, deviceId).name());
    }
}
