package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.TargetType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Alvos multiplos e tipados da OS completa (ED-36), SOBRE o nucleo da P1.31:
 * a classe de alvo e o {@link TargetType} real do motor (14 classes de
 * ALVOS-DA-OS-E-HIERARQUIA-DE-ATIVOS), incluindo EQUIPMENT e COMPONENT do
 * cadastro de ativos da P1.35 — nenhum modelo, tipo ou efeito e redefinido
 * aqui. As regras espelham os CHECKs da V9_1 (service_order_targets):
 * LOCATION_ONLY carrega local e nenhum id de entidade; toda outra classe
 * carrega o id e nenhum local; (tipo, entidade) nao se repete na mesma OS.
 *
 * <p>Se a classe e declaravel para um tipo de servico continua sendo fato
 * de execucao (verificador de existencia registrado pelo modulo dono —
 * P1.35 para EQUIPMENT/COMPONENT); esta validacao confere a OS contra as
 * classes aceitas pelo tipo, entregues pelo chamador a partir do registro
 * do motor.
 */
public final class OrderTargets {

    public record Target(TargetType type, UUID entityId, String locationText) {
        public Target {
            Objects.requireNonNull(type, "classe do alvo e obrigatoria (TargetType da P1.31)");
            if (type.isLocationOnly()) {
                if (entityId != null) throw new IllegalArgumentException("LOCATION_ONLY nao carrega id de entidade (V9_1)");
                if (locationText == null || locationText.isBlank()) throw new IllegalArgumentException("LOCATION_ONLY exige o local");
            } else {
                Objects.requireNonNull(entityId, "alvo " + type + " exige o id da entidade");
                if (locationText != null) throw new IllegalArgumentException("alvo " + type + " nao carrega local (V9_1)");
            }
        }
        public static Target entity(TargetType type, UUID entityId) { return new Target(type, entityId, null); }
        public static Target location(String locationText) { return new Target(TargetType.LOCATION_ONLY, null, locationText); }
    }

    private OrderTargets() {}

    public static List<Target> validate(List<Target> targets, Set<TargetType> acceptedByServiceType) {
        if (targets == null || targets.isEmpty()) throw new IllegalArgumentException("OS completa exige ao menos um alvo");
        Objects.requireNonNull(acceptedByServiceType, "classes aceitas pelo tipo de servico (registro da P1.31) sao obrigatorias");
        if (acceptedByServiceType.isEmpty()) throw new IllegalArgumentException("tipo de servico sem classe de alvo aceita");
        Set<String> seen = new HashSet<>();
        for (Target t : targets) {
            Objects.requireNonNull(t, "alvo nulo na OS");
            if (!acceptedByServiceType.contains(t.type()))
                throw new IllegalArgumentException("classe " + t.type() + " nao aceita pelo tipo de servico (P1.31)");
            String key = t.type() + ":" + t.entityId();
            if (t.entityId() != null && !seen.add(key))
                throw new IllegalArgumentException("alvo repetido na OS: " + key + " (uq_targets_order_target da V9_1)");
        }
        return List.copyOf(targets);
    }

    /** Alvos de ativo da P1.35 presentes na OS (equipamento/componente). */
    public static List<Target> assetTargets(List<Target> targets) {
        return targets.stream().filter(t -> t.type() == TargetType.EQUIPMENT || t.type() == TargetType.COMPONENT).toList();
    }
}
