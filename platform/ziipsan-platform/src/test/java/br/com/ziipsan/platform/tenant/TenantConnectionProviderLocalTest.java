package br.com.ziipsan.platform.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0.28: o proxy de conexao aplica o contexto LOCAL a transacao depois do BEGIN
 * ({@code setAutoCommit(false)}), e reaplica apos commit/rollback. Sem banco: mocks
 * de Connection/PreparedStatement. O ensaio contra PostgreSQL real (duas transacoes
 * de tenants diferentes na mesma conexao fisica, via PgBouncer) esta em
 * docs/operacao/local-env.md e e pendencia de ensaio ao vivo, nao de unidade.
 */
class TenantConnectionProviderLocalTest {

    private static DataSource dataSourceReturning(Connection physical) throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(physical);
        return ds;
    }

    private static Connection physicalWithStatements(PreparedStatement apply, PreparedStatement query) throws Exception {
        Connection physical = mock(Connection.class);
        when(physical.prepareStatement(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            return sql.contains("set_config") ? apply : query;
        });
        return physical;
    }

    @Test
    @DisplayName("em transacao, o primeiro statement aplica set_config local (true)")
    void transactionalAppliesLocal() throws Exception {
        PreparedStatement apply = mock(PreparedStatement.class);
        PreparedStatement query = mock(PreparedStatement.class);
        Connection physical = physicalWithStatements(apply, query);
        var provider = new MultiTenantConnectionProvider(dataSourceReturning(physical));
        Connection proxy = provider.getConnection(new TenantRef("tenant_a", java.util.UUID.randomUUID()));
        proxy.setAutoCommit(false);
        proxy.prepareStatement("SELECT 1");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(physical, atLeastOnce()).prepareStatement(sql.capture());
        assertThat(sql.getAllValues()).anySatisfy(s -> assertThat(s)
                .contains("set_config('app.current_tenant'")
                .contains(", true)"));
        assertThat(sql.getAllValues()).noneSatisfy(s -> assertThat(s).contains(", false)"));
    }

    @Test
    @DisplayName("apos commit, o proximo statement reaplica o contexto (a transacao anterior o descartou)")
    void reappliesAfterCommit() throws Exception {
        PreparedStatement apply = mock(PreparedStatement.class);
        PreparedStatement query = mock(PreparedStatement.class);
        Connection physical = physicalWithStatements(apply, query);
        var provider = new MultiTenantConnectionProvider(dataSourceReturning(physical));
        Connection proxy = provider.getConnection(new TenantRef("tenant_a", java.util.UUID.randomUUID()));
        proxy.setAutoCommit(false);
        proxy.prepareStatement("SELECT 1");
        proxy.commit();
        proxy.setAutoCommit(false);
        proxy.prepareStatement("SELECT 2");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(physical, atLeastOnce()).prepareStatement(sql.capture());
        List<String> applies = sql.getAllValues().stream().filter(s -> s.contains("set_config")).toList();
        assertThat(applies).hasSize(2);
        assertThat(applies).allSatisfy(s -> assertThat(s).contains(", true)"));
    }

    @Test
    @DisplayName("sem transacao (autocommit), o fallback e escopo de sessao, limpo na devolucao")
    void autocommitFallsBackToSessionScope() throws Exception {
        PreparedStatement apply = mock(PreparedStatement.class);
        PreparedStatement query = mock(PreparedStatement.class);
        Connection physical = physicalWithStatements(apply, query);
        var provider = new MultiTenantConnectionProvider(dataSourceReturning(physical));
        Connection proxy = provider.getConnection(new TenantRef("tenant_a", java.util.UUID.randomUUID()));
        proxy.prepareStatement("SELECT 1");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(physical, atLeastOnce()).prepareStatement(sql.capture());
        assertThat(sql.getAllValues()).anySatisfy(s -> assertThat(s).contains(", false)"));
    }
}
