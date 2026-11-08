package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

/**
 * ED-49: o recorte de benchmark do tenant (porte, regiao e natureza
 * juridica — as tres dimensoes do GES-29 da ED-48). Os rotulos sao os
 * DECLARADOS no cadastro do tenant e tem de casar com os rotulos da base
 * publica importada da ED-48: nenhum vocabulario de porte/regiao e
 * presumido aqui (as faixas sao as da publicacao do SINISA). Recorte
 * incompleto e recusado nomeado — comparar com recorte parcial seria
 * comparar com a mediana errada.
 */
public record BenchmarkSlice(String sizeClass, String region, String legalNature) {

    public BenchmarkSlice {
        if (sizeClass == null || sizeClass.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_SLICE_SIZE_CLASS_REQUIRED",
                    "recorte de benchmark sem o porte declarado do tenant (GES-29) [A DEFINIR]");
        }
        if (region == null || region.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_SLICE_REGION_REQUIRED",
                    "recorte de benchmark sem a regiao declarada do tenant (GES-29) [A DEFINIR]");
        }
        if (legalNature == null || legalNature.isBlank()) {
            throw new IndicatorValidationException("BENCHMARK_SLICE_LEGAL_NATURE_REQUIRED",
                    "recorte de benchmark sem a natureza juridica declarada do tenant (GES-29) "
                            + "[A DEFINIR]");
        }
    }
}
