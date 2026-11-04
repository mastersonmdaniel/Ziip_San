package br.com.ziipsan.app.job;

import br.com.ziipsan.core.certification.NormEffectivenessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.List;

/**
 * Transicao diaria de vigencia do catalogo de normas (P1.34).
 *
 * <p>Segue o padrao do CentralCollectorScheduler (P1.17): so existe com
 * {@code ziipsan.norm.effectiveness.enabled=true} (padrao desligado — o
 * processo recomendado e o worker, e a trava de advisory lock do
 * servico impede duas execucoes simultaneas no cluster caso a api
 * tambem o ligue). O intervalo vem de
 * {@code ziipsan.norm.effectiveness.interval-ms} (padrao 86400000, um
 * dia); a primeira execucao apos a subida ja vira as normas cuja data
 * chegou durante o desligamento, porque a regra e por data, nao por
 * evento — desligar o job atrasa a transicao, nunca a perde.
 *
 * <p>Alem da transicao, cada execucao registra em WARN as normas em
 * vigencia futura com data de efeito dentro da janela de
 * {@code ziipsan.norm.effectiveness.alert-window-days} (padrao 30): e o
 * alerta antecipado do plano. A entrega desse alerta ao responsavel do
 * tenant ainda nao tem canal no modulo de certificacao (ver
 * NormEffectivenessService); o log e o relatorio do servico sao o
 * registro ate la.
 */
@Slf4j
@Component
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ziipsan.norm.effectiveness", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class NormEffectivenessScheduler {

    private final DataSource dataSource;

    @Value("${ziipsan.norm.effectiveness.alert-window-days:30}")
    private int alertWindowDays;

    @Scheduled(fixedDelayString = "${ziipsan.norm.effectiveness.interval-ms:86400000}")
    public void transition() {
        NormEffectivenessService service = new NormEffectivenessService(dataSource, "platform");
        NormEffectivenessService.TransitionReport report = service.transitionDue();
        if (!report.lockAcquired()) {
            log.info("Transicao de vigencia das normas adiada: outra execucao detem a trava.");
            return;
        }
        log.info("Transicao de vigencia das normas concluida: {} norma(s) viraram VIGENTE em {}: {}",
                report.transitioned().size(), report.asOf(),
                report.transitioned().stream()
                        .map(NormEffectivenessService.TransitionedView::catalogKey).toList());
        List<NormEffectivenessService.UpcomingView> upcoming =
                service.upcomingEffective(report.asOf(), alertWindowDays);
        if (!upcoming.isEmpty()) {
            log.warn("Alerta antecipado de vigencia: {} norma(s) em vigencia futura com data de efeito"
                            + " nos proximos {} dias: {}",
                    upcoming.size(), alertWindowDays,
                    upcoming.stream()
                            .map(u -> u.catalogKey() + " em " + u.effectiveFrom()).toList());
        }
    }
}
