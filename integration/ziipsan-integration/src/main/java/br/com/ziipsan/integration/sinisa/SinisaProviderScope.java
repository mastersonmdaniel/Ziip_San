package br.com.ziipsan.integration.sinisa;

/**
 * Escopo do prestador no SINISA, valor do parametro {@code sinisa.provider_scope}
 * (aceite da P4.2).
 *
 * <p>Definicoes da pagina oficial "Area do Prestador" do SINISA (Ministerio das
 * Cidades): os modulos de Abastecimento de Agua e de Esgotamento Sanitario tem
 * ambiente separado para prestadores regionais e locais; prestador regional atende
 * a mais de um municipio; prestador local atende somente a um municipio. A regra de
 * contagem de municipios e verificada na abertura do ciclo ({@link SinisaCycle}).
 */
public enum SinisaProviderScope {

    LOCAL(1, 1),
    REGIONAL(2, Integer.MAX_VALUE);

    private final int minMunicipios;
    private final int maxMunicipios;

    SinisaProviderScope(int minMunicipios, int maxMunicipios) {
        this.minMunicipios = minMunicipios;
        this.maxMunicipios = maxMunicipios;
    }

    /** Verdadeiro se a quantidade de municipios atendidos e compativel com o escopo. */
    public boolean aceitaMunicipios(int municipiosAtendidos) {
        return municipiosAtendidos >= minMunicipios && municipiosAtendidos <= maxMunicipios;
    }
}
