package br.com.ziipsan.intelligence.gis.validation;

import br.com.ziipsan.intelligence.gis.validation.GisValidationModel.DetectedIssue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Decisão pura de reconciliação da validação contínua (GIS-03, P2.21, §5.5;
 * correção I1+I2 da revisão), extraída do {@link GisValidationService}
 * para ser testável sem banco: dado o conjunto de problemas persistidos
 * relevantes (OPEN e ACCEPTED das feições do retrato) e o conjunto de
 * problemas detectados na rodada, decide o que abrir e o que resolver.
 *
 * <p>Transições:
 * <ul>
 *   <li>detectado sem correspondente OPEN/ACCEPTED → abre OPEN novo;</li>
 *   <li>OPEN cuja condição some na revalidação → RESOLVED;</li>
 *   <li>ACCEPTED cuja condição some na revalidação → RESOLVED (a
 *   justificativa e os campos {@code accepted_*} são preservados pelo
 *   serviço, que só muda {@code status}/{@code resolved_at});</li>
 *   <li>ACCEPTED cuja condição persiste → continua ACCEPTED: não duplica
 *   nem reabre;</li>
 *   <li>condição que volta após RESOLVED → abre OPEN novo: RESOLVED não
 *   bloqueia abertura (o chamador nem o carrega; se o carregar, ele é
 *   ignorado aqui — não entra nem em {@code toOpen} como bloqueio nem em
 *   {@code toResolve});</li>
 *   <li>dois {@link DetectedIssue} com a mesma chave na mesma rodada
 *   (severidades diferentes) → abre um só, valendo a PRIMEIRA severidade
 *   detectada (a ordem da coleção de entrada é a ordem de detecção).</li>
 * </ul>
 */
final class GisValidationReconciliation {

    private GisValidationReconciliation() {
    }

    /** Chave de deduplicação de problema persistido (regra + feição + segunda feição). */
    record IssueKey(String ruleCode, String featureTable, UUID featureId, UUID feature2Id) {
        static IssueKey of(DetectedIssue issue) {
            return new IssueKey(issue.ruleCode(), issue.featureTable(), issue.featureId(), issue.feature2Id());
        }
    }

    /** Problema persistido relevante para a rodada. */
    record ExistingIssue(UUID id, IssueKey key, String status) {
    }

    /** Plano da rodada: problemas a abrir (OPEN) e persistidos a resolver (RESOLVED). */
    record Plan(List<DetectedIssue> toOpen, List<ExistingIssue> toResolve) {
    }

    static Plan reconcile(List<ExistingIssue> existing, Collection<DetectedIssue> detected) {
        Map<IssueKey, ExistingIssue> openOrAcceptedByKey = new HashMap<>();
        for (ExistingIssue e : existing) {
            if ("OPEN".equals(e.status()) || "ACCEPTED".equals(e.status())) {
                openOrAcceptedByKey.putIfAbsent(e.key(), e);
            }
        }
        List<DetectedIssue> toOpen = new ArrayList<>();
        Set<IssueKey> detectedKeys = new HashSet<>();
        Set<IssueKey> insertedKeys = new HashSet<>();
        for (DetectedIssue issue : detected) {
            IssueKey key = IssueKey.of(issue);
            detectedKeys.add(key);
            if (openOrAcceptedByKey.containsKey(key)) {
                continue; // persiste (OPEN continua aberto; ACCEPTED continua aceito)
            }
            if (!insertedKeys.add(key)) {
                continue; // mesma chave de novo na rodada: vale a primeira severidade
            }
            toOpen.add(issue);
        }
        List<ExistingIssue> toResolve = new ArrayList<>();
        for (ExistingIssue e : openOrAcceptedByKey.values()) {
            if (!detectedKeys.contains(e.key())) {
                toResolve.add(e); // OPEN ou ACCEPTED cuja condicao sumiu
            }
        }
        return new Plan(List.copyOf(toOpen), List.copyOf(toResolve));
    }
}
