package br.com.ziipsan.platform.search;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Visão salva de filtros e colunas por usuário (P1.33, tabela {@code saved_views} da V1_17).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 *
 * <p>Deliberadamente SEM FK para {@code user_preferences} (P1.18, frente paralela — a tabela
 * nem existe nesta base) e SEM FK para unidade organizacional (P3.30, não existe ainda):
 * o dono é o {@code owner_user_id} (o {@code sub} do token) e {@code shared_org_unit_id} é
 * um UUID solto até a P3.30 criar o escopo por área. Uma visão {@code ORG_UNIT} só é listada
 * ao próprio dono até lá — armazenada, não fingida (ver {@code SavedViewService} no app).
 *
 * <p>{@code filters}, {@code columns} e {@code sort} são JSON cru (o serviço valida que
 * {@code filters} é um objeto JSON e {@code columns} um array JSON): o JPA os mapeia como
 * {@code jsonb} sem desserializar, para a visão guardar qualquer filtro futuro de lista sem
 * migração nova a cada campo.
 */
@Entity
@Table(name = "saved_views")
@Getter
@Setter
public class SavedView extends TenantAwareEntity {

    /** Dono da visão: o {@code sub} do token (o mesmo da GUC {@code app.current_user}). */
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Lista a que a visão se aplica: {@code customers}, {@code consumer-units},
     * {@code service-orders}, {@code bills}, {@code documents} ou {@code global} (a busca
     * global da própria P1.33, cuja visão guarda o {@code q} reaplicável).
     */
    @Column(name = "resource", nullable = false)
    private String resource;

    /** Filtros da lista, como objeto JSON (ex.: {@code {"q": "..."}}). */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "filters", nullable = false, columnDefinition = "jsonb")
    private String filters = "{}";

    /** Colunas visíveis, como array JSON de chaves (ex.: {@code ["name", "matricula"]}). */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "columns", nullable = false, columnDefinition = "jsonb")
    private String columns = "[]";

    /** Ordenação, como objeto JSON (ex.: {@code {"key": "name", "dir": "asc"}}); nulo = padrão da lista. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "sort", columnDefinition = "jsonb")
    private String sort;

    /** Densidade da tabela da visão: {@code comfortable} (padrão) ou {@code compact}. */
    @Column(name = "density", nullable = false)
    private String density = "comfortable";

    /** Visibilidade: {@code PRIVATE} (só o dono) ou {@code ORG_UNIT} (compartilhada — P3.30). */
    @Column(name = "visibility", nullable = false)
    private String visibility = "PRIVATE";

    /**
     * Unidade organizacional com a qual a visão é compartilhada (só com
     * {@code visibility = ORG_UNIT}). Sem FK até a P3.30: um UUID solto, validado
     * como UUID pelo serviço, sem resolução de escopo nesta frente.
     */
    @Column(name = "shared_org_unit_id")
    private UUID sharedOrgUnitId;

    /** Visão padrão do dono para o recurso (o frontend a aplica ao abrir a lista). */
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
