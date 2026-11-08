package br.com.ziipsan.intelligence.gis.thematic.web;

import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.PublicThematicView;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canal público dos temáticos (fora do backoffice autenticado,
 * §12 item 3): SEM {@code @PreAuthorize} de propósito, no mesmo
 * desenho do caminho de convidado da P3.3 — a rota entra na cadeia
 * pública da aplicação (portal/borda), e a fiação dessa cadeia é
 * da integração da app, não deste módulo. O tenant vem do contexto
 * (resolução por domínio, P2.27), nunca de parâmetro.
 *
 * <p>A regra de conteúdo é do serviço: fonte pessoal só agregada
 * por quadra/setor com o mínimo de unidades (célula menor não
 * aparece), OS nunca, rede e precisão de ativos integrais por
 * categoria. Nenhum ponto de imóvel ou ligação sai por aqui.
 */
@RestController
@RequestMapping(GisThematicRoles.PUBLIC_BASE)
public class GisPublicThematicController {

    private final GisThematicService service;

    public GisPublicThematicController(GisThematicService service) {
        this.service = service;
    }

    @GetMapping("/{key}/aggregate")
    public PublicThematicView aggregate(@PathVariable String key) {
        return service.renderPublic(key);
    }
}
