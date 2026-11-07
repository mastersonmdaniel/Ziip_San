package br.com.ziipsan.commercial.fiscal.homologacao;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Conferência do registro de acessos ao certificado no cofre local (aceite da
 * P3.7; §2.4; P1.8: o e-CNPJ da Ziip/TGP é lido do cofre local, fora do banco, e
 * CADA acesso é registrado). A conferência cruza o registro do cofre com as
 * assinaturas efetivamente feitas no ciclo:
 *
 * <ol>
 *   <li>toda assinatura do ciclo tem um acesso de finalidade ASSINATURA
 *       registrado para o mesmo ciclo e o mesmo certificado — assinatura sem
 *       acesso registrado é problema;</li>
 *   <li>todo acesso é ao certificado esperado da Ziip/TGP — acesso a
 *       certificado de cliente no cofre local é problema (não pode haver
 *       certificado de cliente no ambiente local);</li>
 *   <li>nenhum acesso tem finalidade fora do conjunto fechado admitido.</li>
 * </ol>
 *
 * <p>A leitura física do cofre local (P0.28/P0.45) é de quem chama; aqui a regra
 * de conferência é testada sobre os registros lidos.
 *
 * <p><b>Limite declarado (D6 da revisão):</b> a conferência é AGREGADA por ciclo
 * (contagem de acessos de assinatura × contagem de assinaturas do ciclo), como
 * pede a letra do aceite ("assinatura sem acesso registrado reprova"). Amarrar
 * cada acesso à sua assinatura individual exigiria a chave do documento também
 * no registro de acesso do cofre, campo que esse registro não tem hoje; o antigo
 * campo {@code accessKey} de {@code AssinaturaDoCiclo} nunca era usado na
 * conferência e foi removido (limpeza D6) em vez de fingir um vínculo que o
 * código não fazia.
 */
public final class ConferenciaAcessosCofre {

    /** Finalidades fechadas de acesso ao certificado no ambiente local. */
    public enum FinalidadeAcesso { ASSINATURA_HOMOLOGACAO, CONFERENCIA_CERTIFICADO }

    /**
     * @param quando     instante do acesso, registrado pelo cofre
     * @param finalidade finalidade declarada do acesso
     * @param ciclo      ciclo de homologação do acesso (nulo só em conferência avulsa)
     * @param thumbprintCertificado thumbprint do certificado acessado
     */
    public record AcessoCertificado(Instant quando, FinalidadeAcesso finalidade, String ciclo,
                                    String thumbprintCertificado) {
    }

    /** Uma assinatura feita no ciclo (uma ocorrência por documento assinado). */
    public record AssinaturaDoCiclo(String ciclo) {
    }

    /** Resultado da conferência: limpo, ou a lista exata dos problemas. */
    public record ResultadoConferencia(List<String> problemas) {
        public boolean conferido() {
            return problemas.isEmpty();
        }
    }

    private final String thumbprintZiipTgpEsperado;

    public ConferenciaAcessosCofre(String thumbprintZiipTgpEsperado) {
        if (thumbprintZiipTgpEsperado == null || thumbprintZiipTgpEsperado.isBlank()) {
            throw new IllegalArgumentException(
                    "Thumbprint esperado do e-CNPJ da Ziip/TGP é configuração obrigatória da conferência");
        }
        this.thumbprintZiipTgpEsperado = thumbprintZiipTgpEsperado;
    }

    public ResultadoConferencia conferir(List<AcessoCertificado> acessos,
                                         List<AssinaturaDoCiclo> assinaturas) {
        Objects.requireNonNull(acessos, "Registro de acessos do cofre é obrigatório");
        Objects.requireNonNull(assinaturas, "Assinaturas do ciclo são obrigatórias");
        List<String> problemas = new ArrayList<>();

        for (AcessoCertificado acesso : acessos) {
            if (acesso.finalidade() == null) {
                problemas.add("Acesso sem finalidade registrada em " + acesso.quando());
            }
            if (acesso.thumbprintCertificado() == null
                    || !acesso.thumbprintCertificado().equalsIgnoreCase(thumbprintZiipTgpEsperado)) {
                problemas.add("Acesso a certificado diverso do e-CNPJ da Ziip/TGP em " + acesso.quando()
                        + " (certificado de cliente não pode existir no cofre local)");
            }
        }

        // Cada assinatura precisa de UM acesso de assinatura no mesmo ciclo —
        // contagem comparada, para assinatura repetida sem registro não passar.
        for (AssinaturaDoCiclo assinatura : assinaturas) {
            long acessosDaAssinatura = acessos.stream()
                    .filter(a -> a.finalidade() == FinalidadeAcesso.ASSINATURA_HOMOLOGACAO)
                    .filter(a -> Objects.equals(a.ciclo(), assinatura.ciclo()))
                    .count();
            long assinaturasDoCiclo = assinaturas.stream()
                    .filter(s -> Objects.equals(s.ciclo(), assinatura.ciclo()))
                    .count();
            if (acessosDaAssinatura < assinaturasDoCiclo) {
                problemas.add("Ciclo " + assinatura.ciclo() + ": " + assinaturasDoCiclo
                        + " assinatura(s) mas só " + acessosDaAssinatura
                        + " acesso(s) de assinatura registrado(s) no cofre");
                break; // um problema por ciclo basta para reprovar a conferência
            }
        }
        return new ResultadoConferencia(List.copyOf(problemas));
    }

    /** Forma usada no fechamento do ciclo: problema na conferência é falha explícita. */
    public void exigirConferido(List<AcessoCertificado> acessos, List<AssinaturaDoCiclo> assinaturas) {
        ResultadoConferencia resultado = conferir(acessos, assinaturas);
        if (!resultado.conferido()) {
            throw new IllegalStateException(
                    "Registro de acessos ao certificado no cofre NÃO conferiu: " + resultado.problemas());
        }
    }
}
