package br.com.ziipsan.core.agenda;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registro de tipos de evento de agenda (P3.39): quem pode aparecer na agenda.
 * Cada fonte registra o seu tipo (na fiacao do modulo dono); registrar o
 * MESMO tipo duas vezes e no-op; registrar um codigo ja usado com definicao
 * DIFERENTE e conflito explicito — dois donos para o mesmo tipo e erro de
 * desenho, nao merge silencioso.
 *
 * <p>Instancia mutavel apenas no registro (arranque/fiacao); as leituras sao
 * por copia. A agenda consulta {@link #isRegistered(String)} a cada leitura:
 * fonte de tipo nao registrado e ignorada, nunca exibida.
 */
public final class AgendaEventTypeRegistry {

    private final Map<String, AgendaEventType> byCode = new LinkedHashMap<>();

    /** Registro com os tipos que nascem com a P3.39 (aceite): P2.5 e P3.31. */
    public static AgendaEventTypeRegistry withBornTypes() {
        AgendaEventTypeRegistry registry = new AgendaEventTypeRegistry();
        registry.register(AgendaEventTypes.BILLING_SCHEDULE);
        registry.register(AgendaEventTypes.INTERNAL_AUDIT);
        return registry;
    }

    /** Registra o tipo; re-registro identico e no-op, divergente e conflito. */
    public synchronized AgendaEventTypeRegistry register(AgendaEventType type) {
        if (type == null) {
            throw new IllegalArgumentException("tipo de evento e obrigatorio para registro");
        }
        AgendaEventType existing = byCode.get(type.code());
        if (existing != null && !existing.equals(type)) {
            throw new IllegalStateException("tipo de agenda '" + type.code()
                    + "' ja registrado por " + existing.originTask()
                    + " (" + existing.sourceModule() + "); definicao divergente de "
                    + type.originTask() + " recusada");
        }
        byCode.put(type.code(), type);
        return this;
    }

    public synchronized boolean isRegistered(String code) {
        return code != null && byCode.containsKey(code);
    }

    public synchronized Optional<AgendaEventType> find(String code) {
        return Optional.ofNullable(code == null ? null : byCode.get(code));
    }

    /** Tipos registrados, na ordem de registro. */
    public synchronized List<AgendaEventType> registeredTypes() {
        return List.copyOf(byCode.values());
    }

    public synchronized Collection<String> registeredCodes() {
        return List.copyOf(byCode.keySet());
    }
}
