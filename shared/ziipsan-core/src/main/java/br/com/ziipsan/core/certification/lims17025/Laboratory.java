package br.com.ziipsan.core.certification.lims17025;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Laboratorio com ESCOPO (aceite da ED-51): a entidade nasce com o
 * escopo declarado — conjunto de pares (metodo, parametro) que o
 * laboratorio executa — e cada item do escopo diz se esta ou nao no
 * escopo ACREDITADO declarado. Laboratorio sem escopo nao nasce:
 * escopo vazio nao apoia ensaio nenhum.
 *
 * <p>O escopo e DADO DECLARADO do tenant/laboratorio: nenhuma lista
 * de metodos ou parametros nasce de fabrica nesta classe. A
 * acreditacao em si e do laboratorio perante o organismo competente
 * (o software apoia e registra o escopo declarado — nao acredita
 * ninguem; fronteira do levantamento 18g, E-LIMS17025: "o software
 * apoia, a acreditacao e do laboratorio").
 *
 * <p>So com a norma ligada: o nascimento passa pela porta da
 * {@link Lims17025Activation} (ADR-0041).
 */
public final class Laboratory {

    /** Um item do escopo declarado: metodo x parametro, acreditado ou nao. */
    public record ScopeEntry(String methodRef, String parameterKey, boolean accredited) {
        public ScopeEntry {
            if (methodRef == null || methodRef.isBlank())
                throw new IllegalArgumentException("methodRef do item de escopo e obrigatorio");
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parameterKey do item de escopo e obrigatorio");
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final String name;
    private final List<ScopeEntry> scope = new ArrayList<>();

    private Laboratory(UUID id, UUID tenantId, String name, List<ScopeEntry> scope) {
        if (id == null) throw new IllegalArgumentException("id do laboratorio e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId do laboratorio e obrigatorio");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("nome do laboratorio e obrigatorio");
        if (scope == null || scope.isEmpty())
            throw new IllegalArgumentException("laboratorio sem escopo declarado nao nasce: "
                    + "o escopo (metodo x parametro) e o fundamento do apoio a ISO/IEC 17025");
        for (ScopeEntry e : scope) {
            Objects.requireNonNull(e, "item de escopo nao pode ser nulo");
            boolean dup = this.scope.stream().anyMatch(x ->
                    x.methodRef().equals(e.methodRef()) && x.parameterKey().equals(e.parameterKey()));
            if (dup) throw new IllegalArgumentException("item de escopo duplicado: "
                    + e.methodRef() + " / " + e.parameterKey());
            this.scope.add(e);
        }
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
    }

    /** Nascimento do laboratorio: so com a norma ligada (porta da ativacao). */
    public static Laboratory register(Lims17025Activation activation, UUID id, UUID tenantId,
                                      String name, List<ScopeEntry> scope) {
        Objects.requireNonNull(activation, "activation e obrigatoria");
        activation.requireEnabled();
        return new Laboratory(id, tenantId, name, scope);
    }

    /** Verdadeiro quando o par (metodo, parametro) esta no escopo declarado. */
    public boolean covers(String methodRef, String parameterKey) {
        return scope.stream().anyMatch(e ->
                e.methodRef().equals(methodRef) && e.parameterKey().equals(parameterKey));
    }

    /** Verdadeiro quando o par esta no escopo declarado E marcado como acreditado. */
    public boolean coversAsAccredited(String methodRef, String parameterKey) {
        return scope.stream().anyMatch(e ->
                e.methodRef().equals(methodRef) && e.parameterKey().equals(parameterKey) && e.accredited());
    }

    /** Porta do ensaio: fora do escopo declarado, o ensaio nao nasce neste laboratorio. */
    public void requireInScope(String methodRef, String parameterKey) {
        if (!covers(methodRef, parameterKey)) {
            throw new IllegalStateException("ensaio fora do escopo declarado do laboratorio "
                    + name + ": " + methodRef + " / " + parameterKey);
        }
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String name() { return name; }
    public List<ScopeEntry> scope() { return List.copyOf(scope); }
}
