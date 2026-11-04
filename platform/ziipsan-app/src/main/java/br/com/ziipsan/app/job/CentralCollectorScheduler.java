package br.com.ziipsan.app.job;

import br.com.ziipsan.platform.central.CentralCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Agendamento do coletor da Central (P1.17, CENTRAL-ZIIP §5: a cada 5 min).
 *
 * <p>So existe com {@code ziipsan.central.collector.enabled=true} (padrao
 * desligado): o processo recomendado e o worker, e a trava de advisory
 * lock do coletor impede duas execucoes simultaneas no cluster caso a
 * api tambem o ligue. O intervalo vem de
 * {@code ziipsan.central.collector.interval-ms} (padrao 300000).
 */
@Slf4j
@Component
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ziipsan.central.collector", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class CentralCollectorScheduler {

    private final CentralCollectorService collector;

    @Scheduled(fixedDelayString = "${ziipsan.central.collector.interval-ms:300000}")
    public void collect() {
        CentralCollectorService.Report report = collector.collect();
        log.info("Coleta da Central concluida: {}", report);
    }
}
