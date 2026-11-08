package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.AreaView;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.IsolationView;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.PlanInput;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.SectorView;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.TargetInput;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.TraceView;

import java.util.List;
import java.util.UUID;

/**
 * Operações da GIS-11 expostas pela API. A implementação de
 * produção (fiação de integração) carrega o retrato pela
 * {@link ManeuverPorts.SnapshotPort} (topologia persistida da
 * GIS-03) e delega aos motores puros desta entrega; o plano de
 * manobra executa sobre {@link ManeuverPlanService}. Declarada como
 * porta para o núcleo ficar provado sem banco/Spring, como nas
 * ondas GIS anteriores.
 */
public interface ManeuverOperations {

    TraceView trace(UUID tenantId, TargetInput target);

    IsolationView isolate(UUID tenantId, TargetInput target);

    List<SectorView> sectors(UUID tenantId);

    AreaView basin(UUID tenantId, TargetInput target);

    AreaView influence(UUID tenantId, TargetInput target);

    /** Cria o plano, dispara o aviso (ED-41) e os lotes de OS (ED-36); devolve o id do plano. */
    UUID createPlanAndDispatch(UUID tenantId, PlanInput input, UUID requestedBy);
}
