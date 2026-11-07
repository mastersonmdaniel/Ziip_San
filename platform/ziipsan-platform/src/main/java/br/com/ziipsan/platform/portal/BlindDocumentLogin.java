package br.com.ziipsan.platform.portal;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.security.crypto.FieldProtector;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Autenticador do portal (contrato do SPI proprio do Keycloak, P3.3): o
 * identificador digitado (CPF, CNPJ ou e-mail) e localizado pelo INDICE
 * CEGO — o valor em claro nunca e gravado nem consultado. O documento e
 * normalizado por {@link DocumentNumber} (P1.39) e o indice e calculado por
 * {@link FieldProtector} com o contexto proprio do portal; o e-mail usa
 * contexto distinto, para os dois espacos nunca colidirem.
 *
 * <p>Anti-enumeracao: quem chama (o SPI/BFF) recebe o mesmo resultado vazio
 * para identificador inexistente e para senha errada, e a recuperacao de
 * senha responde sempre {@link #UNIFORM_RECOVERY_MESSAGE} — ver
 * {@code PortalAuthenticationFlow}. Esta classe nao conhece senha.
 */
public final class BlindDocumentLogin {

    public static final String DOCUMENT_CONTEXT = "portal.account.document";
    public static final String EMAIL_CONTEXT = "portal.account.email";
    public static final String UNIFORM_RECOVERY_MESSAGE =
            "Se houver uma conta para o identificador informado, as instrucoes foram enviadas ao contato cadastrado.";

    /** Porta de leitura da conta do portal: SOMENTE por indice cego. */
    public interface AccountLookup {
        Optional<UUID> findAccountIdByBlindIndex(UUID tenantId, String context, String blindIndex);
    }

    private final FieldProtector protector;
    private final AccountLookup lookup;

    public BlindDocumentLogin(FieldProtector protector, AccountLookup lookup) {
        if (protector == null || lookup == null) {
            throw new IllegalArgumentException("protector e lookup sao obrigatorios");
        }
        this.protector = protector;
        this.lookup = lookup;
    }

    /** Tipo do identificador digitado, sem expor o valor. */
    public enum IdentifierKind { DOCUMENT, EMAIL, INVALID }

    public static IdentifierKind classify(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return IdentifierKind.INVALID;
        }
        if (DocumentNumber.kind(identifier) != DocumentNumber.Kind.INVALID) {
            return IdentifierKind.DOCUMENT;
        }
        String trimmed = identifier.trim();
        return trimmed.contains("@") && trimmed.indexOf('@') > 0 ? IdentifierKind.EMAIL : IdentifierKind.INVALID;
    }

    /** Indice cego do identificador (documento normalizado ou e-mail em minusculas). */
    public String blindIndexOf(UUID tenantId, String identifier) {
        return switch (classify(identifier)) {
            case DOCUMENT -> protector.indexOf(tenantId, DOCUMENT_CONTEXT, DocumentNumber.normalize(identifier));
            case EMAIL -> protector.indexOf(tenantId, EMAIL_CONTEXT, identifier.trim().toLowerCase(Locale.ROOT));
            case INVALID -> throw new IllegalArgumentException("identificador nao e CPF, CNPJ nem e-mail");
        };
    }

    /** Localiza a conta pelo indice cego; vazio tanto para inexistente quanto para formato invalido. */
    public Optional<UUID> resolveAccount(UUID tenantId, String identifier) {
        IdentifierKind kind = classify(identifier);
        if (kind == IdentifierKind.INVALID) {
            return Optional.empty();
        }
        String context = kind == IdentifierKind.DOCUMENT ? DOCUMENT_CONTEXT : EMAIL_CONTEXT;
        return lookup.findAccountIdByBlindIndex(tenantId, context, blindIndexOf(tenantId, identifier));
    }
}
