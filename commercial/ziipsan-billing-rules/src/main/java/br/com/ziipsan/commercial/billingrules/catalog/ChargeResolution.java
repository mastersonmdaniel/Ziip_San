package br.com.ziipsan.commercial.billingrules.catalog;

import java.util.Objects;

/**
 * Resultado da resolucao do catalogo: a definicao da cobranca junto da
 * versao vigente na data de referencia (proposta, secoes 2.1 e 2.2).
 * E o retrato que a camada seguinte (billing maestro) consome para
 * saber o que cobrar e com quais parametros — sem reler o catalogo.
 */
public record ChargeResolution(ChargeDefinition definition, ChargeVersion version) {

    public ChargeResolution {
        Objects.requireNonNull(definition, "definicao da resolucao e obrigatoria");
        Objects.requireNonNull(version, "versao da resolucao e obrigatoria");
        if (!version.chargeDefinitionId().equals(definition.id())) {
            throw new IllegalArgumentException("versao nao pertence a definicao da resolucao");
        }
    }
}
