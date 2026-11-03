package br.com.ziipsan.intelligence.gis.catalog.web;

import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ActiveInput;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ClassView;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ItemInput;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ItemView;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.MaterialView;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.ReferenceReport;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.TypeInput;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogDtos.TypeView;
import br.com.ziipsan.intelligence.gis.catalog.application.GisCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo GIS do tenant (P1.27): classes, tipos, itens e materiais de fábrica
 * (R__gis_factory_catalog.sql) e do tenant, com aprovação por outra pessoa.
 * Sem DELETE: o que sai de uso é desativado e continua resolvendo as feições
 * que já o referenciam.
 */
@RestController
@RequestMapping(GisCatalogRoles.BASE)
@PreAuthorize(GisCatalogRoles.READ)
public class GisCatalogController {

    private final GisCatalogService service;

    public GisCatalogController(GisCatalogService service) {
        this.service = service;
    }

    @GetMapping("/classes")
    public List<ClassView> listClasses() {
        return service.listClasses();
    }

    @GetMapping("/types")
    public List<TypeView> listTypes(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) Boolean active,
                                    @RequestParam(required = false) Boolean usable) {
        return service.listTypes(q, active, usable);
    }

    @GetMapping("/types/{id}")
    public TypeView getType(@PathVariable UUID id) {
        return service.getType(id);
    }

    @PostMapping("/types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisCatalogRoles.MANAGE)
    public TypeView createType(@Valid @RequestBody TypeInput in) {
        return service.createType(in);
    }

    @PostMapping("/types/{id}/approve")
    @PreAuthorize(GisCatalogRoles.MANAGE)
    public TypeView approveType(@PathVariable UUID id) {
        return service.approveType(id);
    }

    @PostMapping("/types/{id}/reject")
    @PreAuthorize(GisCatalogRoles.MANAGE)
    public TypeView rejectType(@PathVariable UUID id) {
        return service.rejectType(id);
    }

    @PatchMapping("/types/{id}/active")
    @PreAuthorize(GisCatalogRoles.MANAGE)
    public TypeView setTypeActive(@PathVariable UUID id, @Valid @RequestBody ActiveInput in) {
        return service.setTypeActive(id, in);
    }

    @GetMapping("/items")
    public List<ItemView> listItems(@RequestParam(required = false) UUID featureTypeId,
                                    @RequestParam(required = false) Boolean active,
                                    @RequestParam(required = false) Boolean usable,
                                    @RequestParam(required = false) String referenceStatus) {
        return service.listItems(featureTypeId, active, usable, referenceStatus);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisCatalogRoles.MANAGE)
    public ItemView createItem(@Valid @RequestBody ItemInput in) {
        return service.createItem(in);
    }

    @PostMapping("/items/{id}/approve")
    @PreAuthorize(GisCatalogRoles.MANAGE)
    public ItemView approveItem(@PathVariable UUID id) {
        return service.approveItem(id);
    }

    @PostMapping("/items/{id}/reject")
    @PreAuthorize(GisCatalogRoles.MANAGE)
    public ItemView rejectItem(@PathVariable UUID id) {
        return service.rejectItem(id);
    }

    @PatchMapping("/items/{id}/active")
    @PreAuthorize(GisCatalogRoles.MANAGE)
    public ItemView setItemActive(@PathVariable UUID id, @Valid @RequestBody ActiveInput in) {
        return service.setItemActive(id, in);
    }

    @GetMapping("/materials")
    public List<MaterialView> listMaterials() {
        return service.listMaterials();
    }

    @GetMapping("/report/reference-to-validate")
    public ReferenceReport referenceReport() {
        return service.referenceReport();
    }
}
