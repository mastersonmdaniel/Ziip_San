package br.com.ziipsan.integration.sinisa;

import java.util.Objects;

/**
 * Uma variavel do mapa SINISA (P4.2, GES-16): a variavel de um formulario oficial e a
 * origem de cada dado no Ziip SAN.
 *
 * <p><b>Codigos oficiais nao sao inventados.</b> No SINISA, cada dado coletado tem um
 * codigo unico de identificacao definido no Glossario de Informacoes oficial
 * (Ministerio das Cidades/SNSA). Por isso o mapa e orientado a dados: {@code key} e a
 * chave interna estavel do Ziip SAN e {@code officialCode} e o codigo do Glossario,
 * preenchido na carga do catalogo a partir do glossario vigente do ciclo (a montagem
 * do catalogo completo e do painel e da ED-48). Variavel sem origem declarada e
 * recusada: e a mesma regra do registro unico de indicadores da P3.41 ("indicador
 * com variavel sem fonte recusado").
 *
 * @param key              chave interna estavel do mapa (ex.: {@code AGUA.VOLUME_FATURADO})
 * @param officialCode     codigo unico do Glossario de Informacoes do SINISA; pode ser
 *                         {@code null} enquanto o glossario do ciclo nao for carregado,
 *                         nunca um codigo presumido
 * @param form             formulario a que a variavel pertence
 * @param name             nome da variavel como consta do formulario/glossario
 * @param unit             unidade de medida declarada (ex.: {@code m3}, {@code R$}, {@code ligacoes})
 * @param sourceKind       natureza da origem no Ziip SAN
 * @param sourceExpression expressao da origem: modulo, tabela/campo ou calculo de onde
 *                         o valor sai (texto auditavel, lido por quem confere o ciclo)
 */
public record SinisaVariable(
        String key,
        String officialCode,
        SinisaForm form,
        String name,
        String unit,
        SourceKind sourceKind,
        String sourceExpression) {

    /** Natureza da origem de um dado do mapa. */
    public enum SourceKind {
        /** Agregado do modulo de medicao (operations/ziipsan-metering, P2.5). */
        METERING,
        /** Agregado do modulo de faturamento (commercial/ziipsan-billing, P2.5). */
        BILLING,
        /** Agregado do cadastro (commercial/ziipsan-registry). */
        REGISTRY,
        /** Derivado por calculo a partir de outras variaveis do mapa (ex.: balanco hidrico). */
        DERIVED,
        /** Sem fonte automatica no Ziip SAN: so entra como valor manual justificado. */
        MANUAL
    }

    public SinisaVariable {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Variavel SINISA sem chave interna");
        }
        Objects.requireNonNull(form, "Variavel SINISA " + key + " sem formulario");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Variavel SINISA " + key + " sem nome");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Variavel SINISA " + key + " sem unidade");
        }
        Objects.requireNonNull(sourceKind, "Variavel SINISA " + key + " sem natureza da origem");
        if (sourceExpression == null || sourceExpression.isBlank()) {
            throw new IllegalArgumentException(
                    "Variavel SINISA " + key + " sem origem declarada: variavel sem fonte e recusada");
        }
        if (officialCode != null && officialCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Variavel SINISA " + key + ": codigo oficial em branco; deixe nulo ate carregar o Glossario");
        }
    }

    /** Verdadeiro quando a origem e automatica (o pre-preenchimento consulta a porta de dados). */
    public boolean automatic() {
        return sourceKind != SourceKind.MANUAL;
    }
}
