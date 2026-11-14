package br.com.ziipsan.app.job;

import br.com.ziipsan.commercial.fiscal.relief.CertificateAlertPlanner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Processo diario de alerta de vencimento do certificado A1 (DESENHO-PA02
 * secao 7.3; F-21: limiares 30/15/7 dias). Segue o padrao dos schedulers da
 * aplicacao (ex.: NormEffectivenessScheduler): desligado por omissao, liga
 * com {@code ziipsan.fiscal.certificate-alert.enabled=true}.
 *
 * <p>O job e AVISO; quem bloqueia a emissao com certificado vencido e a
 * guarda sincrona {@code CertificateEmissionGuard} no acto de emitir. A
 * idempotencia (certificado, limiar, dia) vem do registo
 * {@code fiscal_certificate_alert_log} (M3), lido pela porta
 * {@link CertificateAlertLogPort}.
 */
@Component
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ziipsan.fiscal.certificate-alert", name = "enabled",
        havingValue = "true")
public class CertificateAlertScheduler {

    /** Fonte dos certificados de referencia por tenant/emitente. */
    public interface CertificateSourcePort {
        List<CertificateAlertPlanner.TenantCertificate> referenceCertificates();
    }

    /** Registo de alertas (tabela M3) + despacho pelo canal existente. */
    public interface CertificateAlertLogPort {
        Set<CertificateAlertPlanner.AlertKey> alreadyAlerted();

        void recordAndDispatch(CertificateAlertPlanner.PlannedAlert alert);
    }

    private final CertificateAlertPlanner planner;
    private final CertificateSourcePort source;
    private final CertificateAlertLogPort log;

    public CertificateAlertScheduler(CertificateAlertPlanner planner, CertificateSourcePort source,
                                     CertificateAlertLogPort log) {
        this.planner = planner;
        this.source = source;
        this.log = log;
    }

    @Scheduled(cron = "${ziipsan.fiscal.certificate-alert.cron:0 30 6 * * *}")
    public void runDaily() {
        List<CertificateAlertPlanner.PlannedAlert> planned = planner.plan(
                source.referenceCertificates(), OffsetDateTime.now(), log.alreadyAlerted());
        planned.forEach(log::recordAndDispatch);
    }
}
