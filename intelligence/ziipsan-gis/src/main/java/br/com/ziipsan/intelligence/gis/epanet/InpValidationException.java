package br.com.ziipsan.intelligence.gis.epanet;

import java.util.List;

/**
 * O modelo não pode virar um arquivo EPANET confiável. Carrega a lista completa de problemas, para
 * que o cadastro seja corrigido de uma vez e não um erro por tentativa.
 */
public class InpValidationException extends RuntimeException {

    private final List<String> issues;

    public InpValidationException(List<String> issues) {
        super("Rede inválida para o EPANET (" + issues.size() + " problema(s)): "
                + String.join(" | ", issues.stream().limit(10).toList())
                + (issues.size() > 10 ? " | ..." : ""));
        this.issues = List.copyOf(issues);
    }

    public List<String> getIssues() {
        return issues;
    }
}
