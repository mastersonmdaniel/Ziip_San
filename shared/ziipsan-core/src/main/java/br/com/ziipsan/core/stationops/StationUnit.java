package br.com.ziipsan.core.stationops;

import java.util.List;
import java.util.UUID;

/**
 * Unidade operacional da ED-87: a estacao sobre a qual o diario e a
 * ronda acontecem — ETA, ETE, elevatoria ou reservatorio (aceite).
 * Estrutura de cadastro do prestador: nenhuma unidade, filtro ou
 * sistema nasce de fabrica.
 *
 * <p>{@code supplySystem} e a mesma chave textual do sistema de
 * abastecimento usada pela ED-50 (fechamento mensal e SISAGUA): e o
 * elo que deixa o dado de processo do diario alimentar a conformidade
 * mensal SEM redigitacao (JOR-21).</p>
 *
 * <p>{@code filtration} so se aplica a ETA: a regra das 2 h da
 * Portaria GM/MS 888/2021, art. 28, §3o, incide sobre a unidade
 * filtrante com filtracao RAPIDA. ETE/elevatoria/reservatorio sao
 * {@code NOT_APPLICABLE} e nao entram na apuracao de turbidez.</p>
 */
public record StationUnit(
        UUID id,
        UUID tenantId,
        String code,
        UnitKind kind,
        String supplySystem,
        Filtration filtration,
        List<String> filterUnitCodes,
        boolean active) {

    public enum UnitKind { WTP, WWTP, PUMP_STATION, RESERVOIR }

    public enum Filtration { RAPID, SLOW, NOT_APPLICABLE }

    public StationUnit {
        if (id == null) throw new IllegalArgumentException("id da unidade e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId da unidade e obrigatorio");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code da unidade e obrigatorio");
        if (kind == null) throw new IllegalArgumentException("kind da unidade " + code + " e obrigatorio");
        if (supplySystem == null || supplySystem.isBlank())
            throw new IllegalArgumentException("supplySystem e obrigatorio para a unidade " + code);
        if (filtration == null)
            throw new IllegalArgumentException("filtration e obrigatorio para a unidade " + code);
        if (kind != UnitKind.WTP && filtration != Filtration.NOT_APPLICABLE)
            throw new IllegalArgumentException("filtracao so se aplica a ETA (unidade " + code + ")");
        filterUnitCodes = filterUnitCodes == null ? List.of() : List.copyOf(filterUnitCodes);
        if (filtration == Filtration.RAPID && filterUnitCodes.isEmpty())
            throw new IllegalArgumentException("unidade " + code + " com filtracao rapida exige as"
                    + " unidades filtrantes declaradas (a turbidez e por unidade filtrante)");
        if (filterUnitCodes.stream().anyMatch(f -> f == null || f.isBlank()))
            throw new IllegalArgumentException("codigo de unidade filtrante em branco na unidade " + code);
        if (filterUnitCodes.stream().distinct().count() != filterUnitCodes.size())
            throw new IllegalArgumentException("unidade filtrante duplicada na unidade " + code);
    }

    /** True quando a regra das 2 h (Portaria 888/2021, art. 28, §3o) incide sobre esta unidade. */
    public boolean rapidFiltration() { return filtration == Filtration.RAPID; }
}
