package br.com.ziipsan.commercial.billing.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** P2.20: mesma Idempotency-Key + mesmo pedido = REPLAY; pedido diferente = CONFLICT; chave nova = EXECUTE. */
class MaintenanceIdempotencyTest {

    @Test
    @DisplayName("hash estavel para o mesmo pedido e decisao por combinacao")
    void decisions() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operation", "RECTIFY_CONSUMPTION");
        request.put("total", "80.00");
        String hash = MaintenanceIdempotency.requestHash(request);
        assertThat(MaintenanceIdempotency.requestHash(new LinkedHashMap<>(request))).isEqualTo(hash);
        assertThat(MaintenanceIdempotency.decide(null, hash)).isEqualTo(MaintenanceIdempotency.Decision.EXECUTE);
        assertThat(MaintenanceIdempotency.decide(hash, hash)).isEqualTo(MaintenanceIdempotency.Decision.REPLAY);
        Map<String, Object> other = new LinkedHashMap<>(request);
        other.put("total", "90.00");
        assertThat(MaintenanceIdempotency.decide(hash, MaintenanceIdempotency.requestHash(other)))
                .isEqualTo(MaintenanceIdempotency.Decision.CONFLICT);
    }
}
