package br.com.ziipsan.intelligence.gis.clickos.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.ClickInput;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.CreatedView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PayloadView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PropagationView;
import br.com.ziipsan.intelligence.gis.clickos.ClickServiceOrderOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * II-1 (OS por clique no elemento GIS): propagação do clique,
 * payload pré-preenchido e criação da OS no núcleo da P1.31 pela
 * fiação do {@code ClickOsPorts.ClickOrderPort}. O tenant e o
 * solicitante vêm do contexto da requisição (token) — nunca do
 * corpo — como no {@code GisManeuverController}.
 */
@RestController
@RequestMapping(GisClickOsRoles.BASE)
public class GisClickOsController {

    private final ClickServiceOrderOperations operations;

    public GisClickOsController(ClickServiceOrderOperations operations) {
        this.operations = operations;
    }

    @PostMapping("/propagate")
    @PreAuthorize(GisClickOsRoles.READ)
    public PropagationView propagate(@RequestBody ClickInput input) {
        return operations.propagate(currentTenant(), input);
    }

    @PostMapping("/payload")
    @PreAuthorize(GisClickOsRoles.READ)
    public PayloadView payload(@RequestBody ClickInput input) {
        return operations.buildPayload(currentTenant(), currentUser(), input);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisClickOsRoles.EDIT)
    public CreatedView createOrder(@RequestBody ClickInput input) {
        return operations.createFromClick(currentTenant(), currentUser(), input);
    }

    private UUID currentTenant() {
        return TenantContextHolder.getTenantId();
    }

    private UUID currentUser() {
        return TenantContextHolder.getUserId();
    }
}
