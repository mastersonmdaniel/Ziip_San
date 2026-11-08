package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.core.api.ApiException;

import java.time.Instant;
import java.util.UUID;

/**
 * Denuncia de golpe pelo portal (P3.35): o consumidor relata uma cobranca
 * suspeita (canal, valor relatado — link, numero ou chave — e descricao) e
 * a denuncia (a) fica registrada (V8_3) e (b) alimenta um alerta na Central
 * (aba Seguranca), pelo {@link CentralAlertPort}.
 *
 * <p>A denuncia carrega SOMENTE o que o denunciante declarou sobre o golpe:
 * nenhum dado de titular e lido ou gravado. O contato do denunciante e
 * opcional e so existe se ele o informar para retorno. Limite por origem
 * como nos demais caminhos publicos: estouro = 429 e nada e registrado
 * como denuncia (o abuso do formulario nao vira alerta falso em massa).
 * Correcao D6 da revisao cruzada: o excesso NAO e contado a parte — esta
 * classe nao registra o 429 em porta alguma (a {@link FraudReportPort}
 * nao tem operacao para isso); quem observa o volume de 429 e a borda/
 * telemetria de requisicoes, nao este servico. (Na verificacao de fatura,
 * ao contrario, o RATE_LIMITED E registrado pelo proprio servico.)
 */
public final class FraudReportService {

    public enum ReportedChannel { LINK, MESSAGE, EMAIL, PHONE_CALL, SOCIAL, OTHER }

    /** A denuncia como registrada: so o declarado pelo denunciante. */
    public record FraudReport(UUID tenantId, ReportedChannel channel, String reportedValue,
                              String description, String reporterContact, String originKey,
                              Instant reportedAt) {
        public FraudReport {
            if (tenantId == null || channel == null || reportedAt == null) {
                throw new IllegalArgumentException("denuncia exige tenant, canal e instante");
            }
            if (reportedValue == null || reportedValue.isBlank()) {
                throw new IllegalArgumentException("denuncia exige o valor relatado (link, numero ou chave suspeita)");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("denuncia exige a descricao do ocorrido");
            }
            if (reportedValue.length() > 500 || description.length() > 2000) {
                throw new IllegalArgumentException("denuncia acima do tamanho maximo (valor 500, descricao 2000)");
            }
            reportedValue = reportedValue.strip();
            description = description.strip();
            reporterContact = reporterContact == null || reporterContact.isBlank() ? null : reporterContact.strip();
            originKey = originKey == null ? null : originKey.strip();
        }
    }

    /** Alerta que a denuncia levanta na Central (aba Seguranca). */
    public record CentralFraudAlert(UUID tenantId, ReportedChannel channel, Instant reportedAt) { }

    public interface FraudReportPort {
        int reportsByOrigin(UUID tenantId, String originKey);
        void saveReport(FraudReport report);
    }

    public interface CentralAlertPort {
        void raiseFraudReportAlert(CentralFraudAlert alert);
    }

    private final FraudReportPort reports;
    private final CentralAlertPort central;
    private final int maxReportsPerOrigin;

    public FraudReportService(FraudReportPort reports, CentralAlertPort central, int maxReportsPerOrigin) {
        if (reports == null || central == null) {
            throw new IllegalArgumentException("port de denuncias e port da Central sao obrigatorios");
        }
        if (maxReportsPerOrigin < 1) {
            throw new IllegalArgumentException("limite de denuncias por origem deve ser >= 1");
        }
        this.reports = reports;
        this.central = central;
        this.maxReportsPerOrigin = maxReportsPerOrigin;
    }

    public FraudReport report(UUID tenantId, String originKey, ReportedChannel channel,
                              String reportedValue, String description, String reporterContact, Instant now) {
        if (tenantId == null || originKey == null || originKey.isBlank() || now == null) {
            throw new IllegalArgumentException("tenant, origem e instante sao obrigatorios");
        }
        if (reports.reportsByOrigin(tenantId, originKey) >= maxReportsPerOrigin) {
            throw new ApiException.TooManyRequests("Muitas denuncias desta origem. Aguarde e tente novamente.");
        }
        FraudReport report = new FraudReport(tenantId, channel, reportedValue, description,
                reporterContact, originKey, now);
        reports.saveReport(report);
        central.raiseFraudReportAlert(new CentralFraudAlert(tenantId, channel, now));
        return report;
    }
}
