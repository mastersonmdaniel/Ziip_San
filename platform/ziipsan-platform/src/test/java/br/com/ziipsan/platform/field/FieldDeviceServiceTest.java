package br.com.ziipsan.platform.field;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ciclo de vida do aparelho e portao de sincronizacao (ED-18), com repositorio simulado sobre uma lista. */
class FieldDeviceServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 7, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    private final List<AppDevice> store = new ArrayList<>();
    private FieldDeviceService service;

    @BeforeEach
    void setUp() {
        AppDeviceRepository repo = mock(AppDeviceRepository.class);
        when(repo.findByTenantIdAndDeviceId(any(), any())).thenAnswer(inv -> store.stream()
                .filter(x -> x.getTenantId().equals(inv.getArgument(0)) && x.getDeviceId().equals(inv.getArgument(1)))
                .findFirst());
        when(repo.findByTenantIdOrderByCreatedAtDesc(any())).thenAnswer(inv -> store.stream()
                .filter(x -> x.getTenantId().equals(inv.getArgument(0))).toList());
        when(repo.save(any())).thenAnswer(inv -> {
            AppDevice e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId(UUID.randomUUID());
                store.add(e);
            }
            return e;
        });
        service = new FieldDeviceService(repo);
    }

    private AppDevice activeDevice() {
        service.register(TENANT, DEVICE, "sub-leiturista", "1.0.0", "MEETS_DEVICE_INTEGRITY", NOW);
        return service.approve(TENANT, DEVICE, ACTOR, DeviceArea.TENANT, NOW);
    }

    @Test
    @DisplayName("aparelho nao registrado nao sincroniza e recebe diretiva BLOCK")
    void unregisteredDoesNotSync() {
        assertThatThrownBy(() -> service.assertCanSync(TENANT, UUID.randomUUID(), NOW))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThat(service.directiveFor(TENANT, UUID.randomUUID())).isEqualTo(DeviceDirective.BLOCK);
    }

    @Test
    @DisplayName("aparelho pendente ou sem atestacao nao sincroniza nem e aprovado")
    void pendingDoesNotSync() {
        service.register(TENANT, DEVICE, "sub-leiturista", "1.0.0", null, NOW);
        assertThat(service.directiveFor(TENANT, DEVICE)).isEqualTo(DeviceDirective.BLOCK);
        assertThatThrownBy(() -> service.assertCanSync(TENANT, DEVICE, NOW))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThatThrownBy(() -> service.approve(TENANT, DEVICE, ACTOR, DeviceArea.TENANT, NOW))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("aparelho bloqueado nao sincroniza; diretiva e BLOCK (recusa e apaga)")
    void blockedDoesNotSync() {
        activeDevice();
        service.block(TENANT, DEVICE, ACTOR, DeviceArea.CENTRAL, "aparelho furtado", NOW.plusMinutes(1));
        assertThatThrownBy(() -> service.assertCanSync(TENANT, DEVICE, NOW.plusMinutes(2)))
                .isInstanceOf(ApiException.Forbidden.class);
        assertThat(service.directiveFor(TENANT, DEVICE)).isEqualTo(DeviceDirective.BLOCK);
    }

    @Test
    @DisplayName("aparelho retirado recebe diretiva RETIRE (envia pendencias e apaga) e nao sincroniza de novo")
    void retiredDirective() {
        activeDevice();
        service.retire(TENANT, DEVICE, ACTOR, DeviceArea.TENANT, "troca de aparelho", NOW.plusMinutes(1));
        assertThat(service.directiveFor(TENANT, DEVICE)).isEqualTo(DeviceDirective.RETIRE);
        assertThatThrownBy(() -> service.assertCanSync(TENANT, DEVICE, NOW.plusMinutes(2)))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    @DisplayName("revogacao registra a area (tenant ou Central) e o teto do aceite e 5 minutos")
    void revocationMetadataAndCeiling() {
        AppDevice device = activeDevice();
        service.block(TENANT, DEVICE, ACTOR, DeviceArea.CENTRAL, "perda", NOW.plusMinutes(3));
        assertThat(device.getStatusChangedByArea()).isEqualTo(DeviceArea.CENTRAL);
        assertThat(device.getStatusChangedAt()).isEqualTo(NOW.plusMinutes(3));
        assertThat(FieldDeviceService.REVOCATION_MAX_EFFECTIVE).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("re-registro do mesmo aparelho nao ressuscita estado bloqueado nem pula aprovacao")
    void reRegisterKeepsState() {
        activeDevice();
        service.block(TENANT, DEVICE, ACTOR, DeviceArea.TENANT, "bloqueio", NOW.plusMinutes(1));
        AppDevice again = service.register(TENANT, DEVICE, "sub-leiturista", "1.0.1",
                "MEETS_DEVICE_INTEGRITY", NOW.plusMinutes(2));
        assertThat(again.getStatus()).isEqualTo(AppDeviceStatus.BLOCKED);
    }
}
