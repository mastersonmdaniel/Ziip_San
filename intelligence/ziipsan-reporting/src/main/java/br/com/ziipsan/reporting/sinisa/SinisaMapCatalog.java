package br.com.ziipsan.reporting.sinisa;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.util.Set;

/**
 * Fronteira de pertencimento ao mapa SINISA da P4.2 (correcao D1 da
 * revisao cruzada da ED-48): as chaves CONHECIDAS do mapa sao as da
 * semente da P4.2 ({@code sinisa-catalog-seed.json}) — o unico mapa que
 * existe para este pacote, ja que o modulo reporting nao depende do
 * modulo integration onde vive o {@code SinisaCatalog} carregavel.
 *
 * <p>Toda chave SINISA que entra no pacote (ligacao, variavel de entrada
 * e a propria entrada do catalogo) e conferida contra este conjunto na
 * CONSTRUCAO: chave fora do mapa e recusada com
 * {@code SINISA_VARIABLE_NOT_IN_MAP} — chave inventada nao passa mais
 * como chave valida so por nao estar em branco.
 *
 * <p>Limite declarado com precisao: quando o catalogo SINISA completo do
 * ciclo vigente for carregado na integracao (modulo de esgoto e codigos
 * do Glossario, via {@code SinisaCatalog} da P4.2), o pertencimento das
 * NOVAS chaves e conferido contra ESSE catalogo carregado no ponto de
 * fiacao/persistencia — essas chaves ainda nao existem em nenhum mapa
 * disponivel a este pacote, e inclui-las aqui seria inventa-las. Ate la,
 * este conjunto e o mapa inteiro conhecido, e o teste do catalogo prova
 * que ele e exatamente o conjunto das entradas pre-configuradas.
 */
public final class SinisaMapCatalog {

    private static final Set<String> KNOWN_KEYS = Set.of(
            "AGUA.VOLUME_PRODUZIDO",
            "AGUA.VOLUME_FATURADO",
            "AGUA.AGUA_NAO_FATURADA",
            "AGUA.LIGACOES_ATIVAS",
            "FIN.RECEITA_OPERACIONAL_AGUA",
            "BALANCO.ATIVO_TOTAL");

    private SinisaMapCatalog() {
    }

    public static Set<String> knownKeys() {
        return KNOWN_KEYS;
    }

    public static boolean isKnown(String sinisaVariableKey) {
        return sinisaVariableKey != null && KNOWN_KEYS.contains(sinisaVariableKey);
    }

    /** Recusa nomeada para chave fora do mapa conhecido da P4.2. */
    public static void requireKnown(String sinisaVariableKey) {
        if (!isKnown(sinisaVariableKey)) {
            throw new IndicatorValidationException("SINISA_VARIABLE_NOT_IN_MAP",
                    "chave SINISA fora do mapa da P4.2 (semente vigente): " + sinisaVariableKey
                            + " — pertencimento ao mapa e exigido; chaves de ciclos futuros "
                            + "entram pelo catalogo carregado na integracao, nao por construcao livre");
        }
    }
}
