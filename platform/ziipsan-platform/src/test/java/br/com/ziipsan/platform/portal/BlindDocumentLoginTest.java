package br.com.ziipsan.platform.portal;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P3.3 - autenticador: localiza por indice cego; nunca pelo valor em claro. */
class BlindDocumentLoginTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final String CPF = "52998224725";

    private BlindDocumentLogin loginWith(Map<String, UUID> byIndex) {
        var protector = PortalTestSupport.protector();
        return new BlindDocumentLogin(protector,
                (tenantId, context, blindIndex) -> Optional.ofNullable(byIndex.get(context + "|" + blindIndex)));
    }

    @Test
    void localizaContaPeloIndiceCegoDoDocumento() {
        var protector = PortalTestSupport.protector();
        String index = protector.indexOf(TENANT, BlindDocumentLogin.DOCUMENT_CONTEXT, CPF);
        var login = loginWith(Map.of(BlindDocumentLogin.DOCUMENT_CONTEXT + "|" + index, ACCOUNT));
        assertThat(login.resolveAccount(TENANT, "529.982.247-25")).contains(ACCOUNT);
        assertThat(login.resolveAccount(TENANT, CPF)).contains(ACCOUNT);
    }

    @Test
    void localizaContaPeloEmailComContextoProprio() {
        var protector = PortalTestSupport.protector();
        String index = protector.indexOf(TENANT, BlindDocumentLogin.EMAIL_CONTEXT, "titular@example.com");
        var login = loginWith(Map.of(BlindDocumentLogin.EMAIL_CONTEXT + "|" + index, ACCOUNT));
        assertThat(login.resolveAccount(TENANT, " Titular@Example.com ")).contains(ACCOUNT);
    }

    @Test
    void identificadorInexistenteEInvalidoDevolvemVazio() {
        var login = loginWith(new HashMap<>());
        assertThat(login.resolveAccount(TENANT, CPF)).isEmpty();
        assertThat(login.resolveAccount(TENANT, "nao-e-documento")).isEmpty();
        assertThat(BlindDocumentLogin.classify(CPF)).isEqualTo(BlindDocumentLogin.IdentifierKind.DOCUMENT);
        assertThat(BlindDocumentLogin.classify("a@b.com")).isEqualTo(BlindDocumentLogin.IdentifierKind.EMAIL);
    }

    @Test
    void fluxoDeAutenticacaoEUniformeParaInexistenteESenhaErrada() {
        var protector = PortalTestSupport.protector();
        String index = protector.indexOf(TENANT, BlindDocumentLogin.DOCUMENT_CONTEXT, CPF);
        var login = loginWith(Map.of(BlindDocumentLogin.DOCUMENT_CONTEXT + "|" + index, ACCOUNT));
        java.util.List<UUID> verified = new java.util.ArrayList<>();
        var flow = new PortalAuthenticationFlow(login, (accountId, password) -> {
            verified.add(accountId);
            return accountId.equals(ACCOUNT) && "correta".equals(new String(password));
        });
        var wrongPassword = flow.authenticate(TENANT, CPF, "errada".toCharArray());
        var unknown = flow.authenticate(TENANT, "39053344705", "correta".toCharArray());
        assertThat(wrongPassword.outcome()).isEqualTo(PortalAuthenticationFlow.Outcome.INVALID_CREDENTIALS);
        assertThat(unknown.outcome()).isEqualTo(PortalAuthenticationFlow.Outcome.INVALID_CREDENTIALS);
        // A verificacao rodou nos dois casos; no inexistente, contra a conta ficticia (tempo igual).
        assertThat(verified).containsExactly(ACCOUNT, PortalAuthenticationFlow.DUMMY_ACCOUNT_ID);
        assertThat(flow.authenticate(TENANT, CPF, "correta".toCharArray()).outcome())
                .isEqualTo(PortalAuthenticationFlow.Outcome.SUCCESS);
    }

    @Test
    void recuperacaoDeSenhaRespondeSempreOMesmoEEnfileiraNosDoisCasos() {
        var protector = PortalTestSupport.protector();
        String index = protector.indexOf(TENANT, BlindDocumentLogin.DOCUMENT_CONTEXT, CPF);
        var login = loginWith(Map.of(BlindDocumentLogin.DOCUMENT_CONTEXT + "|" + index, ACCOUNT));
        var flow = new PortalAuthenticationFlow(login, (id, pw) -> false);
        java.util.List<UUID> enqueued = new java.util.ArrayList<>();
        String forKnown = flow.recover(TENANT, CPF, enqueued::add);
        String forUnknown = flow.recover(TENANT, "39053344705", enqueued::add);
        assertThat(forKnown).isEqualTo(BlindDocumentLogin.UNIFORM_RECOVERY_MESSAGE);
        assertThat(forUnknown).isEqualTo(BlindDocumentLogin.UNIFORM_RECOVERY_MESSAGE);
        // O enfileiramento roda nos dois casos; para inexistente, com a conta
        // ficticia (o adaptador descarta sem enviar): trabalho igual na resposta.
        assertThat(enqueued).containsExactly(ACCOUNT, PortalAuthenticationFlow.DUMMY_ACCOUNT_ID);
    }
}
