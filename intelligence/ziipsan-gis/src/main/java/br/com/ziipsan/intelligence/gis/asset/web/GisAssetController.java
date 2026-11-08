package br.com.ziipsan.intelligence.gis.asset.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.asset.AssetDtos;
import br.com.ziipsan.intelligence.gis.asset.AssetOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GIS-19: inventario de ativos, espacos confinados, vencimentos do
 * plano preventivo, conciliacao GIS x patrimonio (ED-32) e plano de
 * renovacao. O tenant vem do contexto da requisicao na fiacao.
 */
@RestController
@RequestMapping("/api/v1/gis/assets")
public class GisAssetController {

    private final AssetOperations operations;

    public GisAssetController(AssetOperations operations) {
        this.operations = operations;
    }

    @GetMapping("/inventory")
    @PreAuthorize(GisAssetRoles.READ)
    public List<AssetDtos.AssetView> inventory() {
        return operations.inventory(currentTenant());
    }

    @GetMapping("/confined-spaces")
    @PreAuthorize(GisAssetRoles.READ)
    public List<AssetDtos.AssetView> confinedSpaces() {
        return operations.confinedSpaces(currentTenant());
    }

    @GetMapping("/preventive-due")
    @PreAuthorize(GisAssetRoles.READ)
    public List<AssetDtos.PmDueView> preventiveDue(@RequestParam LocalDate referenceDate) {
        return operations.preventiveDue(currentTenant(), referenceDate);
    }

    @PostMapping("/reconcile")
    @PreAuthorize(GisAssetRoles.EDIT)
    public AssetDtos.ReconciliationView reconcile(@RequestParam int exercise) {
        return operations.reconcile(currentTenant(), exercise);
    }

    @GetMapping("/renewal-plan")
    @PreAuthorize(GisAssetRoles.READ)
    public List<AssetDtos.RiskView> renewalPlan() {
        return operations.renewalPlan(currentTenant());
    }

    /** Programa de exercicio de valvulas/hidrantes (OPE-09) — correcao D8 da ED-56. */
    @GetMapping("/valve-program")
    @PreAuthorize(GisAssetRoles.READ)
    public List<AssetDtos.ValveProgramView> valveProgram(@RequestParam LocalDate referenceDate) {
        return operations.valveProgram(currentTenant(), referenceDate);
    }

    private UUID currentTenant() { return TenantContextHolder.getTenantId(); }
}
