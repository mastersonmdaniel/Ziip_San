package br.com.ziipsan.platform.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * P0.45: {@link ObjectStorage} sobre HTTP compativel com S3 (emulador local SeaweedFS
 * ou, na LC.1, S3 gerenciado), sem SDK: HttpClient do JDK + {@link S3SigV4Signer}.
 * Endereco por caminho ({@code <endpoint>/<bucket>/<chave>}). Erros 404 viram
 * vazio/false; qualquer outro status vira excecao — nunca um falso sucesso.
 * Credenciais vem do ambiente (compose/.env), nunca do repositorio.
 */
public final class S3ObjectStorage implements ObjectStorage {

    private final String endpoint;
    private final S3SigV4Signer signer;
    private final HttpClient http;

    public S3ObjectStorage(String endpoint, String accessKey, String secretKey, String region) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint S3 obrigatorio");
        }
        this.endpoint = endpoint.replaceAll("/+$", "");
        this.signer = new S3SigV4Signer(accessKey, secretKey, region);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    S3ObjectStorage(String endpoint, S3SigV4Signer signer, HttpClient http) {
        this.endpoint = endpoint;
        this.signer = signer;
        this.http = http;
    }

    @Override
    public void put(String bucket, String key, InputStream content, String contentType) {
        byte[] body = readAll(content);
        HttpResponse<Void> res = send("PUT", bucket, key, body, contentType);
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("PUT S3 falhou com status " + res.statusCode());
        }
    }

    @Override
    public Optional<InputStream> get(String bucket, String key) {
        HttpResponse<InputStream> res;
        try {
            res = sendForStream("GET", bucket, key, new byte[0]);
        } catch (UncheckedIOException e) {
            throw e;
        }
        if (res.statusCode() == 404) {
            return Optional.empty();
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("GET S3 falhou com status " + res.statusCode());
        }
        return Optional.of(res.body());
    }

    @Override
    public boolean exists(String bucket, String key) {
        HttpResponse<Void> res = send("HEAD", bucket, key, new byte[0], null);
        if (res.statusCode() == 404) {
            return false;
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("HEAD S3 falhou com status " + res.statusCode());
        }
        return true;
    }

    @Override
    public void delete(String bucket, String key) {
        HttpResponse<Void> res = send("DELETE", bucket, key, new byte[0], null);
        if (res.statusCode() == 404 || (res.statusCode() >= 200 && res.statusCode() < 300)) {
            return;
        }
        throw new IllegalStateException("DELETE S3 falhou com status " + res.statusCode());
    }

    private HttpResponse<Void> send(String method, String bucket, String key, byte[] body, String contentType) {
        URI uri = uri(bucket, key);
        var headers = signer.sign(method, uri.getHost() + portSuffix(uri), "/" + bucket + "/" + key, body, Instant.now());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
        headers.forEach(builder::header);
        if (contentType != null && !contentType.isBlank()) {
            builder.header("Content-Type", contentType);
        }
        builder.method(method, body.length == 0 && !"PUT".equals(method)
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            throw new UncheckedIOException("falha de rede no S3 (" + method + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("requisicao S3 interrompida", e);
        }
    }

    private HttpResponse<InputStream> sendForStream(String method, String bucket, String key, byte[] body) {
        URI uri = uri(bucket, key);
        var headers = signer.sign(method, uri.getHost() + portSuffix(uri), "/" + bucket + "/" + key, body, Instant.now());
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
        headers.forEach(builder::header);
        builder.method(method, HttpRequest.BodyPublishers.noBody());
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("falha de rede no S3 (GET)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("requisicao S3 interrompida", e);
        }
    }

    private URI uri(String bucket, String key) {
        if (bucket == null || bucket.isBlank() || key == null || key.isBlank() || key.contains("..")) {
            throw new IllegalArgumentException("bucket/chave S3 invalidos");
        }
        return URI.create(endpoint + "/" + bucket + "/" + key);
    }

    private static String portSuffix(URI uri) {
        return uri.getPort() == -1 ? "" : ":" + uri.getPort();
    }

    private static byte[] readAll(InputStream content) {
        if (content == null) {
            throw new IllegalArgumentException("conteudo obrigatorio");
        }
        try {
            return content.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("falha ao ler conteudo para o S3", e);
        }
    }
}
