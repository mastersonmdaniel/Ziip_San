package br.com.ziipsan.commercial.collection.cession;

import br.com.ziipsan.commercial.collection.cession.CessionRegistry.Registration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ED-103/E4 (decisao 62) — loja em memoria da mecanica de cessao, com
 * atomicidade REAL do desenho: um unico cadeado serializa
 * verificar-e-criar e comparar-e-trocar, logo a prova de concorrencia
 * (N threads, mesmo recebivel, exactamente 1 sucesso) exercita a mesma
 * garantia que o indice unico parcial do SQL impoe em persistencia.
 * Produtor (execucao ED-103, decisao 62): sessao
 * 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 */
public final class InMemoryCessionStore implements CessionStore {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, Registration> byId = new LinkedHashMap<>();
    private final List<ExportAudit> audits = new ArrayList<>();

    @Override
    public boolean tryCreateActive(Registration registration) {
        lock.lock();
        try {
            for (Registration r : byId.values())
                if (r.tenantId().equals(registration.tenantId())
                        && r.receivableId().equals(registration.receivableId()) && r.active())
                    return false;
            byId.put(registration.id(), registration);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Registration> activeRegistration(UUID tenantId, String receivableId) {
        lock.lock();
        try {
            return byId.values().stream()
                    .filter(r -> r.tenantId().equals(tenantId) && r.receivableId().equals(receivableId) && r.active())
                    .findFirst();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean replace(Registration current, Registration next) {
        lock.lock();
        try {
            Registration stored = byId.get(current.id());
            if (stored == null || stored.status() != current.status()
                    || stored.events().size() != current.events().size())
                return false;
            byId.put(next.id(), next);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Registration> registrations(UUID tenantId) {
        lock.lock();
        try {
            return byId.values().stream().filter(r -> r.tenantId().equals(tenantId)).toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void appendExportAudit(ExportAudit audit) {
        lock.lock();
        try {
            audits.add(audit);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<ExportAudit> exportAudits(UUID tenantId) {
        lock.lock();
        try {
            return audits.stream().filter(a -> a.tenantId().equals(tenantId)).toList();
        } finally {
            lock.unlock();
        }
    }
}
