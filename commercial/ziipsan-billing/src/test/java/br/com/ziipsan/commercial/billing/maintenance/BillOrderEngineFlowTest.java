package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.20/G3: o ciclo criar -&gt; (aprovar) -&gt; executar dos quatro tipos
 * {@code BILL_*} contra o MOTOR REAL da P1.31 ({@link ServiceOrderService})
 * atraves do adaptador real ({@link CoreServiceOrderGateway}) -- nao contra
 * um gateway falso. O motor e codigo de verdade: maquina de estados,
 * desvio de aprovacao pela fabrica, aprovador != autor, validacao da carga
 * e exigencia de {@code EffectHandler} sao os dele.
 *
 * <p>Sem banco nesta VM: a camada JDBC e um dublê em memoria (proxies de
 * {@link DataSource}/{@link Connection}/{@link PreparedStatement}/
 * {@link ResultSet}) que guarda as linhas que o proprio motor escreve e as
 * devolve nas leituras dele. O dublê nao decide transicao nenhuma -- quem
 * recusa ou aceita e o motor. Os fluxos com PostgreSQL de verdade vivem
 * nos ITs do projeto (padrao ProvisionedTenant/TenantRlsIsolationTest) e na
 * CI total do dono.
 */
class BillOrderEngineFlowTest {

    private static final String SCHEMA = "tenant_test";
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID APPROVER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private FakeDb db;
    private CoreServiceOrderGateway gateway;

    @BeforeEach
    void setUp() {
        db = new FakeDb();
        db.consumerUnits.add(UNIT);
        gateway = new CoreServiceOrderGateway(db.dataSource());
        TenantContextHolder.setTenant(SCHEMA);
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(ACTOR);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private static String payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationRecordId", UUID.randomUUID().toString());
        payload.put("operation", "RECTIFY_CONSUMPTION");
        payload.put("billId", UUID.randomUUID().toString());
        return Json.write(payload);
    }

    private ServiceOrderService rawEngine() {
        return new ServiceOrderService(db.dataSource(), SCHEMA, TENANT);
    }

    @Test
    @DisplayName("sem aprovacao: os 4 tipos BILL_* vao de DRAFT a EXECUTED pelo motor real, com o efeito aplicado 1 vez")
    void cycleWithoutApprovalAllTypes() {
        for (BillOrderType type : BillOrderType.values()) {
            assertThat(gateway.findType(type)).as(type.code()).isNull();
            ServiceOrderGateway.TypeInfo info = gateway.ensureType(type, false);
            assertThat(info).as(type.code()).isEqualTo(new ServiceOrderGateway.TypeInfo(true, false));
            UUID orderId = gateway.createOrder(type, ServiceOrderEnums.Origin.INTERNAL, null, ACTOR, UNIT,
                    payload());
            AtomicInteger applications = new AtomicInteger();
            gateway.execute(orderId, ACTOR, applications::incrementAndGet);
            assertThat(applications.get()).as(type.code()).isEqualTo(1);
            assertThat(rawEngine().getOrder(orderId).status()).as(type.code())
                    .isEqualTo(ServiceOrderStatus.EXECUTED);
            assertThat(db.effects).as(type.code()).hasSize(1);
            assertThat(db.effects.get(0).get("effect_code")).isEqualTo(type.effectCode());
            db.effects.clear();
        }
    }

    @Test
    @DisplayName("com aprovacao: autor nao aprova a propria OS (motor), outro aprova, executa e o efeito aplica 1 vez")
    void cycleWithApprovalAllTypes() {
        for (BillOrderType type : BillOrderType.values()) {
            gateway.ensureType(type, true);
            UUID orderId = gateway.createOrder(type, ServiceOrderEnums.Origin.INTERNAL, null, ACTOR, UNIT,
                    payload());
            assertThat(rawEngine().getOrder(orderId).status()).as(type.code())
                    .isEqualTo(ServiceOrderStatus.PENDING_APPROVAL);
            // O motor recusa aprovador igual ao autor -- a regra e dele, nao do adaptador.
            assertThatThrownBy(() -> gateway.approve(orderId, ACTOR)).as(type.code())
                    .isInstanceOf(ApiException.class);
            gateway.approve(orderId, APPROVER);
            AtomicInteger applications = new AtomicInteger();
            gateway.execute(orderId, APPROVER, applications::incrementAndGet);
            assertThat(applications.get()).as(type.code()).isEqualTo(1);
            ServiceOrderService.OrderView view = rawEngine().getOrder(orderId);
            assertThat(view.status()).as(type.code()).isEqualTo(ServiceOrderStatus.EXECUTED);
            assertThat(view.approvedBy()).as(type.code()).isEqualTo(APPROVER);
            db.effects.clear();
        }
    }

    @Test
    @DisplayName("sem handler registrado o motor RECUSA o execute (no-effect-handler): o registro do adaptador e o que fecha o ciclo")
    void executeWithoutHandlerIsRefusedByRealEngine() {
        ServiceOrderService engine = rawEngine();
        BillOrderType type = BillOrderType.BILL_RECTIFICATION;
        engine.declareType(type.declaration(false), ACTOR);
        ServiceOrderService.OrderView order = engine.createOrder(new ServiceOrderService.OrderInput(
                type.code(), ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, ACTOR, null, null, null, null));
        engine.addTarget(order.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, UNIT, null, null, null, payload()), ACTOR);
        engine.submit(order.id(), ACTOR);
        assertThatThrownBy(() -> engine.execute(order.id(), ACTOR))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("Nenhum manipulador registrado");
        assertThat(engine.getOrder(order.id()).status()).isEqualTo(ServiceOrderStatus.OPEN);
        assertThat(db.effects).isEmpty();
    }

    @Test
    @DisplayName("os dois sentidos da incompatibilidade antiga, provados no motor: execute de PENDING_APPROVAL e approve fora dela sao recusados")
    void oldIncompatibilitiesAreRealEngineRefusals() {
        ServiceOrderService engine = rawEngine();
        // Fabrica exigente, sem aprovacao ainda: execute e recusado (era o NONE + fabrica exigente).
        BillOrderType type = BillOrderType.BILL_CANCELLATION;
        engine.declareType(type.declaration(true), ACTOR);
        ServiceOrderService.OrderView pending = engine.createOrder(new ServiceOrderService.OrderInput(
                type.code(), ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, ACTOR, null, null, null, null));
        engine.addTarget(pending.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, UNIT, null, null, null, payload()), ACTOR);
        engine.submit(pending.id(), ACTOR);
        assertThat(engine.getOrder(pending.id()).status()).isEqualTo(ServiceOrderStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> engine.execute(pending.id(), ACTOR))
                .isInstanceOf(ApiException.Conflict.class);
        // Fabrica livre, OS ja OPEN: approve e recusado (era o REQUIRED + fabrica livre).
        BillOrderType free = BillOrderType.BILL_REVIEW;
        engine.declareType(free.declaration(false), ACTOR);
        ServiceOrderService.OrderView open = engine.createOrder(new ServiceOrderService.OrderInput(
                free.code(), ServiceOrderEnums.Origin.INTERNAL, ServiceOrderEnums.ExecutionMode.INTERNAL,
                ServiceOrderEnums.Priority.NORMAL, ACTOR, null, null, null, null));
        engine.addTarget(open.id(), new ServiceOrderService.TargetInput(
                TargetType.CONSUMER_UNIT, UNIT, null, null, null, payload()), ACTOR);
        engine.submit(open.id(), ACTOR);
        assertThat(engine.getOrder(open.id()).status()).isEqualTo(ServiceOrderStatus.OPEN);
        assertThatThrownBy(() -> engine.approve(open.id(), APPROVER))
                .isInstanceOf(ApiException.Conflict.class)
                .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("findType traduz o NotFound do motor para null; tipo inativo/inexistente em createSubmittedOrder sobe sem traducao")
    void findTypeTranslationAndSubmittedOrder() {
        assertThat(gateway.findType(BillOrderType.BILL_REVIEW)).isNull();
        assertThatThrownBy(() -> gateway.createSubmittedOrder("TIPO_QUE_NAO_EXISTE", UNIT, ACTOR))
                .isInstanceOf(ApiException.NotFound.class);
    }

    // ==================================================================
    // Dublê JDBC em memoria: guarda as linhas que o motor escreve.
    // ==================================================================

    static final class FakeDb {
        final Map<String, Map<String, Object>> typesByCode = new LinkedHashMap<>();
        final Map<UUID, Map<String, Object>> typesById = new LinkedHashMap<>();
        final Map<UUID, Map<String, Object>> ordersById = new LinkedHashMap<>();
        final List<Map<String, Object>> targets = new ArrayList<>();
        final List<Map<String, Object>> effects = new ArrayList<>();
        final java.util.Set<UUID> consumerUnits = new java.util.HashSet<>();

        DataSource dataSource() {
            return proxy(DataSource.class, (p, m, a) -> {
                if (m.getName().equals("getConnection")) {
                    return connection();
                }
                return objectMethod(p, m, a);
            });
        }

        private Connection connection() {
            return proxy(Connection.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "prepareStatement":
                        return preparedStatement((String) a[0]);
                    case "createArrayOf":
                        return fakeArray((Object[]) a[1]);
                    default:
                        return objectMethod(p, m, a);
                }
            });
        }

        private PreparedStatement preparedStatement(String sql) {
            Map<Integer, Object> params = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "setString", "setObject", "setBoolean", "setInt", "setLong", "setTimestamp":
                        params.put((Integer) a[0], a[1]);
                        return null;
                    case "setNull":
                        params.put((Integer) a[0], null);
                        return null;
                    case "setArray":
                        params.put((Integer) a[0], ((FakeArray) Proxy.getInvocationHandler(a[1])).data);
                        return null;
                    case "executeQuery":
                        return resultSet(select(sql, params));
                    case "executeUpdate":
                        update(sql, params);
                        return 1;
                    default:
                        return objectMethod(p, m, a);
                }
            });
        }

        private List<Map<String, Object>> select(String sql, Map<Integer, Object> params) {
            String s = sql.replaceAll("\\s+", " ");
            if (s.contains("set_config") || s.contains("parameter_versions")) {
                return List.of();
            }
            if (s.contains("consumer_units")) {
                return consumerUnits.contains(params.get(2)) ? List.of(Map.of("1", 1)) : List.of();
            }
            if (s.contains("SELECT o.*, t.code AS type_code")) {
                Map<String, Object> order = ordersById.get(params.get(2));
                return order == null ? List.of() : List.of(order);
            }
            if (s.contains("SELECT t.* FROM") && s.contains("service_types")) {
                Map<String, Object> order = ordersById.get(params.get(2));
                if (order == null) {
                    return List.of();
                }
                Map<String, Object> type = typesById.get(order.get("service_type_id"));
                return type == null ? List.of() : List.of(type);
            }
            if (s.contains("SELECT * FROM") && s.contains("service_types")) {
                Map<String, Object> type = typesByCode.get(params.get(2));
                return type == null ? List.of() : List.of(type);
            }
            if (s.contains("service_order_targets")) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<String, Object> target : targets) {
                    if (params.get(2).equals(target.get("order_id"))) {
                        rows.add(target);
                    }
                }
                return rows;
            }
            return List.of();
        }

        private void update(String sql, Map<Integer, Object> params) {
            String s = sql.replaceAll("\\s+", " ");
            if (s.contains("INSERT INTO") && s.contains("service_types")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", params.get(1));
                row.put("code", params.get(3));
                row.put("name", params.get(4));
                row.put("operation", params.get(5));
                row.put("effect_code", params.get(6));
                row.put("effect_schema", params.get(7));
                row.put("indicator", params.get(8));
                row.put("requires_approval", params.get(9));
                row.put("operational", params.get(10));
                row.put("origin", params.get(11));
                row.put("target_classes", params.get(12));
                row.put("price_item_ref", params.get(13));
                row.put("deadline_hours", params.get(14));
                row.put("min_crew", params.get(15));
                row.put("confined_space", params.get(16));
                row.put("height_work", params.get(17));
                row.put("trench_work", params.get(18));
                row.put("electrical_work", params.get(19));
                row.put("requires_risk_analysis", params.get(20));
                row.put("requires_entry_permit", params.get(21));
                row.put("requires_work_permit", params.get(22));
                row.put("required_norm_codes", params.get(23));
                row.put("active", params.get(24));
                typesByCode.put((String) params.get(3), row);
                typesById.put((UUID) params.get(1), row);
                return;
            }
            if (s.contains("INSERT INTO") && s.contains("service_orders")) {
                Map<String, Object> row = new LinkedHashMap<>();
                UUID id = (UUID) params.get(1);
                Map<String, Object> type = typesById.get(params.get(3));
                row.put("id", id);
                row.put("service_type_id", params.get(3));
                row.put("type_code", type == null ? null : type.get("code"));
                row.put("batch_id", params.get(4));
                row.put("parent_order_id", params.get(5));
                row.put("reverses_order_id", params.get(6));
                row.put("protocol_id", params.get(7));
                row.put("origin", params.get(8));
                row.put("execution_mode", params.get(9));
                row.put("priority", params.get(10));
                row.put("status", "DRAFT");
                row.put("created_by", params.get(11));
                row.put("approved_by", null);
                row.put("due_at", params.get(13));
                row.put("executed_at", null);
                row.put("closed_at", null);
                ordersById.put(id, row);
                return;
            }
            if (s.contains("INSERT INTO") && s.contains("service_order_targets")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", params.get(1));
                row.put("order_id", params.get(3));
                row.put("target_type", params.get(4));
                row.put("target_id", params.get(5));
                row.put("effect_code", params.get(7));
                row.put("effect_payload", params.get(8));
                row.put("effect_applied", false);
                targets.add(row);
                return;
            }
            if (s.contains("INSERT INTO") && s.contains("service_order_effects")) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", params.get(1));
                row.put("order_id", params.get(3));
                row.put("target_id", params.get(4));
                row.put("effect_code", params.get(5));
                row.put("payload", params.get(6));
                effects.add(row);
                return;
            }
            if (s.contains("UPDATE") && s.contains("service_orders") && s.contains("SET status")) {
                ordersById.get(params.get(4)).put("status", params.get(1));
                return;
            }
            if (s.contains("UPDATE") && s.contains("service_orders") && s.contains("SET approved_by")) {
                ordersById.get(params.get(4)).put("approved_by", params.get(1));
                return;
            }
            if (s.contains("UPDATE") && s.contains("service_orders") && s.contains("SET executed_at")) {
                ordersById.get(params.get(3)).put("executed_at", new Timestamp(System.currentTimeMillis()));
                return;
            }
            if (s.contains("UPDATE") && s.contains("service_order_targets")) {
                for (Map<String, Object> target : targets) {
                    if (params.get(3).equals(target.get("id"))) {
                        target.put("effect_applied", true);
                    }
                }
            }
            // service_order_history e demais escritas: aceitas sem guardar.
        }

        private ResultSet resultSet(List<Map<String, Object>> rows) {
            int[] cursor = {-1};
            return proxy(ResultSet.class, (p, m, a) -> {
                switch (m.getName()) {
                    case "next":
                        return ++cursor[0] < rows.size();
                    case "getObject", "getString", "getTimestamp":
                        return rows.get(cursor[0]).get(a[0]);
                    case "getBoolean":
                        return Boolean.TRUE.equals(rows.get(cursor[0]).get(a[0]));
                    case "getArray":
                        return fakeArray((Object[]) rows.get(cursor[0]).get(a[0]));
                    default:
                        return objectMethod(p, m, a);
                }
            });
        }

        private Array fakeArray(Object[] data) {
            return proxy(Array.class, new FakeArray(data));
        }
    }

    static final class FakeArray implements InvocationHandler {
        final Object[] data;

        FakeArray(Object[] data) {
            this.data = data;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if (method.getName().equals("getArray")) {
                return data;
            }
            return objectMethod(proxy, method, args);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> iface, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(BillOrderEngineFlowTest.class.getClassLoader(),
                new Class<?>[]{iface}, handler);
    }

    private static Object objectMethod(Object proxy, java.lang.reflect.Method method, Object[] args) {
        switch (method.getName()) {
            case "toString":
                return "fake-jdbc";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                Class<?> rt = method.getReturnType();
                if (rt == boolean.class) {
                    return false;
                }
                if (rt == int.class) {
                    return 0;
                }
                if (rt == long.class) {
                    return 0L;
                }
                return null;
        }
    }
}
