package br.com.ziipsan.platform.portal;

import java.util.Optional;
import java.util.UUID;

/**
 * Fluxo de autenticacao do portal (P3.3): resposta e tempo iguais para
 * documento/e-mail inexistente e senha errada. O segredo e do Keycloak; o
 * que esta classe garante e o CONTRATO do fluxo que o SPI/BFF executa:
 * <ul>
 *   <li>a verificacao de senha e invocada SEMPRE — para conta inexistente,
 *       contra uma conta ficticia fixa (custo equivalente), de modo que o
 *       tempo nao revele existencia;</li>
 *   <li>o resultado publico e um so: {@code INVALID_CREDENTIALS} tanto
 *       para inexistente quanto para senha errada, sem detalhe;</li>
 *   <li>a recuperacao de senha devolve sempre a mesma mensagem
 *       ({@link BlindDocumentLogin#UNIFORM_RECOVERY_MESSAGE}), exista a
 *       conta ou nao.</li>
 * </ul>
 */
public final class PortalAuthenticationFlow {

    /** Conta ficticia fixa usada para equalizar o custo quando o identificador nao existe. */
    public static final UUID DUMMY_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public enum Outcome { SUCCESS, INVALID_CREDENTIALS }

    public record Result(Outcome outcome, UUID accountId) { }

    /** Verificacao de credencial (Keycloak no adaptador). */
    public interface CredentialVerifier {
        boolean verify(UUID accountId, char[] password);
    }

    /**
     * Enfileiramento da recuperacao (adaptador de mensageria/outbox). E
     * chamado SEMPRE, exista a conta ou nao — para conta inexistente recebe
     * {@link #DUMMY_ACCOUNT_ID}, que o adaptador descarta sem enviar nada —
     * de modo que o trabalho dentro da resposta seja o mesmo nos dois
     * casos e o tempo nao revele existencia (revisao cruzada da P3.3).
     */
    public interface RecoverySender {
        void send(UUID accountId);
    }

    private final BlindDocumentLogin login;
    private final CredentialVerifier verifier;

    public PortalAuthenticationFlow(BlindDocumentLogin login, CredentialVerifier verifier) {
        if (login == null || verifier == null) {
            throw new IllegalArgumentException("login e verifier sao obrigatorios");
        }
        this.login = login;
        this.verifier = verifier;
    }

    public Result authenticate(UUID tenantId, String identifier, char[] password) {
        Optional<UUID> account = login.resolveAccount(tenantId, identifier);
        UUID effective = account.orElse(DUMMY_ACCOUNT_ID);
        boolean ok = verifier.verify(effective, password);
        if (account.isPresent() && ok) {
            return new Result(Outcome.SUCCESS, account.get());
        }
        return new Result(Outcome.INVALID_CREDENTIALS, null);
    }

    /** Recuperacao: mesma mensagem e mesmo trabalho de enfileiramento, exista a conta ou nao. */
    public String recover(UUID tenantId, String identifier, RecoverySender sender) {
        if (sender == null) {
            throw new IllegalArgumentException("sender e obrigatorio");
        }
        UUID effective = login.resolveAccount(tenantId, identifier).orElse(DUMMY_ACCOUNT_ID);
        sender.send(effective);
        return BlindDocumentLogin.UNIFORM_RECOVERY_MESSAGE;
    }
}
