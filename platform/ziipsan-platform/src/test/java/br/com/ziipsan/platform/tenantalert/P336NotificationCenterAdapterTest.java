package br.com.ziipsan.platform.tenantalert;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.notification.ConsentGate;
import br.com.ziipsan.platform.notification.DigestMode;
import br.com.ziipsan.platform.notification.NotificationCenterService;
import br.com.ziipsan.platform.notification.NotificationIngestRule;
import br.com.ziipsan.platform.notification.NotificationParameters;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Correção D1 (pós-reprovação): a entrega na central da P3.36 provada contra
 * as classes REAIS da dona — {@link P336NotificationCenterAdapter} →
 * {@code OutboxMessage} real → {@link NotificationCenterService#ingest}
 * real → regra real {@link NotificationIngestRule}. O banco é o JDBC falso
 * mínimo da PRÓPRIA prova de fiação da P3.36
 * ({@code NotificationIngestWiringTest} da dona, mesmo padrão): guarda as
 * linhas por {@code source_message_id} e honra o
 * {@code ON CONFLICT DO NOTHING} da V1_15. Nenhum dublê substitui a central:
 * o único dublê é o banco, como na prova da dona.
 */
class P336NotificationCenterAdapterTest {

    private static final ConsentGate GATE_NAO_USADO_NA_INGESTAO = new ConsentGate() {
        @Override public boolean isVerifiedContact(UUID tenantId, UUID contactId) {
            throw new AssertionError("a ingestão não consulta o gate de consentimento");
        }
        @Override public boolean hasActiveConsent(UUID tenantId, UUID customerId, UUID contactId,
                                                  UUID consentId, String purposeCode) {
            throw new AssertionError("a ingestão não consulta o gate de consentimento");
        }
    };

    private static NotificationCenterService realCenter(FakeDb db) {
        NotificationParameters parameters = new NotificationParameters() {
            @Override public DigestMode digestDefault(UUID tenantId) { return DigestMode.IMMEDIATE; }
            @Override public Set<br.com.ziipsan.platform.notification.NotificationCategory> tenantMandatoryCategories(UUID tenantId) {
                return Set.of();
            }
        };
        return new NotificationCenterService(db.dataSource(), "test", TenantAlertFixtures.TENANT,
                TenantAlertFixtures.catalog(), GATE_NAO_USADO_NA_INGESTAO, parameters,
                ZoneOffset.UTC, Clock.fixed(TenantAlertFixtures.NOW, ZoneOffset.UTC));
    }

    private static TenantAlertPorts.NotificationRequest catalogRequest(UUID eventId) {
        return new TenantAlertPorts.NotificationRequest(TenantAlertFixtures.TENANT,
                TenantAlertFixtures.RESPONSIBLE, TenantAlertFixtures.KNOWN_EVENT,
                TenantAlertFixtures.KNOWN_VERSION, TenantAlert.Kind.CATALOG_EVENT,
                TenantAlertService.titleCodeOf(TenantAlertFixtures.KNOWN_EVENT),
                TenantAlertService.bodyCodeOf(TenantAlertFixtures.KNOWN_EVENT), eventId.toString());
    }

    @Test
    void conversionPassesTheRealIngestRuleOfP336() {
        var message = P336NotificationCenterAdapter.toOutboxMessage(
                catalogRequest(UUID.fromString("44444444-4444-4444-4444-444444444444")),
                TenantAlertFixtures.NOW);
        // A regra REAL da dona lê a mensagem convertida e monta o rascunho.
        var draft = NotificationIngestRule.draft(message, TenantAlertFixtures.catalog());
        assertThat(draft.sourceMessageId()).isEqualTo(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        assertThat(draft.tenantId()).isEqualTo(TenantAlertFixtures.TENANT);
        assertThat(draft.eventName()).isEqualTo(TenantAlertFixtures.KNOWN_EVENT);
        assertThat(draft.eventVersion()).isEqualTo(TenantAlertFixtures.KNOWN_VERSION);
        assertThat(draft.recipientUserId()).isEqualTo(TenantAlertFixtures.RESPONSIBLE);
        assertThat(draft.category()).isEqualTo(br.com.ziipsan.platform.notification.NotificationCategory.BILLING);
        assertThat(draft.titleCode()).isEqualTo("tenant-alert." + TenantAlertFixtures.KNOWN_EVENT);
        assertThat(draft.bodyCode()).isEqualTo("tenant-alert." + TenantAlertFixtures.KNOWN_EVENT + ".body");
    }

    @Test
    void catalogAlertIsIngestedByTheRealNotificationCenterService() {
        FakeDb db = new FakeDb();
        var adapter = new P336NotificationCenterAdapter(realCenter(db),
                Clock.fixed(TenantAlertFixtures.NOW, ZoneOffset.UTC));
        UUID eventId = UUID.randomUUID();
        adapter.deliver(catalogRequest(eventId));
        assertThat(db.insercoes.get()).isEqualTo(1);
        // A chave de dedup gravada pela central real é o id da mensagem = id do evento.
        assertThat(db.avisoPorMensagem).containsKey(eventId);
        assertThat(db.ultimoDeliverAfter).isEqualTo(Timestamp.from(TenantAlertFixtures.NOW));
    }

    @Test
    void redeliveryThroughAdapterIsDeduplicatedByTheRealCenter() {
        FakeDb db = new FakeDb();
        var adapter = new P336NotificationCenterAdapter(realCenter(db),
                Clock.fixed(TenantAlertFixtures.NOW, ZoneOffset.UTC));
        UUID eventId = UUID.randomUUID();
        adapter.deliver(catalogRequest(eventId));
        adapter.deliver(catalogRequest(eventId));
        assertThat(db.insercoes.get()).isEqualTo(1);
        assertThat(db.avisoPorMensagem).hasSize(1);
    }

    @Test
    void batchFailureRequestIsRefusedByAdapterWithoutInventingCatalogEvent() {
        FakeDb db = new FakeDb();
        var adapter = new P336NotificationCenterAdapter(realCenter(db),
                Clock.fixed(TenantAlertFixtures.NOW, ZoneOffset.UTC));
        var batchRequest = new TenantAlertPorts.NotificationRequest(TenantAlertFixtures.TENANT,
                TenantAlertFixtures.RESPONSIBLE, null, null, TenantAlert.Kind.BATCH_FAILURE,
                TenantAlertService.BATCH_FAILURE_TITLE_CODE, TenantAlertService.BATCH_FAILURE_BODY_CODE,
                UUID.randomUUID().toString());
        assertThatThrownBy(() -> adapter.deliver(batchRequest))
                .isInstanceOf(NotificationRoutingException.class)
                .hasMessageContaining("não é evento do catálogo");
        // A central real nem foi tocada: nada foi ingerido sob um nome fingido.
        assertThat(db.insercoes.get()).isZero();
        assertThat(db.avisoPorMensagem).isEmpty();
    }

    @Test
    void batchFailureWithEventDeclaredByIntegrationIsIngestedByTheRealCenter() {
        // O nome usado aqui é o evento do catálogo DUBLÊ dos testes, apenas
        // como o par (nome, versão) que a FIACÃO declararia no vertical do
        // pacote batch — esta entrega não fixa nome oficial nenhum.
        FakeDb db = new FakeDb();
        var adapter = new P336NotificationCenterAdapter(realCenter(db),
                Clock.fixed(TenantAlertFixtures.NOW, ZoneOffset.UTC));
        UUID runId = UUID.randomUUID();
        var notice = new TenantAlertPorts.BatchFailureNotice(TenantAlertFixtures.TENANT,
                TenantAlertFixtures.RESPONSIBLE, runId.toString(), "2026-11", List.of("rota-01"),
                "route-error", TenantAlertService.BATCH_FAILURE_TITLE_CODE,
                TenantAlertService.BATCH_FAILURE_BODY_CODE, TenantAlertFixtures.NOW);
        Optional<UUID> id = adapter.deliverBatchFailureWithDeclaredEvent(
                notice, TenantAlertFixtures.KNOWN_EVENT, TenantAlertFixtures.KNOWN_VERSION);
        assertThat(id).isPresent();
        assertThat(db.insercoes.get()).isEqualTo(1);
        assertThat(db.avisoPorMensagem).containsKey(runId);
    }

    @Test
    void batchFailureWithUndeclaredEventIsRefusedByTheRealCenter() {
        // A prova do portão real: um nome NÃO declarado no catálogo é recusado
        // pela ingestão da P3.36 (ApiException.Conflict) — é exatamente por
        // isso que a ED-07 não pode rotear a falha de lote hoje sem a
        // declaração da integração/P1.38.
        FakeDb db = new FakeDb();
        var adapter = new P336NotificationCenterAdapter(realCenter(db),
                Clock.fixed(TenantAlertFixtures.NOW, ZoneOffset.UTC));
        var notice = new TenantAlertPorts.BatchFailureNotice(TenantAlertFixtures.TENANT,
                TenantAlertFixtures.RESPONSIBLE, UUID.randomUUID().toString(), "2026-11",
                List.of("rota-01"), "route-error", TenantAlertService.BATCH_FAILURE_TITLE_CODE,
                TenantAlertService.BATCH_FAILURE_BODY_CODE, TenantAlertFixtures.NOW);
        assertThatThrownBy(() -> adapter.deliverBatchFailureWithDeclaredEvent(
                notice, "batch.run.failed", 1))
                .isInstanceOf(ApiException.Conflict.class);
        assertThat(db.insercoes.get()).isZero();
    }

    /**
     * JDBC falso mínimo, no padrão da prova de fiação da própria P3.36:
     * guarda os avisos por {@code source_message_id} (a chave de dedup),
     * serve as preferências plantadas (nenhuma, aqui) e registra o
     * {@code deliver_after} de cada INSERT. Só entende os SQLs do ingest.
     */
    private static final class FakeDb {
        final Map<UUID, UUID> avisoPorMensagem = new LinkedHashMap<>();
        final List<Object[]> preferencias = new ArrayList<>();
        final AtomicInteger insercoes = new AtomicInteger();
        volatile Timestamp ultimoDeliverAfter;

        DataSource dataSource() {
            return proxy(DataSource.class, (p, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    return connection();
                }
                return padrao(method.getReturnType());
            });
        }

        private Connection connection() {
            return proxy(Connection.class, (p, method, args) -> {
                if (method.getName().equals("prepareStatement")) {
                    return statement((String) args[0]);
                }
                return padrao(method.getReturnType());
            });
        }

        private PreparedStatement statement(String sql) {
            Map<Integer, Object> params = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (p, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer) {
                    params.put((Integer) args[0], args[1]);
                    return null;
                }
                if (name.equals("executeQuery")) {
                    return resultSet(executar(sql, params));
                }
                if (name.equals("executeUpdate")) {
                    return 1;
                }
                return padrao(method.getReturnType());
            });
        }

        private List<Object[]> executar(String sql, Map<Integer, Object> params) {
            if (sql.contains("set_config")) {
                return List.of();
            }
            if (sql.contains("INSERT INTO")) {
                Map<String, Integer> colunas = colunasDoInsert(sql);
                UUID fonte = (UUID) params.get(colunas.get("source_message_id"));
                if (avisoPorMensagem.containsKey(fonte)) {
                    return List.of(); // ON CONFLICT DO NOTHING: nenhuma linha volta
                }
                UUID id = (UUID) params.get(colunas.get("id"));
                avisoPorMensagem.put(fonte, id);
                insercoes.incrementAndGet();
                ultimoDeliverAfter = (Timestamp) params.get(colunas.get("deliver_after"));
                return List.<Object[]>of(new Object[]{id});
            }
            if (sql.contains("source_message_id")) {
                UUID existente = avisoPorMensagem.get((UUID) params.get(2));
                return existente == null ? List.of() : List.<Object[]>of(new Object[]{existente});
            }
            if (sql.contains("notification_preferences")) {
                return new ArrayList<>(preferencias);
            }
            throw new IllegalStateException("SQL inesperado no fake: " + sql);
        }

        private ResultSet resultSet(List<Object[]> linhas) {
            int[] cursor = {-1};
            return proxy(ResultSet.class, (p, method, args) -> {
                String name = method.getName();
                switch (name) {
                    case "next" -> {
                        cursor[0]++;
                        return cursor[0] < linhas.size();
                    }
                    case "getObject", "getString", "getBoolean", "getTimestamp", "getLong" -> {
                        return linhas.get(cursor[0])[(Integer) args[0] - 1];
                    }
                    default -> {
                        return padrao(method.getReturnType());
                    }
                }
            });
        }

        private static Map<String, Integer> colunasDoInsert(String sql) {
            int abre = sql.indexOf('(');
            int fecha = sql.indexOf(')', abre);
            String[] nomes = sql.substring(abre + 1, fecha).split(",");
            Map<String, Integer> mapa = new LinkedHashMap<>();
            int parametro = 0;
            for (String nome : nomes) {
                String coluna = nome.trim();
                // "channel" NÃO é parâmetro no INSERT real da P3.36: o VALUES
                // traz o literal 'IN_APP' — contá-lo deslocaria os índices
                // (source_message_id/deliver_after cairiam sobre Timestamps).
                if (coluna.equals("channel")) {
                    continue;
                }
                mapa.put(coluna, ++parametro);
            }
            return mapa;
        }

        private static <T> T proxy(Class<T> iface, InvocationHandler handler) {
            return iface.cast(Proxy.newProxyInstance(FakeDb.class.getClassLoader(), new Class<?>[]{iface}, handler));
        }

        private static Object padrao(Class<?> tipo) {
            if (tipo == boolean.class) return false;
            if (tipo == int.class) return 0;
            if (tipo == long.class) return 0L;
            if (tipo == double.class) return 0d;
            if (tipo == float.class) return 0f;
            if (tipo == short.class) return (short) 0;
            if (tipo == byte.class) return (byte) 0;
            if (tipo == char.class) return (char) 0;
            return null;
        }
    }
}
