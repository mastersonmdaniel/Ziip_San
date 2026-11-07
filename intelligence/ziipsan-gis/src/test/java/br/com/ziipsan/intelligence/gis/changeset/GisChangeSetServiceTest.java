package br.com.ziipsan.intelligence.gis.changeset;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ApproveInput;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ChangeSetView;
import br.com.ziipsan.intelligence.gis.changeset.GisChangeSetDtos.ReviewInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-04 (P2.22), correção A3 da revisão: o 403 da api na auto-revisão
 * provado no nível do SERVIÇO (a Gis04ChangeSetIT prova o erro no
 * banco; a GisChangeSetLifecycleTest, só a função pura). O JdbcTemplate
 * é um dublê em memória que devolve o pacote em IN_REVIEW com o autor
 * fixo e registra o que o serviço tentou gravar: com a identidade do
 * autor, aprovar/devolver/rejeitar tem de falhar 403 ANTES de qualquer
 * escrita; com outra identidade, o mesmo caminho chega ao banco.
 */
class GisChangeSetServiceTest {

    private static final UUID TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID AUTHOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID REVIEWER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SET_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private FakeJdbc jdbc;
    private GisChangeSetService service;

    /**
     * Dublê do JdbcTemplate: {@code get} enxerga um pacote IN_REVIEW do
     * AUTHOR; a função de aplicação conta as chamadas; {@code update}
     * registra SQL + argumentos e finge 1 linha afetada.
     */
    private static final class FakeJdbc extends JdbcTemplate {
        int applyCalls;
        final List<String> updates = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            if (sql.contains("FROM gis_change_set cs")) {
                ChangeSetView view = new ChangeSetView(SET_ID, "CORRECTION", "WEB",
                        "IN_REVIEW", AUTHOR, null, null, null, null, Map.of(),
                        null, null, null, null, Instant.EPOCH, Instant.EPOCH, 1);
                return List.of((T) view);
            }
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql.contains("gis_apply_change_set")) {
                applyCalls++;
                return requiredType.cast("APPLIED");
            }
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(sql + " " + Arrays.toString(args));
            return 1;
        }
    }

    @BeforeEach
    void setUp() {
        jdbc = new FakeJdbc();
        service = new GisChangeSetService(jdbc);
        TenantContextHolder.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("api: autor aprovando o próprio pacote recebe 403 e a função de aplicação nem é chamada")
    void authorApproveIsForbidden() {
        TenantContextHolder.setUserId(AUTHOR);
        assertThatThrownBy(() -> service.approve(SET_ID, new ApproveInput(null)))
                .isInstanceOf(ApiException.Forbidden.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(403));
        assertThat(jdbc.applyCalls).isZero();
    }

    @Test
    @DisplayName("api: autor devolvendo o próprio pacote recebe 403 e nenhuma decisão é gravada")
    void authorReturnIsForbidden() {
        TenantContextHolder.setUserId(AUTHOR);
        assertThatThrownBy(() -> service.returnToAuthor(SET_ID, new ReviewInput("faltou o croqui")))
                .isInstanceOf(ApiException.Forbidden.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(403));
        assertThat(jdbc.updates).isEmpty();
    }

    @Test
    @DisplayName("api: autor rejeitando o próprio pacote recebe 403 e nenhuma decisão é gravada")
    void authorRejectIsForbidden() {
        TenantContextHolder.setUserId(AUTHOR);
        assertThatThrownBy(() -> service.reject(SET_ID, new ReviewInput("fora do escopo")))
                .isInstanceOf(ApiException.Forbidden.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(403));
        assertThat(jdbc.updates).isEmpty();
    }

    @Test
    @DisplayName("api: outra pessoa aprova — o serviço chega à função de aplicação do banco")
    void otherReviewerApproveReachesDatabase() {
        TenantContextHolder.setUserId(REVIEWER);
        ChangeSetView view = service.approve(SET_ID, new ApproveInput(null));
        assertThat(view.id()).isEqualTo(SET_ID);
        assertThat(jdbc.applyCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("api: outra pessoa devolve — a decisão RETURNED é gravada")
    void otherReviewerReturnRecordsDecision() {
        TenantContextHolder.setUserId(REVIEWER);
        service.returnToAuthor(SET_ID, new ReviewInput("faltou o croqui"));
        assertThat(jdbc.updates).anyMatch(u -> u.contains("INSERT INTO gis_change_set_review")
                && u.contains("RETURNED"));
    }
}
