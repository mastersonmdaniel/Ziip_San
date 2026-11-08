package br.com.ziipsan.intelligence.losses.nr15;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Declaracao anual do diagnostico de perdas (ED-78; NR 15, art. 4o):
 * o balanco hidrico padronizado do ano, com os indicadores do
 * diagnostico (art. 5o), submetido a entidade reguladora
 * infranacional do tenant.
 *
 * <p>Ciclo de vida: {@code DRAFT -> SUBMITTED -> PUBLISHED}. A
 * submissao registra o instante e a agencia destinataria (a do
 * perfil regulatorio que adotou a NR 15). A publicacao registra
 * canal e referencia (art. 12, paragrafo unico: resultados
 * publicados na pagina do prestador; art. 14, III: a ERI publica os
 * resultados na pagina dela) - a referencia (URL/ato) e declarada
 * por quem publica; nenhuma e fabricada aqui.
 *
 * <p>A criacao e do {@link Nr15DeclarationService}, que exige
 * aplicabilidade, volume produzido reconciliado com a P4.2 e
 * indicadores do mesmo conjunto de fotos do balanco.
 */
public final class AnnualDeclaration {

    public enum Status { DRAFT, SUBMITTED, PUBLISHED }

    /** Canal de publicacao dos resultados (art. 12, p.u., e art. 14, III). */
    public enum PublicationChannel { PROVIDER_SITE, REGULATOR_SITE }

    public record Publication(PublicationChannel channel, String reference, Instant publishedAt) {
        public Publication {
            Objects.requireNonNull(channel, "canal de publicacao obrigatorio");
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("referencia da publicacao obrigatoria (declarada, nunca fabricada)");
            }
            Objects.requireNonNull(publishedAt, "instante da publicacao obrigatorio");
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final String agencyCode;
    private final StandardizedWaterBalance balance;
    private final Nr15Indicators indicators;
    private Status status = Status.DRAFT;
    private Instant submittedAt;
    private Publication publication;

    public AnnualDeclaration(UUID id, UUID tenantId, String agencyCode,
                             StandardizedWaterBalance balance, Nr15Indicators indicators) {
        this.id = Objects.requireNonNull(id, "declaracao sem id");
        this.tenantId = Objects.requireNonNull(tenantId, "declaracao sem tenant");
        if (agencyCode == null || agencyCode.isBlank()) {
            throw new IllegalArgumentException("declaracao sem a agencia reguladora destinataria");
        }
        this.agencyCode = agencyCode;
        this.balance = Objects.requireNonNull(balance, "declaracao sem o balanco padronizado");
        this.indicators = Objects.requireNonNull(indicators, "declaracao sem os indicadores do diagnostico");
        if (!balance.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("balanco de outro tenant na declaracao");
        }
    }

    public void submit(Instant at) {
        Objects.requireNonNull(at, "instante da submissao obrigatorio");
        if (status != Status.DRAFT) {
            throw new IllegalStateException("declaracao " + status + " nao volta a submissao");
        }
        this.status = Status.SUBMITTED;
        this.submittedAt = at;
    }

    public void publish(Publication publication) {
        Objects.requireNonNull(publication, "publicacao obrigatoria");
        if (status != Status.SUBMITTED) {
            throw new IllegalStateException("publicacao exige declaracao SUBMITTED (atual: " + status + ")");
        }
        this.publication = publication;
        this.status = Status.PUBLISHED;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String agencyCode() { return agencyCode; }
    public int baseYear() { return balance.baseYear(); }
    public StandardizedWaterBalance balance() { return balance; }
    public Nr15Indicators indicators() { return indicators; }
    public Status status() { return status; }
    public Instant submittedAt() { return submittedAt; }
    public Publication publication() { return publication; }
}
