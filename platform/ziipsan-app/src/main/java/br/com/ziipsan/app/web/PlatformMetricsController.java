package br.com.ziipsan.app.web;

import br.com.ziipsan.platform.central.CentralService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * API de metricas v1 da Central (P1.17, CENTRAL-ZIIP §8).
 *
 * <p>So leitura, sob o mesmo gate de plataforma das demais rotas
 * ({@code /api/v1/platform/*}: MFA obrigatorio, token sem tenant, papel
 * {@code PLATFORM_*} com {@code central:read}). Envelope estavel:
 * {@code schema_version}, {@code product} ({@code ziip-san}),
 * {@code instance}, {@code collected_at} e {@code points}, cada ponto com
 * {@code key}, {@code tenant_ref} (o UUID do catalogo, nunca nome ou
 * documento), {@code value}, {@code unit} e {@code labels}. Mudanca
 * aditiva na mesma versao; remocao ou mudanca de significado so em v2.
 *
 * <p>Limite honesto da v0: a autenticacao de servico externa (client
 * credentials e mTLS) do §8 ainda nao existe nesta base — o consumo
 * externo usa os mesmos papeis de plataforma. O contrato (rotas,
 * envelope, chaves) e o que esta entrega congela.
 */
@RestController
@RequestMapping("/api/v1/platform/metrics")
public class PlatformMetricsController {

    public record Envelope(String schemaVersion, String product, String instance,
                           OffsetDateTime collectedAt, List<CentralService.MetricPoint> points) {
    }

    public record CatalogEnvelope(String schemaVersion, String product, String instance,
                                  OffsetDateTime collectedAt, List<CentralService.MetricDefinition> definitions) {
    }

    private final CentralService central;
    private final String instance;

    public PlatformMetricsController(CentralService central,
                                     @Value("${ziipsan.instance:default}") String instance) {
        this.central = central;
        this.instance = instance;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('central:read')")
    public CatalogEnvelope catalog() {
        return new CatalogEnvelope("1", "ziip-san", instance, now(), central.catalog());
    }

    @GetMapping("/snapshot")
    @PreAuthorize("hasAuthority('central:read')")
    public Envelope snapshot() {
        return new Envelope("1", "ziip-san", instance, now(), central.snapshot());
    }

    @GetMapping("/series")
    @PreAuthorize("hasAuthority('central:read')")
    public Envelope series(@RequestParam String key,
                           @RequestParam(required = false) UUID tenant,
                           @RequestParam OffsetDateTime from,
                           @RequestParam OffsetDateTime to,
                           @RequestParam(defaultValue = "5m") String step) {
        return new Envelope("1", "ziip-san", instance, now(), central.series(key, tenant, from, to, step));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('central:read')")
    public List<CentralService.AlertView> alerts(@RequestParam(defaultValue = "open") String state) {
        return central.alerts(state);
    }

    @GetMapping("/health-scores")
    @PreAuthorize("hasAuthority('central:read')")
    public List<CentralService.HealthScore> healthScores() {
        return central.healthScores();
    }
}
