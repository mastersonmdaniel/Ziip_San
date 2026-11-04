package br.com.ziipsan.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * P2.2: o contexto transacional de tenant, sem banco (mocks).
 *
 * <p>O que trava aqui: o contexto e aplicado com {@code set_config(...,
 * true)} (LOCAL a transacao, nunca de sessao), o commit acontece, e o
 * autoCommit original e restaurado antes de devolver a conexao — a
 * combinacao que impede o vazamento de tenant no reaproveitamento do pool.
 * O ensaio contra o banco real (os tres pools, pool de 1 conexao) esta no
 * {@code RolePoolIsolationIT}.
 */
@ExtendWith(MockitoExtension.class)
class TenantTransactionTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Test
    @DisplayName("aplica search_path + tenant + usuario com set_config LOCAL, commita e restaura autoCommit")
    void appliesLocalContextCommitsAndRestoresAutoCommit() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        String result = TenantTransaction.run(dataSource, "tenant_a", tenant, user, conn -> {
            assertThat(conn).isSameAs(connection);
            return "ok";
        });
        assertThat(result).isEqualTo("ok");

        InOrder order = inOrder(connection, statement);
        order.verify(connection).setAutoCommit(false);
        order.verify(connection).commit();
        order.verify(connection).setAutoCommit(true);
        // O SQL aplicado tem os tres set_config com escopo local (true), e os
        // argumentos sao schema, tenant e usuario, nessa ordem.
        verify(connection).prepareStatement(
                """
                        SELECT set_config('search_path', quote_ident(?) || ', public', true),
                               set_config('app.current_tenant', ?, true),
                               set_config('app.current_user', ?, true)""");
        verify(statement).setString(1, "tenant_a");
        verify(statement).setString(2, tenant.toString());
        verify(statement).setString(3, user.toString());
    }

    @Test
    @DisplayName("falha no trabalho: rollback, restaura autoCommit e propaga")
    void failureRollsBackRestoresAndPropagates() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        assertThatThrownBy(() -> TenantTransaction.run(dataSource, "tenant_a", UUID.randomUUID(), null,
                conn -> {
                    throw new SQLException("falha simulada");
                }))
                .isInstanceOf(SQLException.class).hasMessage("falha simulada");
        verify(connection).rollback();
        // Inicio (false) e restauracao (true, o valor original): uma vez cada.
        verify(connection).setAutoCommit(false);
        verify(connection).setAutoCommit(true);
    }

    @Test
    @DisplayName("sem tenant: aplica o contexto vazio (RLS nao devolve nada)")
    void noTenantAppliesEmptyContext() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        TenantTransaction.run(dataSource, null, null, null, conn -> null);
        verify(connection).prepareStatement(
                """
                        SELECT set_config('search_path', 'public', true),
                               set_config('app.current_tenant', '', true),
                               set_config('app.current_user', '', true)""");
    }

    @Test
    @DisplayName("validacao: schema public, schema invalido e schema sem tenant sao recusados")
    void validationRejectsBadSchemas() {
        assertThatThrownBy(() -> TenantTransaction.run(dataSource, "public", UUID.randomUUID(), null,
                conn -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantTransaction.run(dataSource, "Tenant_A", UUID.randomUUID(), null,
                conn -> null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TenantTransaction.run(dataSource, "tenant_a", null, null, conn -> null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
