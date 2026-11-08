package br.com.ziipsan.intelligence.gis.thematic.web;

import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ThematicDataView;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ThematicInput;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ThematicView;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Temáticos da GIS-08 no backoffice: definições (fábrica + tenant),
 * dados com legenda automática e o filtro por categoria do SG-883
 * ({@code ?categoryCode=}). A leitura pede {@code gis:view}; a
 * fonte pessoal pede {@code gis:view-personal} dentro do serviço;
 * a escrita pede {@code gis:thematic-manage}.
 */
@RestController
@RequestMapping(GisThematicRoles.THEMATICS_BASE)
@PreAuthorize(GisThematicRoles.VIEW)
public class GisThematicController {

    private final GisThematicService service;

    public GisThematicController(GisThematicService service) {
        this.service = service;
    }

    @GetMapping
    public List<ThematicView> list() {
        return service.list();
    }

    @GetMapping("/{key}")
    public ThematicView get(@PathVariable String key) {
        return service.get(key);
    }

    @GetMapping("/{key}/data")
    public ThematicDataView data(@PathVariable String key,
                                 @RequestParam(required = false) String categoryCode) {
        return service.render(key, categoryCode);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(GisThematicRoles.MANAGE)
    public ThematicView create(@Valid @RequestBody ThematicInput input) {
        return service.create(input);
    }

    @PutMapping("/{key}")
    @PreAuthorize(GisThematicRoles.MANAGE)
    public ThematicView update(@PathVariable String key,
                               @Valid @RequestBody ThematicInput input) {
        return service.update(key, input);
    }

    @PostMapping("/{key}/activation")
    @PreAuthorize(GisThematicRoles.MANAGE)
    public Map<String, Boolean> activation(@PathVariable String key,
                                           @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        service.setActive(key, active);
        return Map.of("active", active);
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(GisThematicRoles.MANAGE)
    public void delete(@PathVariable String key) {
        service.delete(key);
    }
}
