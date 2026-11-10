package br.com.ziipsan.core.slaos;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Codificacao problema-causa-remedio obrigatoria no encerramento (II-4),
 * no espirito da codificacao de defeito da ISO 14224 citada no PDCA-01.
 *
 * <p><b>Base viva declarada:</b> o pacote da ED-36
 * ({@code ~/workspace/ziipsan-producao/ed-36/}) NAO fixa catalogo de
 * problema-causa-remedio — a sua entrega e o backoffice da OS sobre a
 * P1.31 (programacao, executores, materiais, custo, anexos). Logo, nenhum
 * codigo de catalogo e afirmado aqui como facto: o CONTEUDO do catalogo
 * (quais problemas, causas e remedios existem e como se combinam) e DADO
 * do tenant, {@code [A DEFINIR]} (pendencia P-II4-CATALOGO), fornecido
 * pela porta {@link PcrCatalog}. O que esta classe fixa e o MECANISMO:
 * a tripla e obrigatoria, nao vazia, e so vale se o catalogo injectado a
 * reconhecer e aceitar a combinacao.
 */
public record ProblemCauseRemedy(String problemCode, String causeCode, String remedyCode) {

    public ProblemCauseRemedy {
        problemCode = requireCode(problemCode, "problema");
        causeCode = requireCode(causeCode, "causa");
        remedyCode = requireCode(remedyCode, "remedio");
    }

    private static String requireCode(String code, String field) {
        if (code == null || code.isBlank())
            throw new IllegalArgumentException("codigo de " + field + " e obrigatorio na codificacao problema-causa-remedio");
        return code.trim();
    }

    public enum InvalidReason { UNKNOWN_PROBLEM, CAUSE_NOT_FOR_PROBLEM, REMEDY_NOT_FOR_COMBINATION }

    /** Porta do catalogo: o conteudo vem do tenant/fonte, nunca desta classe. */
    public interface PcrCatalog {
        /** Vazio = tripla valida; presente = motivo tipificado da invalidade. */
        Optional<InvalidReason> validate(ProblemCauseRemedy coding);
    }

    /**
     * Catalogo em memoria construido a partir de dados externos
     * (problema -> causa -> remedios aceites). Serve a fiacao e aos
     * testes; os dados passados ao construtor NAO sao constantes de
     * dominio — quem os fornece e a configuracao do tenant.
     */
    public static final class InMemoryPcrCatalog implements PcrCatalog {
        private final Map<String, Map<String, Set<String>>> data;

        public InMemoryPcrCatalog(Map<String, Map<String, Set<String>>> data) {
            this.data = Map.copyOf(Objects.requireNonNull(data, "dados do catalogo sao obrigatorios"));
        }

        @Override
        public Optional<InvalidReason> validate(ProblemCauseRemedy coding) {
            Map<String, Set<String>> causes = data.get(coding.problemCode());
            if (causes == null) return Optional.of(InvalidReason.UNKNOWN_PROBLEM);
            Set<String> remedies = causes.get(coding.causeCode());
            if (remedies == null) return Optional.of(InvalidReason.CAUSE_NOT_FOR_PROBLEM);
            if (!remedies.contains(coding.remedyCode()))
                return Optional.of(InvalidReason.REMEDY_NOT_FOR_COMBINATION);
            return Optional.empty();
        }
    }
}
