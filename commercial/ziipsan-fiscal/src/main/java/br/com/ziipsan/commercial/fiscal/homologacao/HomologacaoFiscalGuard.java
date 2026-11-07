package br.com.ziipsan.commercial.fiscal.homologacao;

import br.com.ziipsan.commercial.fiscal.nfage.NfageProductionGuard;

import java.util.Objects;

/**
 * Trava de entrada de QUALQUER operação da homologação fiscal local (P3.7;
 * ADR-0003; ADR-0033; §2.4). A homologação roda SOMENTE:
 *
 * <ol>
 *   <li>em ambiente de homologação da SEFAZ com {@code tpAmb=2} — a coerência
 *       ambiente×tpAmb é a da trava do dicionário ({@link NfageProductionGuard},
 *       reutilizada, nunca duplicada); produção ({@code tpAmb=1}) neste pacote é
 *       recusada sempre, mesmo com habilitação;</li>
 *   <li>com o e-CNPJ da PRÓPRIA Ziip/TGP como certificado de assinatura — a exceção
 *       do ADR-0033. Nenhum certificado de cliente entra no ambiente local (P1.8):
 *       titular divergente do esperado ou marcado como de cliente é recusado.</li>
 * </ol>
 *
 * <p>O CNPJ esperado e a impressão digital (thumbprint) esperada do e-CNPJ da
 * Ziip/TGP são CONFIGURAÇÃO de quem opera (lidos do cofre local no ato da
 * homologação real), nunca constantes desta classe: nenhum dado do certificado
 * real é presumido ou gravado no código.
 *
 * <p>Esta trava NÃO executa a homologação: ela só decide se uma operação de
 * homologação pode começar. A homologação real na SEFAZ é aceite externo da
 * P3.7 (ver RELATORIO-P3.7.md).
 */
public final class HomologacaoFiscalGuard {

    /** Titularidade do certificado apresentado para assinar na homologação. */
    public enum TitularCertificado {
        /** e-CNPJ da própria Ziip/TGP (exceção do ADR-0033). */
        ZIIP_TGP,
        /** Certificado de cliente/tenant — proibido no ambiente local. */
        CLIENTE
    }

    /**
     * @param contexto              contexto de ambiente da emissão (trava do dicionário)
     * @param titular               titularidade do certificado de assinatura
     * @param cnpjCertificado       CNPJ do certificado apresentado
     * @param cnpjZiipTgpEsperado   CNPJ esperado do e-CNPJ da Ziip/TGP (configuração do cofre)
     * @param thumbprintCertificado impressão digital do certificado apresentado
     * @param thumbprintEsperado    impressão digital esperada (configuração do cofre)
     */
    public record ContextoHomologacao(NfageProductionGuard.ContextoAmbiente contexto,
                                      TitularCertificado titular, String cnpjCertificado,
                                      String cnpjZiipTgpEsperado, String thumbprintCertificado,
                                      String thumbprintEsperado) {
    }

    private HomologacaoFiscalGuard() {
    }

    /**
     * @throws IllegalStateException se qualquer condição da homologação local falhar
     *                               (a trava falha fechada, antes de qualquer assinatura)
     */
    public static void exigirHomologacao(ContextoHomologacao ctx) {
        Objects.requireNonNull(ctx, "Contexto da homologação é obrigatório");
        Objects.requireNonNull(ctx.contexto(), "Contexto de ambiente é obrigatório");

        // 1) Só homologação tpAmb=2: a coerência é a do dicionário (P2.6) e, além
        // dela, este pacote recusa PRODUCTION em qualquer hipótese.
        NfageProductionGuard.exigirPermitido(ctx.contexto());
        if (ctx.contexto().ambiente() != NfageProductionGuard.Ambiente.HOMOLOGATION) {
            throw new IllegalStateException(
                    "Homologação fiscal local (P3.7) só roda em HOMOLOGATION com tpAmb=2; ambiente recebido: "
                            + ctx.contexto().ambiente());
        }

        // 2) Só o e-CNPJ da própria Ziip/TGP (ADR-0033); certificado de cliente, nunca.
        if (ctx.titular() != TitularCertificado.ZIIP_TGP) {
            throw new IllegalStateException(
                    "Ambiente local não aceita certificado de cliente (P1.8/ADR-0033): só o e-CNPJ da própria Ziip/TGP");
        }
        if (ctx.cnpjZiipTgpEsperado() == null || ctx.cnpjZiipTgpEsperado().isBlank()
                || ctx.thumbprintEsperado() == null || ctx.thumbprintEsperado().isBlank()) {
            throw new IllegalStateException(
                    "CNPJ e thumbprint esperados do e-CNPJ da Ziip/TGP são configuração obrigatória do cofre local");
        }
        if (!ctx.cnpjZiipTgpEsperado().equals(ctx.cnpjCertificado())) {
            throw new IllegalStateException(
                    "Certificado apresentado não é o e-CNPJ esperado da Ziip/TGP (CNPJ divergente)");
        }
        if (!ctx.thumbprintEsperado().equalsIgnoreCase(
                ctx.thumbprintCertificado() == null ? "" : ctx.thumbprintCertificado())) {
            throw new IllegalStateException(
                    "Certificado apresentado não é o e-CNPJ esperado da Ziip/TGP (thumbprint divergente)");
        }
    }
}
