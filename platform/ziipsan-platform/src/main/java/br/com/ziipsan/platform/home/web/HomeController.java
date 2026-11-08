package br.com.ziipsan.platform.home.web;

import br.com.ziipsan.core.agenda.AgendaEvent;
import br.com.ziipsan.platform.home.AgendaService;
import br.com.ziipsan.platform.home.HomePage;
import br.com.ziipsan.platform.home.HomePageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API da pagina inicial e da agenda (P3.39), no espirito das APIs
 * /api/v1/me/* das P2.28/P3.36: o {@code sub} do token e o dono de toda
 * leitura — ninguem pede a pagina nem a agenda de outro usuario.
 *
 * <p>SO GET, por contrato e por teste: nao existe POST/PUT/DELETE nesta
 * classe porque a agenda nao cria nem altera evento e a pagina inicial e
 * composicao de leitura. O perfil vem do chamador (a sessao/fiacao resolve
 * o perfil ativo do usuario a partir das atribuicoes da P1.37); perfil fora
 * do catalogo de fabrica e recusado pelo servico com 422 nomeado.
 *
 * <p>O tenant NAO vem do cliente: ele e o do contexto autenticado da
 * instalacao (o servico e instanciado por tenant, como os servicos das
 * P2.28/P3.36) — por isso o controller recebe o tenantId ja resolvido na
 * fiacao em vez de um parametro de requisicao. A fiacao dos beans (quem
 * instancia os servicos e liga as portas) e da integracao, como nas
 * frentes vizinhas.
 */
@RestController
@RequestMapping("/api/v1/me")
@PreAuthorize("isAuthenticated()")
public class HomeController {

    private final HomePageService homeService;
    private final AgendaService agendaService;
    private final UUID tenantId;

    public HomeController(HomePageService homeService, AgendaService agendaService, UUID tenantId) {
        this.homeService = homeService;
        this.agendaService = agendaService;
        this.tenantId = tenantId;
    }

    /** Pagina inicial do usuario no perfil pedido (item de menu Inicio, rota /inicio). */
    @GetMapping("/home")
    public HomePage home(@AuthenticationPrincipal(expression = "subject") String subject,
                         @RequestParam("profile") String profile) {
        return homeService.compose(tenantId, UUID.fromString(subject), profile);
    }

    /** Tipos de agenda registrados e a disponibilidade de cada fonte. */
    @GetMapping("/agenda/types")
    public List<AgendaService.TypeAvailability> agendaTypes() {
        return agendaService.types();
    }

    /** Eventos da agenda do tenant na janela [from, to). Somente leitura. */
    @GetMapping("/agenda")
    public List<AgendaEvent> agenda(@RequestParam("from") Instant from,
                                    @RequestParam("to") Instant to) {
        return agendaService.eventsInWindow(tenantId, from, to);
    }
}
