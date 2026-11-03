package br.com.ziipsan.commercial.fiscal.support;

import br.com.ziipsan.commercial.fiscal.port.AuthorizedXmlStore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Guarda de XML autorizado em memória, para os testes do serviço. */
public final class InMemoryXmlStore implements AuthorizedXmlStore {

    public record Stored(byte[] gzip, String sha256, String source) {
    }

    private final Map<UUID, Stored> stored = new HashMap<>();

    @Override
    public synchronized void store(UUID tenantId, UUID documentId, byte[] xmlGzip, String sha256, String source) {
        stored.put(documentId, new Stored(xmlGzip, sha256, source));
    }

    public synchronized Optional<Stored> stored(UUID documentId) {
        return Optional.ofNullable(stored.get(documentId));
    }
}
