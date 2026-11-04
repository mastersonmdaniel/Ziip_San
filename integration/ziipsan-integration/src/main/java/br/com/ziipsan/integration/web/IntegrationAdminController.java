package br.com.ziipsan.integration.web;

import br.com.ziipsan.integration.service.ApiClientService;
import br.com.ziipsan.integration.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Telas do tenant para a API pública (P2.26): credenciais (criar, girar,
 * revogar, ver o último uso) e webhooks (endpoints, entregas, reenvio).
 *
 * <p>Autenticação: token de tenant (JWT do usuário, cadeia normal da
 * API do frontend — estas são telas do backoffice, não da API pública).
 * A permissão fina é {@code integration:manage}, nos perfis de fábrica
 * ADMIN (P1.37: permissão nova atribuída a perfil, nunca só concessão
 * direta). O segredo em claro só volta na criação e na rotação; a
 * listagem nunca o inclui.
 */
@RestController
@RequestMapping("/api/v1/tenant/integration")
@PreAuthorize("hasAuthority('integration:manage')")
public class IntegrationAdminController {

    public record CreateEndpointInput(String url, List<String> eventTypes) {
    }

    private final ApiClientService clients;
    private final WebhookService webhooks;

    public IntegrationAdminController(ApiClientService clients, WebhookService webhooks) {
        this.clients = clients;
        this.webhooks = webhooks;
    }

    // ---------------------------------------------------------------- clientes
    @GetMapping("/clients")
    public List<ApiClientService.ClientView> listClients() {
        return clients.list();
    }

    @GetMapping("/clients/{id}")
    public ApiClientService.ClientView getClient(@PathVariable UUID id) {
        return clients.get(id);
    }

    @PostMapping("/clients")
    public ResponseEntity<ApiClientService.CreatedClient> createClient(
            @RequestBody ApiClientService.CreateInput in) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clients.create(in));
    }

    @PostMapping("/clients/{id}/rotate-secret")
    public ApiClientService.CreatedClient rotateClientSecret(@PathVariable UUID id) {
        return clients.rotateSecret(id);
    }

    @PostMapping("/clients/{id}/revoke")
    public void revokeClient(@PathVariable UUID id) {
        clients.revoke(id);
    }

    // ---------------------------------------------------------------- webhooks
    @GetMapping("/webhooks/endpoints")
    public List<WebhookService.EndpointView> listEndpoints() {
        return webhooks.listEndpoints();
    }

    @PostMapping("/webhooks/endpoints")
    public ResponseEntity<WebhookService.CreatedEndpoint> createEndpoint(@RequestBody CreateEndpointInput in) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(webhooks.createEndpoint(in.url(), in.eventTypes()));
    }

    @PostMapping("/webhooks/endpoints/{id}/rotate-secret")
    public WebhookService.CreatedEndpoint rotateEndpointSecret(@PathVariable UUID id) {
        return webhooks.rotateEndpointSecret(id);
    }

    @PostMapping("/webhooks/endpoints/{id}/disable")
    public void disableEndpoint(@PathVariable UUID id) {
        webhooks.disableEndpoint(id);
    }

    @PostMapping("/webhooks/endpoints/{id}/enable")
    public void enableEndpoint(@PathVariable UUID id) {
        webhooks.enableEndpoint(id);
    }

    @PostMapping("/webhooks/endpoints/{id}/send-test")
    public UUID sendTest(@PathVariable UUID id) {
        return webhooks.sendTest(id);
    }

    @GetMapping("/webhooks/deliveries")
    public List<WebhookService.DeliveryView> listDeliveries(
            @RequestParam(required = false) UUID endpointId,
            @RequestParam(required = false) String status) {
        return webhooks.listDeliveries(endpointId, status);
    }

    @PostMapping("/webhooks/deliveries/{id}/retry")
    public void retryDelivery(@PathVariable UUID id) {
        webhooks.retryDelivery(id);
    }
}
