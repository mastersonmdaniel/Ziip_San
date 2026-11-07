package br.com.ziipsan.platform.statuspage;

import java.util.List;
import java.util.Map;

/**
 * P3.18: porta de leitura dos fatos da página de status (componentes,
 * leituras de sonda, incidentes e janelas das tabelas da core
 * {@code V10_4}), no padrão das portas do módulo (cf.
 * {@code BatchJobStore}).
 *
 * <p>A implementação JDBC sobre {@code support.status_*} e o coletor que
 * grava {@code last_probe_*} a partir das sondas da P0.27 são a fiação da
 * integração (camada p3b) e da LC.7 (sondas em produção) — declarados no
 * relatório da P3.18, não fingidos aqui: sem implementação entregue,
 * nenhum bean é registrado e o endpoint não sobe com dado falso.
 */
public interface StatusPageSource {

    List<StatusComponent> components();

    /** Leitura mais recente por {@code componentKey}; sem a chave = nunca mediu. */
    Map<String, ProbeReading> latestReadings();

    List<StatusIncident> incidents();

    List<MaintenanceWindow> maintenanceWindows();
}
