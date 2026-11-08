package br.com.ziipsan.intelligence.losses.pressure;

import java.util.Objects;
import java.util.UUID;

/**
 * Zona de pressao (PRESSURE_ZONE; ED-86, OPE-04): regiao hidraulica
 * com limites proprios de pressao. Os limites NAO nascem na zona: sao
 * resolvidos dos parametros do tenant
 * ({@code pressure.min_mca}/{@code pressure.max_mca}, via
 * {@link PressureParameters#zoneLimits}) e guardados como retrato da
 * resolucao — zona sem limites resolvidos NAO existe nesta entrega
 * (o construtor recusa), porque sem a ABNT NBR 12218:2017 conferida
 * nao ha valor de fabrica para presumir.
 */
public final class PressureZone {

    public enum Status { ACTIVE, INACTIVE }

    private final UUID id;
    private final UUID tenantId;
    private final String code;
    private final String name;
    private final PressureParameters.ZoneLimits limits;
    private Status status = Status.ACTIVE;

    public PressureZone(UUID id, UUID tenantId, String code, String name,
                        PressureParameters.ZoneLimits limits) {
        this.id = Objects.requireNonNull(id, "zona de pressao sem id");
        this.tenantId = Objects.requireNonNull(tenantId, "zona de pressao sem tenant");
        if (code == null || code.isBlank()) throw new IllegalArgumentException(
                "codigo da zona de pressao obrigatorio (unico por tenant)");
        if (name == null || name.isBlank()) throw new IllegalArgumentException(
                "nome da zona de pressao obrigatorio");
        this.limits = Objects.requireNonNull(limits,
                "zona de pressao exige os limites resolvidos de pressure.min_mca/pressure.max_mca "
                        + "(sem valor de fabrica: parametro faltante e erro explicito, nao zona sem limite)");
        this.code = code;
        this.name = name;
    }

    public void deactivate() { this.status = Status.INACTIVE; }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String code() { return code; }
    public String name() { return name; }
    public PressureParameters.ZoneLimits limits() { return limits; }
    public Status status() { return status; }
}
