package br.com.ziipsan.intelligence.gis.hydraulic;

import java.util.List;

/** Problemas que impedem um .inp confiável; a lista inteira vai na mensagem (disciplina da GIS-03). */
public class HydraulicValidationException extends RuntimeException {
    private final List<String> issues;

    public HydraulicValidationException(List<String> issues) {
        super(String.join(" | ", issues));
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return issues;
    }
}
