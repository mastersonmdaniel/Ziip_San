package br.com.ziipsan.reporting.sinisa;

/**
 * Formulario SINISA de uma variavel do catalogo da ED-48. Espelha, por nome,
 * o {@code SinisaForm} da P4.2 (modulo integration): o modulo reporting NAO
 * depende do modulo integration (o pom do reporting so depende do core), por
 * isso a ligacao entre os dois lados e pela CHAVE da variavel e por este
 * vocabulario de tres valores, identico nos dois modulos. Se o vocabulario
 * oficial mudar, os dois enums mudam juntos na integracao.
 */
public enum SinisaPanelForm {
    FINANCEIRO,
    TECNICO,
    BALANCO
}
