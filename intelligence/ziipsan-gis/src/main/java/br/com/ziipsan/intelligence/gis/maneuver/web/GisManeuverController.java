package br.com.ziipsan.intelligence.gis.maneuver.web;

import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.AreaView;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.IsolationView;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.PlanInput;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.SectorView;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.TargetInput;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverDtos.TraceView;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * GIS-11: traçados montante/jusante, setores de manobra
 * pré-calculados, isolamento, áreas de influência/bacias/polígonos
 * e plano de manobra (aviso ED-41 + OS em lote ED-36, via serviço).
 * O tenant vem do contexto da requisição na fiação (aqui, o
 * {@code tenantId} é o do chamador autenticado resolvido pela
 * implementação de {@link ManeuverOperations}).
 */
@RestController
@RequestMapping("/api/v1/gis/maneuvers")
public class GisManeuverController {

    private final ManeuverOperations operations;

    public GisManeuverController(ManeuverOperations operations) {
        this.operations = operations;
    }

    @PostMapping("/trace")
    @PreAuthorize(GisManeuverRoles.READ)
    public TraceView trace(@RequestBody TargetInput target) {
        return operations.trace(currentTenant(), target);
    }

    @PostMapping("/isolate")
    @PreAuthorize(GisManeuverRoles.READ)
    public IsolationView isolate(@RequestBody TargetInput target) {
        return operations.isolate(currentTenant(), target);
    }

    @GetMapping("/sectors")
    @PreAuthorize(GisManeuverRoles.READ)
    public List<SectorView> sectors() {
        return operations.sectors(currentTenant());
    }

    @PostMapping("/basin")
    @PreAuthorize(GisManeuverRoles.READ)
    public AreaView basin(@RequestBody TargetInput target) {
        return operations.basin(currentTenant(), target);
    }

    @PostMapping("/influence")
    @PreAuthorize(GisManeuverRoles.READ)
    public AreaView influence(@RequestBody TargetInput target) {
        return operations.influence(currentTenant(), target);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisManeuverRoles.EDIT)
    public UUID createPlan(@RequestBody PlanInput input) {
        return operations.createPlanAndDispatch(currentTenant(), input, currentUser());
    }

    private UUID currentTenant() { return TenantContextHolder.getTenantId(); }

    private UUID currentUser() { return TenantContextHolder.getUserId(); }
}
