package br.com.ziipsan.platform.notification;

/**
 * Evento do catálogo único da P1.38, como a central o enxerga: nome e versão declarados e
 * a categoria do aviso. A carga do catálogo, por regra da P1.38, não tem dado pessoal
 * (só identificadores e códigos).
 *
 * <p>Correção pós-revisão: o campo {@code recipientPermission} existia nesta porta e NUNCA
 * era aplicado em lugar nenhum (carregado e morto). Foi REMOVIDO em vez de fingir uma
 * checagem: esta central não tem modelo de permissão de destinatário definido — quando o
 * catálogo real da P1.38 for espelhado e trouxer a permissão como contrato, ela volta à
 * porta junto com a regra que a aplica e o teste dela.
 */
public record CatalogEvent(String name, int version, NotificationCategory category) {
    public CatalogEvent {
        if (name == null || name.isBlank() || version < 1 || category == null) {
            throw new IllegalArgumentException("evento do catálogo exige nome, versão >= 1 e categoria");
        }
    }
}
