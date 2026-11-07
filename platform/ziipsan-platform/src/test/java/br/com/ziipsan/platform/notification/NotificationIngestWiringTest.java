package br.com.ziipsan.platform.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fiação da ingestão (correção pós-revisão da P3.36): as preferências do destinatário, o
 * {@code notify.digest_default} do tenant e a deduplicação por mensagem são exercitados no
 * SERVIÇO REAL ({@link NotificationCenterService#ingest}), não só na regra pura — sobre um
 * JDBC falso mínimo (proxy) que guarda as linhas em memória e honra o
 * {@code ON CONFLICT DO NOTHING} da chave de dedup, como o índice único parcial da V1_15.
 * Valores de {@code deliver_after} calculados à mão (America/Sao_Paulo = UTC-3):
 * 2026-03-10T13:00:00Z = 10:00 local; 2026-03-10T02:30:00Z = 2026-03-09 23:30 local;
 * dia seguinte 00:00 local = 2026-03-11T03:00:00Z; fim do silêncio 22:00–07:00 = 07:00
 * local = 2026-03-10T10:00:00Z.
 */
class NotificationIngestWiringTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final Instant AS_10H_LOCAL = Instant.parse("2026-03-10T13:00:00Z");
    private static final Instant AS_23H30_LOCAL = Instant.parse("2026-03-10T02:30:00Z");

    private static final BusinessEventCatalog CATALOG = (name, version) -> {
        if (version != 1) {
            return Optional.empty();
        }
        return switch (name) {
            case "billing.bill.issued" -> Optional.of(new CatalogEvent(name, 1, NotificationCategory.BILLING));
            case "legal.notice.published" -> Optional.of(new CatalogEvent(name, 1, NotificationCategory.LEGAL_NOTICE));
            default -> Optional.empty();
        };
    };

    private static final ConsentGate GATE_NAO_USADO = new ConsentGate() {
        @Override
        public boolean isVerifiedContact(UUID tenantId, UUID contactId) {
            throw new AssertionError("a ingestão não consulta o gate de consentimento");
        }

        @Override
        public boolean hasActiveConsent(UUID tenantId, UUID customerId, UUID contactId, UUID consentId, String purposeCode) {
            throw new AssertionError("a ingestão não consulta o gate de consentimento");
        }
    };

    private static NotificationCenterService service(FakeDb db, DigestMode padraoDoTenant, Instant agora) {
        NotificationParameters parameters = new NotificationParameters() {
            @Override
            public DigestMode digestDefault(UUID tenantId) {
                return padraoDoTenant;
            }

            @Override
            public Set<NotificationCategory> tenantMandatoryCategories(UUID tenantId) {
                return Set.of();
            }
        };
        return new NotificationCenterService(db.dataSource(), "test", TENANT, CATALOG, GATE_NAO_USADO,
                parameters, SP, Clock.fixed(agora, SP));
    }

    private static OutboxMessage mensagem(UUID id, String tipo) {
        String payload = "{\"eventVersion\":1,\"recipientUserId\":\"" + USER
                + "\",\"titleCode\":\"bill.issued.title\",\"bodyCode\":\"bill.issued.body\"}";
        return new OutboxMessage(id, TENANT, tipo, payload, AS_10H_LOCAL);
    }

    @Test
    @DisplayName("CORREÇÃO (idempotência): a MESMA mensagem ingerida 2× gera 1 aviso e devolve o mesmo id")
    void redeliveryDoesNotDuplicate() {
        FakeDb db = new FakeDb();
        NotificationCenterService service = service(db, DigestMode.IMMEDIATE, AS_10H_LOCAL);
        UUID messageId = UUID.randomUUID();

        Optional<UUID> primeira = service.ingest(mensagem(messageId, "billing.bill.issued"));
        Optional<UUID> reentrega = service.ingest(mensagem(messageId, "billing.bill.issued"));

        assertThat(primeira).isPresent();
        assertThat(reentrega).isEqualTo(primeira);
        assertThat(db.insercoes.get()).isEqualTo(1);
        assertThat(db.avisoPorMensagem).hasSize(1);
    }

    @Test
    @DisplayName("CORREÇÃO (fiação): canal IN_APP desligado na preferência SUPRIME a linha de categoria comum")
    void disabledInAppSuppresses() {
        FakeDb db = new FakeDb();
        db.preferencia(false, null, null, null);
        NotificationCenterService service = service(db, DigestMode.IMMEDIATE, AS_10H_LOCAL);

        assertThat(service.ingest(mensagem(UUID.randomUUID(), "billing.bill.issued"))).isEmpty();
        assertThat(db.insercoes.get()).isZero();
    }

    @Test
    @DisplayName("CORREÇÃO (fiação): categoria OBRIGATÓRIA ignora preferência desligada, resumo e silêncio — entrega já")
    void mandatoryIgnoresEverything() {
        FakeDb db = new FakeDb();
        db.preferencia(false, "DAILY", LocalTime.of(22, 0), LocalTime.of(7, 0));
        NotificationCenterService service = service(db, DigestMode.DAILY, AS_23H30_LOCAL);

        Optional<UUID> id = service.ingest(mensagem(UUID.randomUUID(), "legal.notice.published"));

        assertThat(id).isPresent();
        assertThat(db.ultimoDeliverAfter).isEqualTo(Timestamp.from(AS_23H30_LOCAL));
    }

    @Test
    @DisplayName("CORREÇÃO (fiação): silêncio 22:00–07:00 vigente DEFERE a entrega para o fim da janela (07:00 local = 10:00Z)")
    void silenceDefersDelivery() {
        FakeDb db = new FakeDb();
        db.preferencia(true, null, LocalTime.of(22, 0), LocalTime.of(7, 0));
        NotificationCenterService service = service(db, DigestMode.IMMEDIATE, AS_23H30_LOCAL);

        assertThat(service.ingest(mensagem(UUID.randomUUID(), "billing.bill.issued"))).isPresent();
        assertThat(db.ultimoDeliverAfter).isEqualTo(Timestamp.from(Instant.parse("2026-03-10T10:00:00Z")));
    }

    @Test
    @DisplayName("CORREÇÃO (fiação): sem preferência, o digest DIÁRIO do tenant (notify.digest_default) agenda para o dia seguinte 00:00 local (03:00Z)")
    void tenantDailyDigestSchedules() {
        FakeDb db = new FakeDb();
        NotificationCenterService service = service(db, DigestMode.DAILY, AS_10H_LOCAL);

        assertThat(service.ingest(mensagem(UUID.randomUUID(), "billing.bill.issued"))).isPresent();
        assertThat(db.ultimoDeliverAfter).isEqualTo(Timestamp.from(Instant.parse("2026-03-11T03:00:00Z")));
    }

    @Test
    @DisplayName("CORREÇÃO (fiação): o digest da PREVISÃO do usuário (IMMEDIATE) vence o padrão diário do tenant")
    void preferenceDigestOverridesTenantDefault() {
        FakeDb db = new FakeDb();
        db.preferencia(true, "IMMEDIATE", null, null);
        NotificationCenterService service = service(db, DigestMode.DAILY, AS_10H_LOCAL);

        assertThat(service.ingest(mensagem(UUID.randomUUID(), "billing.bill.issued"))).isPresent();
        assertThat(db.ultimoDeliverAfter).isEqualTo(Timestamp.from(AS_10H_LOCAL));
    }

    /**
     * JDBC falso mínimo: guarda os avisos por {@code source_message_id} (a chave de dedup),
     * serve as linhas de preferência plantadas pelo teste e registra o {@code deliver_after}
     * de cada INSERT. Só entende os SQLs que o {@code ingest} emite.
     */
    private static final class FakeDb {
        final Map<UUID, UUID> avisoPorMensagem = new LinkedHashMap<>();
        final List<Object[]> preferencias = new ArrayList<>();
        final AtomicInteger insercoes = new AtomicInteger();
        volatile Timestamp ultimoDeliverAfter;

        void preferencia(boolean enabled, String digestMode, LocalTime silenceStart, LocalTime silenceEnd) {
            preferencias.add(new Object[]{enabled, digestMode, silenceStart, silenceEnd});
        }

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
            for (int i = 0; i < nomes.length; i++) {
                mapa.put(nomes[i].trim(), i + 1);
            }
            return mapa;
        }

        private static <T> T proxy(Class<T> iface, InvocationHandler handler) {
            return iface.cast(Proxy.newProxyInstance(FakeDb.class.getClassLoader(), new Class<?>[]{iface}, handler));
        }

        private static Object padrao(Class<?> tipo) {
            if (tipo == boolean.class) {
                return false;
            }
            if (tipo == int.class) {
                return 0;
            }
            if (tipo == long.class) {
                return 0L;
            }
            if (tipo == double.class) {
                return 0d;
            }
            if (tipo == float.class) {
                return 0f;
            }
            if (tipo == short.class) {
                return (short) 0;
            }
            if (tipo == byte.class) {
                return (byte) 0;
            }
            if (tipo == char.class) {
                return (char) 0;
            }
            return null;
        }
    }
}
