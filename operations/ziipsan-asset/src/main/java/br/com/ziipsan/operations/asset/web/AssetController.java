package br.com.ziipsan.operations.asset.web;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.operations.asset.AssetService;
import br.com.ziipsan.operations.asset.AssetService.AssetView;
import br.com.ziipsan.operations.asset.AssetService.ConfinedSpaceRow;
import br.com.ziipsan.operations.asset.AssetService.EquipmentClassView;
import br.com.ziipsan.operations.asset.web.AssetDtos.AssetInputDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

/**
 * API do cadastro minimo de ativos (P1.35). Adaptador fino sobre o
 * {@link AssetService}: a regra (escrita so por OS interna, hierarquia,
 * MCPSB, espaco confinado) vive no servico e e a mesma usada pelos testes
 * de integracao, sem Spring.
 */
@RestController
@RequestMapping(AssetRoles.BASE)
@PreAuthorize(AssetRoles.READ)
public class AssetController {

    private final DataSource dataSource;

    public AssetController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private AssetService service() {
        String schema = TenantContextHolder.getTenant();
        UUID tenantId = TenantContextHolder.getTenantId();
        if (schema == null || tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisicao");
        }
        return new AssetService(dataSource, schema, tenantId);
    }

    @GetMapping
    public List<AssetView> list() {
        return service().listAssets();
    }

    @GetMapping("/{id}")
    public AssetView get(@PathVariable UUID id) {
        return service().getAsset(id);
    }

    @GetMapping("/confined-spaces")
    public List<ConfinedSpaceRow> confinedSpaces() {
        return service().listConfinedSpaces();
    }

    @GetMapping("/equipment-classes")
    public List<EquipmentClassView> equipmentClasses() {
        return service().listEquipmentClasses();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(AssetRoles.MANAGE)
    public AssetView create(@Valid @RequestBody AssetInputDto in) {
        UUID id = service().createAsset(in.orderId(), in.toInput(), TenantContextHolder.getUserId());
        return service().getAsset(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(AssetRoles.MANAGE)
    public AssetView update(@PathVariable UUID id, @Valid @RequestBody AssetInputDto in) {
        AssetService service = service();
        service.updateAsset(in.orderId(), id, in.toInput(), TenantContextHolder.getUserId());
        return service.getAsset(id);
    }
}
