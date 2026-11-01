package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.core.api.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P0.16: o teto de página em JpqlQuery.page, sem banco. */
class PageCeilingTest {

    @SuppressWarnings("unchecked")
    private EntityManager emWithMocks(TypedQuery<String> query, TypedQuery<Long> count) {
        EntityManager em = mock(EntityManager.class);
        when(em.createQuery(anyString(), eq(String.class))).thenReturn(query);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(count);
        return em;
    }

    @Test
    @DisplayName("page*size acima de Integer.MAX_VALUE: 400 invalid-page, sem tocar na consulta")
    void offsetOverflowIs400() {
        TypedQuery<String> query = mock(TypedQuery.class);
        TypedQuery<Long> count = mock(TypedQuery.class);
        EntityManager em = emWithMocks(query, count);
        JpqlQuery jpql = new JpqlQuery("l", "Locality l");
        // 30.000.000 * 100 = 3.000.000.000 > 2.147.483.647: em int enrolaria para negativo.
        assertThatThrownBy(() -> jpql.page(em, String.class, new PageSpec(30000000, 100, null, true), "l.id"))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("limite");
        verify(query, never()).setFirstResult(anyInt());
    }

    @Test
    @DisplayName("construção direta com page negativa ou size zero: 400 (PageSpec.of não é o único caminho)")
    void directInvalidSpecIs400() {
        TypedQuery<String> query = mock(TypedQuery.class);
        TypedQuery<Long> count = mock(TypedQuery.class);
        EntityManager em = emWithMocks(query, count);
        JpqlQuery jpql = new JpqlQuery("l", "Locality l");
        assertThatThrownBy(() -> jpql.page(em, String.class, new PageSpec(-1, 25, null, true), "l.id"))
                .isInstanceOf(ApiException.BadRequest.class);
        assertThatThrownBy(() -> jpql.page(em, String.class, new PageSpec(0, 0, null, true), "l.id"))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    @DisplayName("página normal: offset em int, sem erro")
    void normalPageWorks() {
        TypedQuery<String> query = mock(TypedQuery.class);
        TypedQuery<Long> count = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(List.of());
        when(count.getSingleResult()).thenReturn(0L);
        EntityManager em = emWithMocks(query, count);
        PageResult<String> result = new JpqlQuery("l", "Locality l")
                .page(em, String.class, new PageSpec(2, 25, null, true), "l.id");
        verify(query).setFirstResult(50);
        verify(query).setMaxResults(25);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(0);
    }
}
