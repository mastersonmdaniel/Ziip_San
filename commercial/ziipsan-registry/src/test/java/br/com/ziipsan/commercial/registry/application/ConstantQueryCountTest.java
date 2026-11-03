package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.repository.AddressRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerContactRepository;
import br.com.ziipsan.commercial.registry.repository.CustomerRepository;
import br.com.ziipsan.commercial.registry.service.CustomerDocumentService;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0.22: listas com número constante de consultas, independente do tamanho da página.
 *
 * <p>A lista de clientes emite: 1 consulta de página + 1 de COUNT (JpqlQuery.page) +
 * 1 de contagem de imóveis em lote (unitCounts, um único IN, nunca N consultas).
 * Total constante: 3, com size 1 ou 100. O teste conta as chamadas a
 * {@code EntityManager.createQuery} com EM mockado — sem banco, sem flakiness de
 * contador de statements — e prova que o lote não vira uma consulta por linha.
 */
class ConstantQueryCountTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @SuppressWarnings("unchecked")
    private EntityManager emWith(TypedQuery<String> data, TypedQuery<Long> count) {
        EntityManager em = mock(EntityManager.class);
        when(em.createQuery(anyString(), eq(String.class))).thenReturn(data);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(count);
        return em;
    }

    @Test
    @DisplayName("JpqlQuery.page: exatamente 2 consultas (página + COUNT) com size 1 e com size 100")
    void pageAlwaysIssuesTwoQueries() {
        for (int size : List.of(1, 100)) {
            TypedQuery<String> data = mock(TypedQuery.class);
            when(data.getResultList()).thenReturn(List.of());
            TypedQuery<Long> count = mock(TypedQuery.class);
            when(count.getSingleResult()).thenReturn(0L);
            EntityManager em = emWith(data, count);

            new JpqlQuery("c", "Customer c").page(em, String.class,
                    new PageSpec(0, size, null, true), "c.id");

            // 1 consulta de página + 1 de COUNT, nem mais com página maior.
            verify(em, times(1)).createQuery(anyString(), eq(String.class));
            verify(em, times(1)).createQuery(anyString(), eq(Long.class));
        }
    }

    @Test
    @DisplayName("unitCounts: exatamente 1 consulta com 1 id e com 100 ids, via IN único")
    void unitCountsIsOneQueryRegardlessOfIds() {
        TenantContextHolder.setTenantId(UUID.randomUUID());
        for (int n : List.of(1, 100)) {
            @SuppressWarnings("unchecked")
            TypedQuery<Object[]> query = mock(TypedQuery.class);
            when(query.getResultList()).thenReturn(List.of());
            // unitCounts encadeia setParameter(...).setParameter(...): o mock precisa
            // devolver a si mesmo, senão o segundo setParameter dá NPE.
            when(query.setParameter(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any())).thenReturn(query);
            EntityManager em = mock(EntityManager.class);
            when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(query);

            CustomerService service = new CustomerService(
                    mock(CustomerRepository.class), mock(CustomerContactRepository.class),
                    mock(AddressRepository.class), mock(CustomerDocumentService.class));
            ReflectionTestUtils.setField(service, "em", em);

            List<UUID> ids = IntStream.range(0, n).mapToObj(i -> UUID.randomUUID()).toList();
            service.unitCounts(ids);

            verify(em, times(1)).createQuery(anyString(), eq(Object[].class));
            ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
            verify(em).createQuery(jpql.capture(), eq(Object[].class));
            assertThat(jpql.getValue()).contains("in :ids");
            verify(query).setParameter("ids", ids);
        }
    }
}
