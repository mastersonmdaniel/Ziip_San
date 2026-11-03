package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.core.api.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P0.22: paginação keyset em JpqlQuery.pageAfter, sem banco. */
class KeysetPaginationTest {

    private static final UUID ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ID_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @SuppressWarnings("unchecked")
    private EntityManager emReturning(TypedQuery<String> query) {
        EntityManager em = mock(EntityManager.class);
        when(em.createQuery(anyString(), eq(String.class))).thenReturn(query);
        return em;
    }

    private String jpqlOf(EntityManager em) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(em).createQuery(captor.capture(), eq(String.class));
        return captor.getValue();
    }

    @Test
    @DisplayName("primeira página: sem predicado de cursor, limite size+1 para detectar próxima")
    void firstPageHasNoCursorPredicate() {
        TypedQuery<String> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(List.of("ana", "bia"));
        EntityManager em = emReturning(query);

        KeysetPage<String> page = new JpqlQuery("c", "Customer c")
                .pageAfter(em, String.class, 2, "lower(c.name)", "c.id", null, s -> new KeysetCursor(s, ID_1));

        assertThat(jpqlOf(em)).doesNotContain("cursorSort")
                .contains("order by lower(c.name) asc, c.id asc");
        verify(query).setMaxResults(3);
        assertThat(page.content()).containsExactly("ana", "bia");
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("página seguinte: predicado (nome > cursor ou nome = cursor e id > id), hasNext e nextCursor")
    void nextPageUsesCursorPredicate() {
        TypedQuery<String> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(List.of("ana", "bia", "caio"));
        EntityManager em = emReturning(query);

        KeysetPage<String> page = new JpqlQuery("c", "Customer c")
                .pageAfter(em, String.class, 2, "lower(c.name)", "c.id",
                        new KeysetCursor("ana", ID_1), s -> new KeysetCursor(s, ID_2));

        String jpql = jpqlOf(em);
        assertThat(jpql).contains("lower(c.name) > :cursorSort")
                .contains("lower(c.name) = :cursorSort and c.id > :cursorId");
        verify(query).setParameter("cursorSort", "ana");
        verify(query).setParameter("cursorId", ID_1);
        verify(query).setMaxResults(3);
        assertThat(page.content()).containsExactly("ana", "bia");
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(new KeysetCursor("bia", ID_2));
    }

    @Test
    @DisplayName("size acima do teto P0.16 (100) é cortado, não rejeitado: busca 101 linhas")
    void sizeAboveCeilingIsCapped() {
        TypedQuery<String> query = mock(TypedQuery.class);
        when(query.getResultList()).thenReturn(List.of());
        EntityManager em = emReturning(query);

        new JpqlQuery("c", "Customer c")
                .pageAfter(em, String.class, 500, "lower(c.name)", "c.id", null, s -> new KeysetCursor(s, ID_3));

        verify(query).setMaxResults(101);
    }

    @Test
    @DisplayName("size zero: 400, sem tocar na consulta")
    void zeroSizeIs400() {
        TypedQuery<String> query = mock(TypedQuery.class);
        EntityManager em = emReturning(query);

        assertThatThrownBy(() -> new JpqlQuery("c", "Customer c")
                .pageAfter(em, String.class, 0, "lower(c.name)", "c.id", null, s -> new KeysetCursor(s, ID_3)))
                .isInstanceOf(ApiException.BadRequest.class);
        verify(em, org.mockito.Mockito.never()).createQuery(anyString(), eq(String.class));
    }
}
