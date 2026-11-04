package br.com.ziipsan.platform.about;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * P1.24: registros do Sobre completo (ADR-0021; AJUDA-SUPORTE-SOBRE.md §9).
 *
 * <p>Sem anotações de framework, no mesmo padrão do módulo de documentos da
 * P1.20: o repositório é construído explicitamente e o serviço não conhece
 * Spring — os controllers da aplicação é que os expõem.
 *
 * <p>Contrato de reuso (P2.25): o ciclo de revisão e ciência dos documentos
 * reaproveita {@link DocumentAcceptance} — o aceite JÁ É a ciência; a
 * revisão futura acrescenta colunas a {@code platform.document_acceptance}
 * pela migration da P2.25, sem criar uma segunda tabela de aceite.
 */
public final class AboutRecords {

    private AboutRecords() {
    }

    /** Um documento legal do catálogo global ({@code platform.document}). */
    public record DocumentCatalog(String code, String title, String currentVersion,
                                  String currentContentHash, OffsetDateTime publishedAt, boolean active) {
    }

    /**
     * Um aceite: quem aceitou, o quê (código + versão + hash do texto exato),
     * quando, de onde (IP) e como se autenticou. É a evidência que a
     * assinatura simples da P3.34 usa — por isso nenhum campo é atualizado
     * depois de gravado (a role da aplicação nem tem UPDATE na tabela).
     */
    public record DocumentAcceptance(UUID id, UUID tenantId, UUID userId, String documentCode,
                                     String documentVersion, String contentHash,
                                     OffsetDateTime acceptedAt, String ipAddress, String authMethod) {
    }

    /** Uma novidade vista: uma vez por versão por usuário (ADR-0021 decisão 8). */
    public record ReleaseNoteView(UUID id, UUID tenantId, UUID userId, String version,
                                  OffsetDateTime viewedAt) {
    }
}
