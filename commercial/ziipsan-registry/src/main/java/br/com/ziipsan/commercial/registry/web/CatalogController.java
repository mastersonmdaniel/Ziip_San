package br.com.ziipsan.commercial.registry.web;

import br.com.ziipsan.commercial.registry.application.CatalogDtos.CategoryInput;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.CategoryView;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.SewerStatusInput;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.SewerStatusView;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.SubcategoryInput;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.SubcategoryView;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.WaterStatusInput;
import br.com.ziipsan.commercial.registry.application.CatalogDtos.WaterStatusView;
import br.com.ziipsan.commercial.registry.application.CatalogService;
import br.com.ziipsan.commercial.registry.application.PageResult;
import br.com.ziipsan.commercial.registry.application.PageSpec;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Catálogos com tabela: {@code categories}, {@code subcategories}, {@code water-statuses} e
 * {@code sewer-statuses}. Sem DELETE (desativar com {@code active=false}); os demais catálogos da tela
 * (perfil do imóvel, tipo de cliente, pavimento...) não têm tabela e não têm rota.
 */
@RestController
@RequestMapping(RegistryRoles.BASE)
@PreAuthorize(RegistryRoles.READ)
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public PageResult<CategoryView> listCategories(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Boolean active,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size,
                                                   @RequestParam(required = false) String sort) {
        return service.listCategories(q, active, PageSpec.of(page, size, sort));
    }

    @GetMapping("/categories/{id}")
    public CategoryView getCategory(@PathVariable UUID id) {
        return service.getCategory(id);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public CategoryView createCategory(@Valid @RequestBody CategoryInput in) {
        return service.createCategory(in);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public CategoryView updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryInput in) {
        return service.updateCategory(id, in);
    }

    @GetMapping("/subcategories")
    public PageResult<SubcategoryView> listSubcategories(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) UUID categoryId,
                                                         @RequestParam(required = false) Boolean active,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size,
                                                         @RequestParam(required = false) String sort) {
        return service.listSubcategories(q, categoryId, active, PageSpec.of(page, size, sort));
    }

    @GetMapping("/subcategories/{id}")
    public SubcategoryView getSubcategory(@PathVariable UUID id) {
        return service.getSubcategory(id);
    }

    @PostMapping("/subcategories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public SubcategoryView createSubcategory(@Valid @RequestBody SubcategoryInput in) {
        return service.createSubcategory(in);
    }

    @PutMapping("/subcategories/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public SubcategoryView updateSubcategory(@PathVariable UUID id, @Valid @RequestBody SubcategoryInput in) {
        return service.updateSubcategory(id, in);
    }

    @GetMapping("/water-statuses")
    public PageResult<WaterStatusView> listWaterStatuses(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) Boolean active,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size,
                                                         @RequestParam(required = false) String sort) {
        return service.listWaterStatuses(q, active, PageSpec.of(page, size, sort));
    }

    @GetMapping("/water-statuses/{id}")
    public WaterStatusView getWaterStatus(@PathVariable UUID id) {
        return service.getWaterStatus(id);
    }

    @PostMapping("/water-statuses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public WaterStatusView createWaterStatus(@Valid @RequestBody WaterStatusInput in) {
        return service.createWaterStatus(in);
    }

    @PutMapping("/water-statuses/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public WaterStatusView updateWaterStatus(@PathVariable UUID id, @Valid @RequestBody WaterStatusInput in) {
        return service.updateWaterStatus(id, in);
    }

    @GetMapping("/sewer-statuses")
    public PageResult<SewerStatusView> listSewerStatuses(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) Boolean active,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size,
                                                         @RequestParam(required = false) String sort) {
        return service.listSewerStatuses(q, active, PageSpec.of(page, size, sort));
    }

    @GetMapping("/sewer-statuses/{id}")
    public SewerStatusView getSewerStatus(@PathVariable UUID id) {
        return service.getSewerStatus(id);
    }

    @PostMapping("/sewer-statuses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(RegistryRoles.WRITE)
    public SewerStatusView createSewerStatus(@Valid @RequestBody SewerStatusInput in) {
        return service.createSewerStatus(in);
    }

    @PutMapping("/sewer-statuses/{id}")
    @PreAuthorize(RegistryRoles.WRITE)
    public SewerStatusView updateSewerStatus(@PathVariable UUID id, @Valid @RequestBody SewerStatusInput in) {
        return service.updateSewerStatus(id, in);
    }
}
