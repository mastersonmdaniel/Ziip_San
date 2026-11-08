package br.com.ziipsan.intelligence.losses.dmc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Distrito de medicao e controle (DMC) como ENTIDADE VIVA (ED-85;
 * OPE-01, COE-11): o modulo {@code losses} e o dono da setorizacao.
 *
 * <p><b>Fonte unica de verdade (decisao declarada):</b> a geometria do
 * DMC vive SOMENTE na zona DMA de {@code gis_zone}
 * ({@code zone_type='DMA'}, V13_2); os atributos operacionais
 * (setor, ligacoes, extensao de rede, macromedidores, meta) vivem no
 * registro operacional ancorado em {@code dma_district} (V2_2, modulo
 * losses) — a tabela nova {@code loss_dmc_profiles} estende
 * {@code dma_district} 1:1 e referencia a zona por
 * {@code gis_zone_id}. NENHUMA geometria e duplicada aqui e NENHUM
 * segundo cadastro de distrito e criado: {@code DmcDistrict} sem
 * vinculo com a zona DMA nao existe (construtor recusa), e o servico
 * ({@link DmcSectorService}) garante um DMC ativo por zona.
 *
 * <p>Macromedidores de entrada e saida sao VINCULOS a pontos de
 * medicao da ED-17 ({@code MeasuringPoint}, identificados pelo codigo
 * estavel do tenant) — o medidor em si continua sendo da medicao,
 * nenhum medidor paralelo nasce no losses. A EXISTENCIA do ponto e
 * conferida no vinculo pela {@link MeasuringPointPort} (correcao R1):
 * codigo inexistente na ED-17 e porta ausente RECUSAM (fail-closed).
 */
public final class DmcDistrict {

    public enum Status { ACTIVE, INACTIVE }

    public enum Direction { INLET, OUTLET }

    /** Vinculo a um macromedidor da ED-17 pelo codigo do ponto de medicao. */
    public record MacroMeterLink(Direction direction, String measuringPointCode) {
        public MacroMeterLink {
            Objects.requireNonNull(direction, "direcao do macromedidor obrigatoria");
            if (measuringPointCode == null || measuringPointCode.isBlank())
                throw new IllegalArgumentException("codigo do ponto de medicao (ED-17) obrigatorio no vinculo");
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final UUID dmaDistrictId;
    private final UUID gisZoneId;
    private final String code;
    private final String sectorCode;
    private final int connections;
    private final Double mainsLengthKmOrNull;
    private Status status = Status.ACTIVE;
    private final List<MacroMeterLink> meterLinks = new ArrayList<>();

    public DmcDistrict(UUID id, UUID tenantId, UUID dmaDistrictId, UUID gisZoneId,
                       String code, String sectorCode, int connections, Double mainsLengthKmOrNull) {
        this.id = Objects.requireNonNull(id, "DMC sem id");
        this.tenantId = Objects.requireNonNull(tenantId, "DMC sem tenant");
        this.dmaDistrictId = Objects.requireNonNull(dmaDistrictId,
                "DMC exige o registro dma_district (V2_2) que ele estende");
        this.gisZoneId = Objects.requireNonNull(gisZoneId,
                "DMC exige o vinculo com a zona DMA de gis_zone (fonte unica da geometria)");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do DMC obrigatorio (unico por tenant)");
        if (sectorCode == null || sectorCode.isBlank()) throw new IllegalArgumentException("setor do DMC obrigatorio (balanco por setor)");
        if (connections < 0) throw new IllegalArgumentException("ligacoes do DMC negativas");
        if (mainsLengthKmOrNull != null && mainsLengthKmOrNull < 0)
            throw new IllegalArgumentException("extensao de rede do DMC negativa");
        this.code = code;
        this.sectorCode = sectorCode;
        this.connections = connections;
        this.mainsLengthKmOrNull = mainsLengthKmOrNull;
    }

    public void linkMacroMeter(MacroMeterLink link, MeasuringPointPort measuringPoints) {
        Objects.requireNonNull(link, "vinculo de macromedidor obrigatorio");
        Objects.requireNonNull(measuringPoints,
                "porta de pontos de medicao da ED-17 obrigatoria no vinculo (fail-closed)");
        if (status != Status.ACTIVE) throw new IllegalStateException("DMC inativo nao recebe macromedidor");
        boolean duplicate = meterLinks.stream().anyMatch(m ->
                m.direction() == link.direction() && m.measuringPointCode().equals(link.measuringPointCode()));
        if (duplicate) throw new IllegalArgumentException("macromedidor ja vinculado nesta direcao: " + link);
        if (!measuringPoints.exists(tenantId, link.measuringPointCode()))
            throw new IllegalArgumentException(
                    "ponto de medicao inexistente na ED-17 para o tenant (vinculo recusado, fail-closed): "
                            + link.measuringPointCode());
        meterLinks.add(link);
    }

    public List<MacroMeterLink> inletMeters() {
        return meterLinks.stream().filter(m -> m.direction() == Direction.INLET).toList();
    }

    public List<MacroMeterLink> outletMeters() {
        return meterLinks.stream().filter(m -> m.direction() == Direction.OUTLET).toList();
    }

    /** Balanco exige ao menos um macromedidor de entrada (a saida pode nao existir num DMC fechado). */
    public boolean hasInletMeter() { return !inletMeters().isEmpty(); }

    public void deactivate() { this.status = Status.INACTIVE; }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID dmaDistrictId() { return dmaDistrictId; }
    public UUID gisZoneId() { return gisZoneId; }
    public String code() { return code; }
    public String sectorCode() { return sectorCode; }
    public int connections() { return connections; }
    public Double mainsLengthKmOrNull() { return mainsLengthKmOrNull; }
    public Status status() { return status; }
    public List<MacroMeterLink> meterLinks() { return List.copyOf(meterLinks); }
}
