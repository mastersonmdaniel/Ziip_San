package br.com.ziipsan.intelligence.gis.clickos;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.ClickInput;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.CreatedView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PayloadView;
import br.com.ziipsan.intelligence.gis.clickos.ClickOsDtos.PropagationView;
import br.com.ziipsan.intelligence.gis.clickos.web.GisClickOsController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Controller da II-1 (Ciclo 2 do PDCA GIS): fino — o tenant e o
 * solicitante que chegam às operações são os do contexto da
 * requisição, nunca os do corpo.
 */
class GisClickOsControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ORDER = UUID.randomUUID();

    private final AtomicReference<UUID> seenTenant = new AtomicReference<>();
    private final AtomicReference<UUID> seenUser = new AtomicReference<>();
    private final AtomicReference<ClickInput> seenInput = new AtomicReference<>();

    private final ClickServiceOrderOperations operations = new ClickServiceOrderOperations() {
        @Override
        public PropagationView propagate(UUID tenantId, ClickInput input) {
            seenTenant.set(tenantId);
            seenInput.set(input);
            return new PropagationView("DOWNSTREAM", "WITH_AFFECTED_UNITS",
                    List.of(), List.of(), List.of(), List.of(), List.of(), 0,
                    List.of(), List.of(), List.of(), null, false, false);
        }

        @Override
        public PayloadView buildPayload(UUID tenantId, UUID requestedBy, ClickInput input) {
            seenTenant.set(tenantId);
            seenUser.set(requestedBy);
            seenInput.set(input);
            return new PayloadView("T", "DOWNSTREAM", "WITH_AFFECTED_UNITS",
                    List.of(), List.of(), List.of(), List.of(), 0, false, true, List.of());
        }

        @Override
        public CreatedView createFromClick(UUID tenantId, UUID requestedBy, ClickInput input) {
            seenTenant.set(tenantId);
            seenUser.set(requestedBy);
            seenInput.set(input);
            return new CreatedView(ORDER, buildPayload(tenantId, requestedBy, input));
        }
    };

    private GisClickOsController controller;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
        controller = new GisClickOsController(operations);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createPassesContextTenantAndUserAndReturnsTheCreatedView() {
        ClickInput input = new ClickInput(null, UUID.randomUUID(), "DOWNSTREAM", "T", null, null);
        CreatedView created = controller.createOrder(input);
        assertEquals(ORDER, created.orderId());
        assertEquals(TENANT, seenTenant.get());
        assertEquals(USER, seenUser.get());
        assertSame(input, seenInput.get());
    }

    @Test
    void propagatePassesContextTenant() {
        ClickInput input = new ClickInput(UUID.randomUUID(), null, "UPSTREAM", null, null, null);
        PropagationView view = controller.propagate(input);
        assertEquals("WITH_AFFECTED_UNITS", view.outcome());
        assertEquals(TENANT, seenTenant.get());
        assertSame(input, seenInput.get());
    }
}
