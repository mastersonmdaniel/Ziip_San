package br.com.ziipsan.app.job;

import br.com.ziipsan.commercial.fiscal.relief.CertificateAlertPlanner;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador REAL da porta de registo/despacho do scheduler de alertas de
 * certificado (correccao R-03 da REVISAO-PA-02: a porta tinha zero
 * implementacoes bean).
 *
 * <p>Comportamento: mantem o conjunto de chaves ja alertadas
 * (certificado, limiar, dia) — a idempotencia da secao 3.8 — e a lista de
 * alertas despachados. E o adaptador de processo; a tabela
 * {@code fiscal_certificate_alert_log} (migration M3 do pacote) e a forma
 * persistente da mesma chave, ligada na integracao JDBC sem mudar a porta
 * nem o scheduler.
 */
public final class InMemoryCertificateAlertLogPort
        implements CertificateAlertScheduler.CertificateAlertLogPort {

    private final Set<CertificateAlertPlanner.AlertKey> alerted = ConcurrentHashMap.newKeySet();
    private final List<CertificateAlertPlanner.PlannedAlert> dispatched =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public Set<CertificateAlertPlanner.AlertKey> alreadyAlerted() {
        return Set.copyOf(alerted);
    }

    @Override
    public void recordAndDispatch(CertificateAlertPlanner.PlannedAlert alert) {
        alerted.add(new CertificateAlertPlanner.AlertKey(
                alert.certificateId(), alert.threshold(), alert.alertDate()));
        dispatched.add(alert);
    }

    /** Alertas despachados por este adaptador (auditoria/teste). */
    public List<CertificateAlertPlanner.PlannedAlert> dispatched() {
        return List.copyOf(dispatched);
    }
}
