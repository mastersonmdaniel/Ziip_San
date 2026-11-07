package br.com.ziipsan.intelligence.gis.validation.web;

import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.AcceptInput;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.IssueView;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.PageView;
import br.com.ziipsan.intelligence.gis.validation.GisQualityDtos.SummaryView;
import br.com.ziipsan.intelligence.gis.validation.GisQualityService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Painel de qualidade do GIS (P2.21, §5.5): problemas da validação
 * contínua por severidade, estado, regra, tabela de feição e pacote, agregados para os
 * cartões do painel e aceite com justificativa obrigatória.
 */
@RestController
@RequestMapping(GisValidationRoles.BASE)
@PreAuthorize(GisValidationRoles.READ)
public class GisQualityController {

    private final GisQualityService service;

    public GisQualityController(GisQualityService service) {
        this.service = service;
    }

    @GetMapping("/issues")
    public PageView<IssueView> listIssues(@RequestParam(required = false) String severity,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false) String ruleCode,
                                          @RequestParam(required = false) String featureTable,
                                          @RequestParam(required = false) UUID changeSetId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.listIssues(severity, status, ruleCode, featureTable, changeSetId, page, size);
    }

    @GetMapping("/summary")
    public SummaryView summary() {
        return service.summary();
    }

    @PostMapping("/issues/{id}/accept")
    @PreAuthorize(GisValidationRoles.MANAGE)
    public IssueView accept(@PathVariable UUID id, @Valid @RequestBody AcceptInput in) {
        return service.accept(id, in);
    }
}
