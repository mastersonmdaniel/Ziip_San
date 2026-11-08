package br.com.ziipsan.platform.portal.verify.web;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.platform.portal.PortalParameters;
import br.com.ziipsan.platform.portal.verify.FraudProtectionParameters;
import br.com.ziipsan.platform.portal.verify.FraudProtectionTexts;
import br.com.ziipsan.platform.portal.verify.FraudReportService;
import br.com.ziipsan.platform.portal.verify.OfficialChannels;
import br.com.ziipsan.platform.portal.verify.PublicBillVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P3.35: rotas publicas de autenticidade (familia {@code /api/v1/public/*},
 * como status da P3.18 e Trust Center da P3.38 — a seguranca da aplicacao
 * libera estas rotas sem autenticacao na integracao):
 *
 * <ul>
 *   <li>{@code GET /api/v1/public/documents/verify?code=...} — a rota do
 *       aceite. O codigo de verificacao NAO e dado pessoal: ele existe para
 *       ser impresso e codificado no QR (URL), ao contrario do documento do
 *       titular, que a P3.3 mantem fora de URL (RNF-52). A resposta e o
 *       {@link PublicBillVerificationService.VerificationResult} serializado
 *       pela {@code Json} do core: sem titular, endereco ou documento;</li>
 *   <li>{@code GET /api/v1/public/documents/official-channels} — canais
 *       oficiais conferidos contra {@code branding.contact}, o alerta de
 *       golpe configurado e o texto padrao anti-link-avulso;</li>
 *   <li>{@code POST /api/v1/public/documents/report-fraud} — denuncia de
 *       golpe do portal (corpo, nunca URL), que alimenta a Central.</li>
 * </ul>
 * Tenant vem do contexto (resolucao por dominio, P2.27); a origem do limite
 * e o endereco da requisicao, como no caminho de convidado da P3.3.
 */
@RestController
public class PublicDocumentVerifyController {

    /** Fonte dos canais oficiais ja conferidos e do contato da marca (adaptador = integracao). */
    public interface OfficialChannelsSource {
        OfficialChannels officialChannels(java.util.UUID tenantId);
    }

    public record FraudReportRequest(String channel, String reportedValue, String description,
                                     String reporterContact) { }

    private final PublicBillVerificationService verification;
    private final FraudReportService fraudReports;
    private final FraudProtectionParameters parameters;
    private final PortalParameters portalParameters;
    private final PortalParameters.AccountThrottle factoryThrottle;
    private final OfficialChannelsSource channelsSource;

    public PublicDocumentVerifyController(PublicBillVerificationService verification,
                                          FraudReportService fraudReports,
                                          FraudProtectionParameters parameters,
                                          PortalParameters portalParameters,
                                          PortalParameters.AccountThrottle factoryThrottle,
                                          OfficialChannelsSource channelsSource) {
        this.verification = verification;
        this.fraudReports = fraudReports;
        this.parameters = parameters;
        this.portalParameters = portalParameters;
        this.factoryThrottle = factoryThrottle;
        this.channelsSource = channelsSource;
    }

    @GetMapping(value = "/api/v1/public/documents/verify", produces = MediaType.APPLICATION_JSON_VALUE)
    public String verify(@RequestParam("code") String code, HttpServletRequest http) {
        var result = verification.verify(TenantContextHolder.getTenantId(), http.getRemoteAddr(), code,
                parameters, portalParameters.accountThrottle(factoryThrottle), Instant.now());
        return Json.write(result);
    }

    @GetMapping(value = "/api/v1/public/documents/official-channels", produces = MediaType.APPLICATION_JSON_VALUE)
    public String officialChannels() {
        java.util.UUID tenantId = TenantContextHolder.getTenantId();
        OfficialChannels channels = channelsSource.officialChannels(tenantId);
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> list = channels.channels().stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", c.type().name());
            m.put("label", c.label());
            m.put("value", c.value());
            return m;
        }).toList();
        body.put("officialChannels", list);
        body.put("neverLooseLinkText", FraudProtectionTexts.NEVER_LOOSE_LINK_TEXT);
        var alert = parameters.scamAlert();
        body.put("scamAlertEnabled", alert.enabled());
        if (alert.enabled()) {
            body.put("scamAlertText", alert.text());
        }
        return Json.write(body);
    }

    @PostMapping(value = "/api/v1/public/documents/report-fraud", produces = MediaType.APPLICATION_JSON_VALUE)
    public String reportFraud(@RequestBody FraudReportRequest request, HttpServletRequest http) {
        // Validacao de entrada (correcao D5 da revisao cruzada): corpo ausente,
        // canal nulo/desconhecido ou campos invalidos sao erro DO CLIENTE —
        // ApiException.BadRequest (400, problem+json pelo tratador global),
        // nunca IllegalArgumentException/NullPointerException soltos (500).
        if (request == null) {
            throw new ApiException.BadRequest("fraud-report-invalid",
                    "Denuncia sem corpo: informe canal, valor relatado e descricao.");
        }
        FraudReportService.ReportedChannel channel = parseChannel(request.channel());
        FraudReportService.FraudReport report;
        try {
            report = fraudReports.report(TenantContextHolder.getTenantId(), http.getRemoteAddr(),
                    channel, request.reportedValue(), request.description(),
                    request.reporterContact(), Instant.now());
        } catch (IllegalArgumentException e) {
            // As recusas de validacao do FraudReport (valor/descricao vazios ou
            // acima do tamanho) tambem sao erro do cliente: 400, nao 500.
            // TooManyRequests (429) e ApiException, nao IllegalArgumentException:
            // atravessa intacta.
            throw new ApiException.BadRequest("fraud-report-invalid", e.getMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("received", true);
        body.put("reportedAt", report.reportedAt().toString());
        return Json.write(body);
    }

    private static FraudReportService.ReportedChannel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new ApiException.BadRequest("fraud-report-invalid",
                    "Denuncia sem canal: informe o canal da cobranca suspeita.");
        }
        try {
            return FraudReportService.ReportedChannel.valueOf(channel.strip());
        } catch (IllegalArgumentException e) {
            throw new ApiException.BadRequest("fraud-report-invalid",
                    "Canal de denuncia desconhecido: " + channel.strip());
        }
    }
}
