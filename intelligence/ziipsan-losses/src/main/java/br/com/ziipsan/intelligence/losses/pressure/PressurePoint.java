package br.com.ziipsan.intelligence.losses.pressure;

import java.util.Objects;
import java.util.UUID;

/**
 * Ponto de controle de pressao (PRESSURE_POINT; ED-86, OPE-04):
 * pertence a uma {@link PressureZone} e le a sua serie do ponto de
 * medicao da ED-17 referenciado pelo codigo estavel — o medidor
 * continua sendo da ED-17; nenhum medidor paralelo nasce aqui.
 *
 * <p>O registro ({@link #register}) confere a existencia do ponto na
 * ED-17 pela {@link PressureSeriesPort} em modo fail-closed (padrao
 * R1 da ED-85): porta ausente ou codigo inexistente recusam. O
 * construtor direto existe para a reidratacao a partir da
 * persistencia, onde o vinculo ja foi conferido no registro.
 */
public final class PressurePoint {

    private final UUID id;
    private final UUID tenantId;
    private final UUID zoneId;
    private final String code;
    private final String measuringPointCode;
    private final String locationText;
    private boolean active = true;

    public PressurePoint(UUID id, UUID tenantId, UUID zoneId, String code,
                         String measuringPointCode, String locationText) {
        this.id = Objects.requireNonNull(id, "ponto de controle sem id");
        this.tenantId = Objects.requireNonNull(tenantId, "ponto de controle sem tenant");
        this.zoneId = Objects.requireNonNull(zoneId, "ponto de controle exige a zona de pressao");
        if (code == null || code.isBlank()) throw new IllegalArgumentException(
                "codigo do ponto de controle obrigatorio (unico por tenant)");
        if (measuringPointCode == null || measuringPointCode.isBlank()) throw new IllegalArgumentException(
                "codigo do ponto de medicao da ED-17 obrigatorio no ponto de controle");
        this.code = code;
        this.measuringPointCode = measuringPointCode;
        this.locationText = locationText;
    }

    /** Registro com conferencia fail-closed da existencia do ponto de medicao na ED-17. */
    public static PressurePoint register(UUID id, UUID tenantId, UUID zoneId, String code,
                                         String measuringPointCode, String locationText,
                                         PressureSeriesPort seriesPort) {
        Objects.requireNonNull(seriesPort,
                "porta da serie da ED-17 obrigatoria no registro do ponto (fail-closed)");
        PressurePoint point = new PressurePoint(id, tenantId, zoneId, code, measuringPointCode, locationText);
        if (!seriesPort.exists(tenantId, measuringPointCode))
            throw new IllegalArgumentException(
                    "ponto de medicao inexistente na ED-17 para o tenant (vinculo recusado, fail-closed): "
                            + measuringPointCode);
        return point;
    }

    public void deactivate() { this.active = false; }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID zoneId() { return zoneId; }
    public String code() { return code; }
    public String measuringPointCode() { return measuringPointCode; }
    public String locationText() { return locationText; }
    public boolean active() { return active; }
}
