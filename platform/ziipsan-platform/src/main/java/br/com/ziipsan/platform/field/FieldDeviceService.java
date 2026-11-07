package br.com.ziipsan.platform.field;

import br.com.ziipsan.core.api.ApiException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Registro, aprovacao, bloqueio e retirada de aparelhos do app de campo (ED-18; ADR-0030),
 * sobre {@code platform.app_device} (core V11).
 *
 * <h2>Portao de sincronizacao</h2>
 * {@link #assertCanSync} e chamado no inicio de TODA sincronizacao (e de todo download de
 * pacote de rota): aparelho nao registrado, pendente, bloqueado ou retirado NAO sincroniza.
 * A conferencia le o estado atual do banco a cada contato — nao ha cache de estado neste
 * servico —, e por isso a revogacao feita pela area do tenant ou pela Central vale no
 * contato seguinte. O teto de 5 minutos do aceite cobre o intervalo ate esse contato:
 * {@link #REVOCATION_MAX_EFFECTIVE} e a constante que o teste da ED-18 fixa, e qualquer
 * cache futuro entre o aparelho e esta conferencia tem de caber dentro dela somado ao
 * tempo de propagacao; introduzir cache sem rever esta constante quebra o aceite.
 *
 * <h2>Diretivas ao aparelho</h2>
 * {@link #directiveFor} traduz o estado na diretiva do contato: BLOCKED devolve
 * {@code BLOCK} (recusa + apagamento local); RETIRED devolve {@code RETIRE} (o aparelho
 * envia as pendencias uma ultima vez e apaga). Quem executa o apagamento e o app; o
 * servidor nunca apaga dado do aparelho remotamente — ele recusa e instrui.
 */
@Service
public class FieldDeviceService {

    /** Teto do aceite da ED-18: revogacao efetiva em no maximo 5 minutos. */
    public static final Duration REVOCATION_MAX_EFFECTIVE = Duration.ofMinutes(5);

    private final AppDeviceRepository devices;

    public FieldDeviceService(AppDeviceRepository devices) {
        this.devices = devices;
    }

    /**
     * Registra um aparelho novo em PENDING_APPROVAL. Registro repetido do mesmo
     * (tenant, device_id) devolve o existente sem mudar o estado: reinstalar o app
     * nao ressuscita aparelho bloqueado nem pula a aprovacao.
     */
    public AppDevice register(UUID tenantId, UUID deviceId, String userSub, String appVersion,
                              String attestationVerdict, OffsetDateTime now) {
        return devices.findByTenantIdAndDeviceId(tenantId, deviceId).orElseGet(() -> {
            AppDevice device = new AppDevice();
            device.setTenantId(tenantId);
            device.setDeviceId(deviceId);
            device.setUserSub(userSub);
            device.setStatus(AppDeviceStatus.PENDING_APPROVAL);
            device.setAppVersion(appVersion);
            device.setAttestationVerdict(attestationVerdict);
            device.setAttestedAt(attestationVerdict == null ? null : now);
            device.setLastContactAt(now);
            return devices.save(device);
        });
    }

    /**
     * Aprova um aparelho pendente. Exige atestacao apresentada e nao reprovada:
     * aparelho sem atestacao (ou com FAILED) nao sai de PENDING_APPROVAL.
     */
    public AppDevice approve(UUID tenantId, UUID deviceId, UUID approvedBy, DeviceArea area,
                             OffsetDateTime now) {
        AppDevice device = require(tenantId, deviceId);
        if (device.getStatus() != AppDeviceStatus.PENDING_APPROVAL) {
            throw new ApiException.Conflict("device-not-pending",
                    "So aparelho aguardando aprovacao pode ser aprovado.");
        }
        if (device.getAttestationVerdict() == null || "NOT_PRESENTED".equals(device.getAttestationVerdict())
                || "FAILED".equals(device.getAttestationVerdict())) {
            throw new ApiException.Conflict("device-attestation-required",
                    "Aparelho sem atestacao valida nao pode ser aprovado.");
        }
        device.setStatus(AppDeviceStatus.ACTIVE);
        device.setApprovedBy(approvedBy);
        device.setApprovedAt(now);
        touchStatus(device, area, approvedBy, null, now);
        return devices.save(device);
    }

    /** Bloqueia (revoga) o aparelho: area do tenant ou Central, efeito no contato seguinte. */
    public AppDevice block(UUID tenantId, UUID deviceId, UUID actor, DeviceArea area,
                           String reason, OffsetDateTime now) {
        return changeStatus(tenantId, deviceId, AppDeviceStatus.BLOCKED, actor, area, reason, now);
    }

    /** Retira o aparelho de uso: ele ainda envia as pendencias uma vez (diretiva RETIRE). */
    public AppDevice retire(UUID tenantId, UUID deviceId, UUID actor, DeviceArea area,
                            String reason, OffsetDateTime now) {
        return changeStatus(tenantId, deviceId, AppDeviceStatus.RETIRED, actor, area, reason, now);
    }

    private AppDevice changeStatus(UUID tenantId, UUID deviceId, AppDeviceStatus target, UUID actor,
                                   DeviceArea area, String reason, OffsetDateTime now) {
        AppDevice device = require(tenantId, deviceId);
        if (device.getStatus() == target) {
            return device;
        }
        device.setStatus(target);
        touchStatus(device, area, actor, reason, now);
        return devices.save(device);
    }

    private void touchStatus(AppDevice device, DeviceArea area, UUID actor, String reason, OffsetDateTime now) {
        device.setStatusChangedByArea(area);
        device.setStatusChangedBy(actor);
        device.setStatusChangedAt(now);
        device.setStatusReason(reason);
    }

    /** Diretiva do proximo contato deste aparelho; aparelho desconhecido recebe BLOCK. */
    public DeviceDirective directiveFor(UUID tenantId, UUID deviceId) {
        return devices.findByTenantIdAndDeviceId(tenantId, deviceId)
                .map(d -> DeviceDirective.forStatus(d.getStatus()))
                .orElse(DeviceDirective.BLOCK);
    }

    /**
     * Portao de sincronizacao: so ACTIVE sincroniza. A recusa e 403 indistinta para
     * nao registrado, pendente, bloqueado e retirado (nao vaza qual caso e).
     */
    public AppDevice assertCanSync(UUID tenantId, UUID deviceId, OffsetDateTime now) {
        AppDevice device = devices.findByTenantIdAndDeviceId(tenantId, deviceId)
                .filter(d -> d.getStatus().canSync() && d.getApprovedAt() != null)
                .orElseThrow(() -> new ApiException.Forbidden(
                        "Aparelho nao autorizado a sincronizar."));
        device.setLastContactAt(now);
        return devices.save(device);
    }

    public List<AppDevice> list(UUID tenantId) {
        return devices.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private AppDevice require(UUID tenantId, UUID deviceId) {
        return devices.findByTenantIdAndDeviceId(tenantId, deviceId)
                .orElseThrow(() -> new ApiException.NotFound("Aparelho nao registrado."));
    }
}
