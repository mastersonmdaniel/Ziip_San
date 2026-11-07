package br.com.ziipsan.commercial.fiscal.arquivo;

/**
 * Erro de integridade do XML (P2.18): o SHA-256 calculado sobre os bytes não
 * confere com o hash esperado (o {@code xml_sha256} do dicionário fiscal ou
 * o hash gravado no registro de retenção), ou o elo devolvido pela
 * {@code HashChain} diverge do elo gravado no registro (coerência
 * registro × elo — a porta da cadeia não recebe o hash, logo não há
 * conferência elo × conteúdo nesta camada).
 *
 * <p>Divergência é sempre esta exceção explícita: o serviço <b>nunca</b>
 * devolve nem arquiva XML suspeito em silêncio.
 */
public class IntegridadeXmlException extends RuntimeException {

    public IntegridadeXmlException(String mensagem) {
        super(mensagem);
    }
}
