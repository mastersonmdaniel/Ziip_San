package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.core.tenant.TenantContextHolder;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P1.41 (§2.2): nome na conta — usuário titular; sem usuário, o padrão de
 * fábrica retém a fatura (nulo); as opções do tenant mudam o fallback; o
 * nome social vale na conta (P2.17), salvo modo CIVIL_NAME_ONLY.
 */
class BillHolderResolverTest {

    private static final UUID TENANT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID UNIT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID LAST_USER = UUID.randomUUID();

    private EntityManager em;
    private BillHolderResolver resolver;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(UUID.randomUUID());
        em = mock(EntityManager.class);
        resolver = new BillHolderResolver();
        try {
            var field = BillHolderResolver.class.getDeclaredField("em");
            field.setAccessible(true);
            field.set(resolver, em);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private void stubLinks(List<Object[]> links, UUID lastUser) {
        when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            if (sql.contains("ORDER BY uc.end_date DESC")) {
                when(q.getResultList()).thenReturn(lastUser == null ? List.of() : List.of(lastUser));
            } else if (sql.contains("FROM unit_customers")) {
                when(q.getResultList()).thenReturn(links);
            } else if (sql.contains("FROM customers")) {
                when(q.getResultList()).thenAnswer(inv2 -> {
                    // O id vem do segundo setParameter("id", ...); simplificação:
                    // o teste usa um único cliente por consulta de nome.
                    // (Testemunha explícita: sem ela, o array seria expandido
                    // como varargs e a linha viraria 3 elementos.)
                    return List.<Object[]>of(currentNameRow());
                });
            }
            return q;
        });
    }

    private Object[] currentNameRow() {
        return nameRow;
    }

    private Object[] nameRow = new Object[]{"Nome Civil", null, "PF"};

    private void stubName(UUID id, String name, String social, String personType) {
        // Reconfigura: a próxima consulta de nome devolve esta linha.
        nameRow = new Object[]{name, social, personType};
    }

    @Test
    @DisplayName("usuário vigente é o nome na conta; com OWNER, mostra os dois")
    void userHolderOnlyAndWithOwner() {
        stubLinks(List.of(new Object[]{USER, "USER"}, new Object[]{OWNER, "OWNER"}), null);
        stubName(USER, "Titular", null, "PF");
        assertThat(resolver.holderName(UNIT, "USER_HOLDER_ONLY")).isEqualTo("Titular");
    }

    @Test
    @DisplayName("sem usuário: fábrica retém a fatura (nulo); proprietário provisório mostra o dono")
    void vacantPolicies() {
        stubLinks(List.<Object[]>of(new Object[]{OWNER, "OWNER"}), null);
        stubName(OWNER, "Dono", null, "PF");
        assertThat(resolver.holderName(UNIT, "USER_HOLDER_ONLY", "NO_HOLDER_BILL_HELD",
                "SOCIAL_EVERYWHERE_EXCEPT_FISCAL")).isNull();
        assertThat(resolver.holderName(UNIT, "USER_HOLDER_ONLY", "OWNER_AS_PROVISIONAL_HOLDER",
                "SOCIAL_EVERYWHERE_EXCEPT_FISCAL")).isEqualTo("Dono");
    }

    @Test
    @DisplayName("sem usuário nem dono: LAST_USER_UNTIL_NOTICE mostra o último usuário")
    void lastUserUntilNotice() {
        stubLinks(List.of(), LAST_USER);
        stubName(LAST_USER, "Último", null, "PF");
        assertThat(resolver.holderName(UNIT, "USER_HOLDER_ONLY", "LAST_USER_UNTIL_NOTICE",
                "SOCIAL_EVERYWHERE_EXCEPT_FISCAL")).isEqualTo("Último");
    }

    @Test
    @DisplayName("nome social vale na conta, salvo modo civil")
    void socialNameOnBill() {
        stubLinks(List.<Object[]>of(new Object[]{USER, "USER"}), null);
        stubName(USER, "Civil", "Social", "PF");
        assertThat(resolver.holderName(UNIT, "USER_HOLDER_ONLY", "NO_HOLDER_BILL_HELD",
                "SOCIAL_EVERYWHERE_EXCEPT_FISCAL")).isEqualTo("Social");
        assertThat(resolver.holderName(UNIT, "USER_HOLDER_ONLY", "NO_HOLDER_BILL_HELD",
                "CIVIL_NAME_ONLY")).isEqualTo("Civil");
    }
}
