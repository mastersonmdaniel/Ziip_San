package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.core.fieldos.FieldOsPorts;
import br.com.ziipsan.core.fieldos.Tracking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementacao EM MEMORIA da porta {@link FieldOsPorts.TrackStore},
 * entregue como implementacao de ENSAIO E TESTES: permite a integracao e os
 * testes exercitarem a cadeia da trilha (ingestao -&gt; persistencia -&gt;
 * painel) sem banco. NAO e persistencia de producao: nada sobrevive ao
 * processo, nao ha tenant real nem expurgo por retencao.
 *
 * <p>Propositadamente SEM anotacao de componente Spring: ela nunca pode
 * virar a persistencia de producao por acidente de classpath. Quem a usa
 * instancia-a explicitamente. A implementacao JDBC sobre a tabela
 * {@code field_track_points} (SQL da ED-20 em {@code db/pendente}, ainda sem
 * numeracao Flyway global) e a pendencia P-MOB-GPS-05.
 *
 * <p>Contrato honrado: {@code append} so recebe ponto que ja passou por
 * {@code Tracking.ingest} (a composicao ingestao-&gt;persistencia e do
 * chamador, {@code FieldTrackIngestionService} — P-ED20-02); {@code points}
 * devolve os pontos do trabalhador na janela [from, to] (extremos
 * incluidos), ordenados pelo instante.
 */
public class InMemoryTrackStore implements FieldOsPorts.TrackStore {

    private final Map<UUID, Map<UUID, List<Tracking.TrackPoint>>> pointsByTenantAndWorker = new HashMap<>();

    @Override
    public synchronized void append(UUID tenantId, Tracking.TrackPoint point) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(point, "ponto ingerido e obrigatorio");
        pointsByTenantAndWorker
                .computeIfAbsent(tenantId, t -> new HashMap<>())
                .computeIfAbsent(point.workerId(), w -> new ArrayList<>())
                .add(point);
    }

    @Override
    public synchronized List<Tracking.TrackPoint> points(UUID tenantId, UUID workerId, Instant from, Instant to) {
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(workerId, "trabalhador e obrigatorio");
        Objects.requireNonNull(from, "inicio da janela e obrigatorio");
        Objects.requireNonNull(to, "fim da janela e obrigatorio");
        List<Tracking.TrackPoint> stored = pointsByTenantAndWorker
                .getOrDefault(tenantId, Map.of())
                .getOrDefault(workerId, List.of());
        return stored.stream()
                .filter(p -> !p.at().isBefore(from) && !p.at().isAfter(to))
                .sorted(Comparator.comparing(Tracking.TrackPoint::at))
                .toList();
    }
}
