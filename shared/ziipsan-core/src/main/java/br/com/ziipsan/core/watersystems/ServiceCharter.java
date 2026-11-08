package br.com.ziipsan.core.watersystems;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Carta de Servicos — ED-79, com a base legal citada no aceite:
 * Lei 13.460/2017 ({@link #LEGAL_BASIS}). A Carta e DADO DO TENANT:
 * servicos, requisitos, etapas, canais e o prazo DECLARADO de cada
 * servico sao cadastrados pelo prestador — esta tarefa nao afirma
 * prazo, requisito nem compromisso que nao venha do cadastro
 * [SEM FONTE: nenhum conteudo de Carta consta das fontes]. Entrada
 * sem prazo declarado, sem requisito, sem etapa ou sem canal e
 * INCOMPLETA e trava a publicacao (a Carta publicada tem de dizer
 * como o usuario acessa cada servico e em que prazo declarado ele e
 * atendido — estrutura, nao conteudo inventado).
 *
 * <p>A PUBLICACAO e do portal do consumidor da P3.20: sem a
 * referencia devolvida pela porta, nao existe Carta PUBLISHED
 * (invariante do record).
 *
 * <p><b>Correcao D1 (revisao da ED-79, 08/11/2026 — sonda A8):</b>
 * era um record de construtor publico: uma Carta PUBLISHED com
 * {@code portalRef} falso nascia direto, sem a P3.20. Agora e
 * classe final SELADA (construtor privado, padrao M-1 da ED-50):
 * nasce por {@link #draft} e so vira PUBLISHED por
 * {@link #published}, de ACESSO DE PACOTE, chamada somente pelo
 * {@code ServiceCharterService} com a referencia devolvida pela
 * porta da P3.20; SUPERSEDED idem, via {@link #superseded} de
 * pacote. Os acessores seguem o estilo do record original.
 */
public final class ServiceCharter {

    /** Base legal da Carta, literal do aceite (Lei 13.460/2017). */
    public static final String LEGAL_BASIS = "Lei 13.460/2017";

    public enum Status { DRAFT, PUBLISHED, SUPERSEDED }

    private final UUID id;
    private final UUID tenantId;
    private final int version;
    private final Status status;
    private final List<CharterEntry> entries;
    private final String portalRef;
    private final Instant publishedAt;

    private ServiceCharter(UUID id, UUID tenantId, int version, Status status,
                           List<CharterEntry> entries, String portalRef, Instant publishedAt) {
        Objects.requireNonNull(id, "id da Carta e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        if (version < 1) throw new IllegalArgumentException("versao da Carta deve ser >= 1");
        Objects.requireNonNull(status, "situacao da Carta e obrigatoria");
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        if (status == Status.PUBLISHED) {
            if (portalRef == null || portalRef.isBlank())
                throw new IllegalArgumentException("Carta PUBLICADA exige a referencia do portal da P3.20");
            Objects.requireNonNull(publishedAt, "Carta PUBLICADA exige o instante da publicacao");
            if (this.entries.isEmpty())
                throw new IllegalArgumentException("Carta PUBLICADA exige ao menos um servico");
        }
        if (status == Status.DRAFT && portalRef != null)
            throw new IllegalArgumentException("Carta em rascunho nao carrega referencia de publicacao");
        this.id = id;
        this.tenantId = tenantId;
        this.version = version;
        this.status = status;
        this.portalRef = portalRef;
        this.publishedAt = publishedAt;
    }

    public static ServiceCharter draft(UUID id, UUID tenantId, int version, List<CharterEntry> entries) {
        return new ServiceCharter(id, tenantId, version, Status.DRAFT, entries, null, null);
    }

    /** Transicao para PUBLISHED (acesso de pacote — so o ServiceCharterService, com a ref. da P3.20). */
    ServiceCharter published(String portalReference, Instant at) {
        if (status != Status.DRAFT)
            throw new IllegalStateException("so Carta em rascunho pode ser publicada (estado atual: " + status + ")");
        return new ServiceCharter(id, tenantId, version, Status.PUBLISHED, entries, portalReference, at);
    }

    /** Transicao para SUPERSEDED (acesso de pacote — so o ServiceCharterService). */
    ServiceCharter superseded() {
        if (status != Status.PUBLISHED)
            throw new IllegalStateException("so Carta publicada pode ser substituida (SUPERSEDED)");
        return new ServiceCharter(id, tenantId, version, Status.SUPERSEDED, entries, portalRef, publishedAt);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public int version() { return version; }
    public Status status() { return status; }
    public List<CharterEntry> entries() { return entries; }
    public String portalRef() { return portalRef; }
    public Instant publishedAt() { return publishedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceCharter other)) return false;
        return version == other.version
                && Objects.equals(id, other.id) && Objects.equals(tenantId, other.tenantId)
                && status == other.status && Objects.equals(entries, other.entries)
                && Objects.equals(portalRef, other.portalRef)
                && Objects.equals(publishedAt, other.publishedAt);
    }

    @Override public int hashCode() {
        return Objects.hash(id, tenantId, version, status, entries, portalRef, publishedAt);
    }

    @Override public String toString() {
        return "ServiceCharter[id=" + id + ", tenantId=" + tenantId + ", version=" + version
                + ", status=" + status + ", entries=" + entries + ", portalRef=" + portalRef
                + ", publishedAt=" + publishedAt + "]";
    }

    /** Um servico da Carta, como declarado pelo prestador. */
    public record CharterEntry(
            String serviceName,
            String description,
            List<String> requirements,
            List<String> steps,
            String deadlineDeclared,
            List<String> channels) {
        public CharterEntry {
            if (serviceName == null || serviceName.isBlank())
                throw new IllegalArgumentException("nome do servico da Carta e obrigatorio");
            if (description == null || description.isBlank())
                throw new IllegalArgumentException("descricao do servico da Carta e obrigatoria: " + serviceName);
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
            steps = steps == null ? List.of() : List.copyOf(steps);
            channels = channels == null ? List.of() : List.copyOf(channels);
        }

        /** Completa para publicar: requisitos, etapas, canais e o prazo DECLARADO pelo prestador. */
        public boolean complete() {
            return !requirements.isEmpty() && !steps.isEmpty() && !channels.isEmpty()
                    && deadlineDeclared != null && !deadlineDeclared.isBlank();
        }
    }
}
