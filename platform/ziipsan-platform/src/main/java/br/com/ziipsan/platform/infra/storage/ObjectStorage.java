package br.com.ziipsan.platform.infra.storage;

import java.io.InputStream;

/**
 * P0.45: armazenamento de objetos. Interface pura de dominio de infra: nenhum tipo de
 * SDK de provedor (AWS, GCP, Azure) aparece aqui — a implementacao local e
 * {@link LocalFileObjectStorage} e a compativel com S3 e {@link S3ObjectStorage}
 * (HTTP + SigV4 com o HttpClient do JDK, sem SDK). A implementacao AWS gerenciada
 * (S3/KMS) e da LC.1 e deve passar na mesma suite de contrato.
 *
 * <p>Bucket e chave sao validados pelas implementacoes (sem {@code ..}, sem barra
 * inicial, sem caracteres de controle). O chamador fecha o {@link InputStream} de
 * {@link #get}; o de {@link #put} e consumido pela implementacao.
 */
public interface ObjectStorage {

    void put(String bucket, String key, InputStream content, String contentType);

    /** Conteudo do objeto, ou vazio quando inexistente. O chamador fecha o stream. */
    java.util.Optional<InputStream> get(String bucket, String key);

    /** true quando o objeto existe; false (nunca excecao) quando nao existe. */
    boolean exists(String bucket, String key);

    /** Idempotente: apagar um objeto inexistente nao falha. */
    void delete(String bucket, String key);
}
