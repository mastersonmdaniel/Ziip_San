package br.com.ziipsan.platform.statuspage.web;

import br.com.ziipsan.platform.statuspage.PublicStatusService;
import br.com.ziipsan.platform.statuspage.StatusPageSource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * P3.18: endpoint público da página de status —
 * {@code GET /api/v1/public/status} (rota mantida pelo plano v3.0).
 *
 * <p>Público de verdade: sem autenticação (a página é pública por
 * definição do aceite) e sem nenhum dado de tenant — a visão montada por
 * {@link PublicStatusService} só contém componentes, incidentes abertos,
 * janelas e resumos JÁ publicados. O corpo é o JSON da {@code Json} do
 * core via {@code PublicStatusService.toJson}; a página estática em
 * {@code docs/status-page/} consome este endpoint e, na LC.7, é
 * publicada em hospedagem separada da produção apontando para ele.
 *
 * <p>A segurança da aplicação precisa liberar esta rota sem autenticação
 * na integração (padrão das rotas {@code /api/v1/public/*}); isso é
 * fiação declarada no relatório, não uma anotação inventada aqui.
 */
@RestController
public class PublicStatusController {

    private final StatusPageSource source;

    public PublicStatusController(StatusPageSource source) {
        this.source = source;
    }

    @GetMapping(value = "/api/v1/public/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public String status() {
        PublicStatusService.PublicStatusView view = PublicStatusService.buildView(
                source.components(), source.latestReadings(), source.incidents(),
                source.maintenanceWindows(), Instant.now());
        return PublicStatusService.toJson(view);
    }
}
