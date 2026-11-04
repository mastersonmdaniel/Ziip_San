package br.com.ziipsan.platform.domain;

import java.util.List;
import java.util.Optional;

/**
 * Resolução de DNS para a verificação de posse da P2.27, atrás de
 * interfaces pequenas para que os testes usem falsos (sem DNS real) e
 * a automação da AWS (LC.2) possa substituir o emissor sem tocar no
 * serviço.
 *
 * <p>Implementação real por JNDI ({@code com.sun.jndi.dns.DnsContextFactory}):
 * {@link JndiDns}. Nenhuma delas é usada no caminho pré-login — só nas
 * ações do admin (verificar, renovar, revogar).
 */
public final class DnsResolvers {

    private DnsResolvers() {
    }

    /** Registros TXT de um nome, sem aspas de envoltório do DNS. */
    public interface TxtResolver {
        List<String> txtRecords(String name);
    }

    /** Alvo do CNAME de um hostname, ou vazio quando não há CNAME. */
    public interface CnameResolver {
        Optional<String> cnameTarget(String hostname);
    }
}
