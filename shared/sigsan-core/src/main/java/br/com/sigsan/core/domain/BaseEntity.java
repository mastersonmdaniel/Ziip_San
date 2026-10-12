package br.com.sigsan.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Base de toda entidade persistida.
 *
 * <p>O identificador é atribuído em {@link #onCreate()} (UUID v4), e não por
 * {@code @GeneratedValue}: o Hibernate trata uma entidade com id preenchido e gerador como
 * "destacada" e recusa o {@code persist}. Quem precisar do id antes de salvar pode atribuí-lo
 * com {@link #setId(UUID)}.
 *
 * <p>{@code version} é controle de concorrência otimista gerenciado pelo Hibernate. Fica
 * {@code null} em entidade nova, o que o Spring Data usa para decidir entre INSERT e UPDATE;
 * o Hibernate grava 0 no primeiro INSERT e incrementa a cada UPDATE. Não alterar à mão.
 *
 * <p>Atenção: classes que herdam desta <b>não podem usar {@code @Builder}</b>. O Lombok
 * descarta em silêncio os campos herdados, e a identidade ficaria nula em runtime.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
