package br.com.ziipsan.platform.work;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O resolvedor {@link TenantWorkParameters} sobre JDBC falso (correção pós-revisão
 * m2): a trava de {@code wf.self_approval} não mora só na {@link ApprovalRule} —
 * o resolvedor lê {@code parameter_legal_limits} e um valor {@code true} explode
 * JÁ NA RESOLUÇÃO, como erro de estado, antes de qualquer decisão.
 */
class TenantWorkParametersTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Test
    @DisplayName("catálogo adulterado com TRUE aceito: com LOCKED 'false' + ALLOWED_VALUES '[false]' presentes, a resolução explode (IllegalStateException)")
    void tamperedTrueExplodesAtResolution() {
        FakeDb db = new FakeDb("a", "false",
                List.of(new String[]{"LOCKED", "false"}, new String[]{"ALLOWED_VALUES", "[false]"}),
                "true");
        TenantWorkParameters parameters = new TenantWorkParameters(db.dataSource(), "tenant_test", TENANT);
        assertThatThrownBy(() -> parameters.selfApproval(TENANT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("valor FALSE (aceito ou do padrão) resolve normalmente com os limites presentes")
    void falseResolves() {
        FakeDb acceptedFalse = new FakeDb("a", "false",
                List.of(new String[]{"LOCKED", "false"}, new String[]{"ALLOWED_VALUES", "[false]"}),
                "false");
        assertThat(new TenantWorkParameters(acceptedFalse.dataSource(), "tenant_test", TENANT)
                .selfApproval(TENANT)).isFalse();

        FakeDb factoryFalse = new FakeDb("a", "false",
                List.of(new String[]{"LOCKED", "false"}, new String[]{"ALLOWED_VALUES", "[false]"}),
                null);
        assertThat(new TenantWorkParameters(factoryFalse.dataSource(), "tenant_test", TENANT)
                .selfApproval(TENANT)).isFalse();
    }

    @Test
    @DisplayName("mesmo SEM linhas de limite legal, TRUE aceito continua explodindo na resolução (a trava da definição/regra é a defesa restante — limite ausente não vira licença)")
    void trueExplodesEvenWithoutLegalLimitRows() {
        FakeDb db = new FakeDb("a", "false", List.of(), "true");
        TenantWorkParameters parameters = new TenantWorkParameters(db.dataSource(), "tenant_test", TENANT);
        assertThatThrownBy(() -> parameters.selfApproval(TENANT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("classe a, sempre falso");
    }

    @Test
    @DisplayName("padrão de fábrica adulterado para TRUE explode já na conferência da definição")
    void tamperedFactoryDefaultExplodes() {
        FakeDb db = new FakeDb("a", "true",
                List.of(new String[]{"LOCKED", "false"}, new String[]{"ALLOWED_VALUES", "[false]"}),
                null);
        TenantWorkParameters parameters = new TenantWorkParameters(db.dataSource(), "tenant_test", TENANT);
        assertThatThrownBy(() -> parameters.selfApproval(TENANT))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * JDBC falso mínimo do resolvedor: definição (classe + padrão), limites legais
     * e valor aceito vigente — três consultas, nenhum banco. Não decide regra.
     */
    private static final class FakeDb {
        private final String paramClass;
        private final String factoryDefault;
        private final List<String[]> limits;
        private final String acceptedValue;

        FakeDb(String paramClass, String factoryDefault, List<String[]> limits, String acceptedValue) {
            this.paramClass = paramClass;
            this.factoryDefault = factoryDefault;
            this.limits = limits;
            this.acceptedValue = acceptedValue;
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
            return proxy(PreparedStatement.class, (p, method, args) -> {
                if (method.getName().equals("executeQuery")) {
                    return resultSet(rowsFor(sql));
                }
                return padrao(method.getReturnType());
            });
        }

        private List<Object[]> rowsFor(String sql) {
            if (sql.contains("set_config")) {
                return List.of();
            }
            if (sql.contains("parameter_legal_limits")) {
                return limits.stream().map(l -> (Object[]) l).toList();
            }
            if (sql.contains("parameter_definitions")) {
                return List.<Object[]>of(new Object[]{paramClass, factoryDefault});
            }
            if (sql.contains("parameter_versions")) {
                return acceptedValue == null ? List.of() : List.<Object[]>of(new Object[]{acceptedValue});
            }
            throw new IllegalStateException("SQL inesperado no fake: " + sql);
        }

        private ResultSet resultSet(List<Object[]> rows) {
            int[] cursor = {-1};
            return proxy(ResultSet.class, (p, method, args) -> {
                String name = method.getName();
                switch (name) {
                    case "next" -> {
                        cursor[0]++;
                        return cursor[0] < rows.size();
                    }
                    case "getObject", "getString", "getTimestamp" -> {
                        return rows.get(cursor[0])[(Integer) args[0] - 1];
                    }
                    case "getInt", "getLong" -> {
                        return rows.get(cursor[0])[(Integer) args[0] - 1];
                    }
                    default -> {
                        return padrao(method.getReturnType());
                    }
                }
            });
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
