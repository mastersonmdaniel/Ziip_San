package br.com.ziipsan.core.backoffice;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Executores da OS completa (ED-36; POC-43: encerrar com executores). Espelha
 * as entradas do portao de programacao/encerramento da P1.31
 * (service_order_crew da V9_1: usuario, funcao, codigo de habilitacao e
 * validade) — NAO substitui o portao do motor, que continua sendo quem
 * barra a transicao; esta validacao e a conferencia do backoffice antes de
 * submeter a OS ao motor, com as mesmas recusas: equipe abaixo do minimo do
 * tipo, pessoa repetida, habilitacao exigida sem codigo ou vencida na data
 * da execucao.
 */
public final class CrewAssignment {

    public record Member(UUID personId, String role, String qualificationCode, LocalDate qualificationValidUntil) {
        public Member {
            Objects.requireNonNull(personId, "executor e obrigatorio");
            if (role == null || role.isBlank()) throw new IllegalArgumentException("funcao do executor e obrigatoria");
            if (qualificationCode != null && qualificationCode.isBlank())
                throw new IllegalArgumentException("codigo de habilitacao em branco nao e habilitacao");
        }
    }

    private CrewAssignment() {}

    /**
     * @param minCrew equipe minima do tipo (service_types.min_crew da P1.31; 1 quando o tipo nao declara)
     * @param qualificationRequired o tipo exige habilitacao vigente de cada executor (controles SST da P1.31)
     * @param onDate data da execucao/programacao contra a qual a validade e conferida
     */
    public static List<Member> validate(List<Member> members, int minCrew, boolean qualificationRequired, LocalDate onDate) {
        if (members == null || members.isEmpty()) throw new IllegalArgumentException("OS executada exige ao menos um executor");
        if (minCrew < 1) throw new IllegalArgumentException("equipe minima deve ser ao menos 1");
        Objects.requireNonNull(onDate, "data de referencia da habilitacao e obrigatoria");
        if (members.size() < minCrew)
            throw new IllegalArgumentException("equipe de " + members.size() + " abaixo do minimo do tipo (" + minCrew + ")");
        Set<UUID> seen = new HashSet<>();
        for (Member m : members) {
            Objects.requireNonNull(m, "executor nulo");
            if (!seen.add(m.personId())) throw new IllegalArgumentException("executor repetido na equipe: " + m.personId());
            if (qualificationRequired) {
                if (m.qualificationCode() == null)
                    throw new IllegalArgumentException("executor " + m.personId() + " sem habilitacao exigida pelo tipo (SST, P1.31)");
                if (m.qualificationValidUntil() == null)
                    throw new IllegalArgumentException("habilitacao do executor " + m.personId() + " sem validade registrada nao aprova (P1.31)");
                if (m.qualificationValidUntil().isBefore(onDate))
                    throw new IllegalArgumentException("habilitacao do executor " + m.personId() + " vencida em " + m.qualificationValidUntil());
            }
        }
        return List.copyOf(members);
    }

    public static List<UUID> executorIds(List<Member> members) {
        return members.stream().map(Member::personId).toList();
    }
}
