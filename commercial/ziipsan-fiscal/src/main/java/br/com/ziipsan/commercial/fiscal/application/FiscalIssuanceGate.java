package br.com.ziipsan.commercial.fiscal.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Trava de produção da P0.32 (FISCAL-PORTE-JAVA §3.6): sem
 * {@code ziipsan.fiscal.production-allowed=true} <b>não há emissão com tpAmb=1</b>.
 *
 * <p>A propriedade é falsa por omissão em todo perfil (dev, CI, homologação local) até o
 * lançamento comercial (ADR-0033; LC.13). A emissão em produção exige ainda, além dela, a
 * <i>flag</i> do tenant aprovada por dois papéis distintos e certificado ICP-Brasil válido —
 * os dois últimos ainda não existem (o cofre de certificado está bloqueado até a P0.28 e a
 * aprovação dupla chega com a P1.8), por isso chegam aqui como parâmetro explícito do
 * chamador, nunca como valor presumido: sem aprovação explícita, a produção é recusada
 * mesmo com a propriedade verdadeira. No ambiente local o egresso para os hosts de produção
 * da Sefin/SEFAZ fica bloqueado na rede, como segunda barreira.
 */
@ConfigurationProperties(prefix = "ziipsan.fiscal")
public record FiscalIssuanceGate(boolean productionAllowed) {

    /** Ambiente do documento, com o tpAmb correspondente da NFS-e Nacional. */
    public enum Environment {
        /** Simulador do C#: padrão de todo ambiente que não seja produção. */
        SIMULATED(null),
        /** Produção restrita da NFS-e / homologação da SEFAZ. */
        HOMOLOGATION(2),
        /** Produção. Só com as três condições da §3.6. */
        PRODUCTION(1);

        private final Integer tpAmb;

        Environment(Integer tpAmb) {
            this.tpAmb = tpAmb;
        }

        public Integer tpAmb() {
            return tpAmb;
        }
    }

    public FiscalIssuanceGate {
        // productionAllowed falso por omissão: o record sem argumento não existe, mas quem
        // instancia sem ler a propriedade deve passar false explicitamente (ver teste).
    }

    /** Instância de fábrica com a trava desligada (o padrão de dev, CI e homologação). */
    public static FiscalIssuanceGate closed() {
        return new FiscalIssuanceGate(false);
    }

    /**
     * Resolve o ambiente pedido. Produção sem propriedade verdadeira ou sem aprovação
     * explícita do tenant é recusada com exceção — nunca degradada em silêncio para
     * homologação, porque degradar esconderia a emissão que o chamador pediu.
     */
    public Environment resolve(Environment requested, boolean tenantApproved) {
        if (requested == null) {
            throw new IllegalArgumentException("O ambiente pedido e obrigatorio");
        }
        if (requested == Environment.PRODUCTION) {
            if (!productionAllowed) {
                throw new IllegalStateException(
                        "Emissao em producao (tpAmb=1) recusada: ziipsan.fiscal.production-allowed nao e verdadeira");
            }
            if (!tenantApproved) {
                throw new IllegalStateException(
                        "Emissao em producao (tpAmb=1) recusada: falta a aprovacao do tenant (dois papeis distintos)");
            }
        }
        return requested;
    }
}
