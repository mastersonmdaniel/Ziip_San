package br.com.ziipsan.intelligence.gis.connection;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.CreateInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ItemInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetService;
import br.com.ziipsan.intelligence.gis.connection.AutoServiceLineDtos.GeneratedLineView;
import br.com.ziipsan.intelligence.gis.connection.AutoServiceLineDtos.GenerationResultView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Geração de ramais automáticos (GIS-05, P2.23; §3.6.3, §3.10, §6.1):
 * cria UM pacote {@code AUTO_SERVICE_LINE} (canal {@code AUTO}), chama
 * a função {@code gis_generate_auto_service_lines} da V13_10 — que
 * grava os ramais em {@code PLANNED} com
 * {@code capture_method = AUTO_SERVICE_LINE}, até
 * {@code gis.service_line_max_distance_m} (padrão 100 m) do imóvel —
 * registra cada ramal como item INSERT do pacote propondo
 * {@code IN_SERVICE} e submete o pacote a revisão. Nada entra em
 * operação sem revisão por outra pessoa (decisão 3 do ADR-0034): quem
 * gera não aprova o próprio pacote, como em todo pacote da GIS-04.
 *
 * <p>Tudo numa transação: pacote, ramais, itens e submissão sobem ou
 * caem juntos. Rodada sem nenhuma ligação elegível não cria pacote.
 */
@Service
@Transactional(readOnly = true)
public class AutoServiceLineService {

    private final JdbcTemplate jdbc;
    private final GisChangeSetService changeSets;

    public AutoServiceLineService(JdbcTemplate jdbc, GisChangeSetService changeSets) {
        this.jdbc = jdbc;
        this.changeSets = changeSets;
    }

    /** Distância máxima vigente do tenant (parâmetro da V13_10, padrão 100 m). */
    public BigDecimal maxDistanceM() {
        UUID tenantId = tenantId();
        return jdbc.queryForObject("SELECT gis_service_line_max_distance_m(?)",
                BigDecimal.class, tenantId);
    }

    @Transactional
    public GenerationResultView generate() {
        UUID tenantId = tenantId();
        BigDecimal maxDistance = maxDistanceM();

        // O pacote precisa existir antes da função (ela grava os ramais
        // com o change_set_id dele), mas a função é quem diz se há
        // ligação elegível. Sem elegível, a transação é marcada para
        // desfazer o pacote vazio ao final (ver o fim do método).
        UUID changeSetId = changeSets.create(new CreateInput(
                "AUTO_SERVICE_LINE", "AUTO", null, null, null,
                "Ramais automáticos da GIS-05 (P2.23)", null)).id();

        List<GeneratedLineView> lines = jdbc.query("""
                SELECT service_connection_id, gis_connection_id, arc_id,
                       distance_m, generated, skip_reason
                FROM gis_generate_auto_service_lines(?, ?, ?)
                ORDER BY service_connection_id
                """, (rs, row) -> new GeneratedLineView(
                rs.getObject("service_connection_id", UUID.class),
                rs.getObject("gis_connection_id", UUID.class),
                rs.getObject("arc_id", UUID.class),
                rs.getBigDecimal("distance_m"),
                rs.getBoolean("generated"),
                rs.getString("skip_reason")), tenantId, changeSetId, maxDistance);

        for (GeneratedLineView line : lines) {
            if (!line.generated()) {
                continue;
            }
            changeSets.addItem(changeSetId, new ItemInput(
                    "INSERT", "gis_connection", line.gisConnectionId(),
                    Map.of("lifecycle_status", "IN_SERVICE",
                            "service_connection_id", line.serviceConnectionId().toString(),
                            "arc_id", line.arcId().toString()),
                    null, null));
        }

        GenerationResultView result = new GenerationResultView(changeSetId, maxDistance, lines);
        if (result.generatedCount() == 0) {
            // Nenhum ramal: cancela o pacote vazio em vez de submetê-lo.
            changeSets.cancel(changeSetId);
            return result;
        }
        changeSets.submit(changeSetId);
        return result;
    }

    private static UUID tenantId() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto da requisição");
        }
        return tenantId;
    }
}
