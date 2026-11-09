package br.com.ziipsan.core.certification.external;

/**
 * Recursos legiveis por um observador externo (ED-75). A matriz de
 * escopo por perfil vive em {@link ExternalScope}.
 */
public enum ExternalResource {
    /** Pacote de evidencia das normas ligadas (ED-75). */
    EVIDENCE_PACK,
    /** Dados regulatorios (conteudo concreto definido pela ED-97). */
    REGULATORY_DATA,
    /** Relatorios regulatorios (conteudo concreto definido pela ED-97). */
    REGULATORY_REPORT,
    /** Dado pessoal ALEM DO MINIMO estritamente necessario: ninguem le. */
    PERSONAL_DATA,
    /** O minimo de dado pessoal que o escopo regulatorio admite (ED-97 delimita). */
    PERSONAL_DATA_MINIMUM
}
