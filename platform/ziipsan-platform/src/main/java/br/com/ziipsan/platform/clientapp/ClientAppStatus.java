package br.com.ziipsan.platform.clientapp;

/**
 * Estado de publicacao do app do cliente (ED-39), espelho do CHECK de
 * {@code platform.client_app_config.status} na core {@code V11_1}:
 * DRAFT (gerado pelo pipeline, nao submetido), IN_REVIEW (submetido,
 * aguardando a loja), PUBLISHED, SUSPENDED e UNPUBLISHED.
 */
public enum ClientAppStatus {
    DRAFT,
    IN_REVIEW,
    PUBLISHED,
    SUSPENDED,
    UNPUBLISHED
}
