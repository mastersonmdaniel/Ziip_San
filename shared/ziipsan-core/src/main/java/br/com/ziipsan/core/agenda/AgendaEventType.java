package br.com.ziipsan.core.agenda;

import java.util.regex.Pattern;

/**
 * Tipo de evento de agenda (P3.39): o CONTRATO que cada fonte registra antes de
 * expor eventos. A agenda so exibe tipos REGISTRADOS neste contrato; evento de
 * tipo nao registrado nunca aparece (ver AgendaEventTypeRegistry).
 *
 * <p>{@code code} e estavel, kebab-case ({@code billing-schedule},
 * {@code internal-audit}); {@code originTask} e a tarefa DONA da fonte (quem
 * registra e quem mantem a fonte); {@code sourceModule} e o modulo dono dos
 * dados. Nenhum tipo nasce aqui alem dos dois do aceite da P3.39 (ver
 * {@link AgendaEventTypes}): os tipos das fontes posteriores (ED-36, ED-35,
 * ED-51, ED-56, ED-98) sao registrados PELA TAREFA DE CADA FONTE, quando ela
 * existir — registrar agora um tipo sem fonte fabricaria uma agenda vazia
 * com aparencia de cobertura.
 */
public record AgendaEventType(String code, String label, String originTask, String sourceModule) {

    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");

    public AgendaEventType {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("tipo de evento de agenda exige codigo kebab-case: " + code);
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("tipo de evento de agenda exige rotulo: " + code);
        }
        if (originTask == null || originTask.isBlank()) {
            throw new IllegalArgumentException("tipo de evento de agenda exige a tarefa dona da fonte: " + code);
        }
        if (sourceModule == null || sourceModule.isBlank()) {
            throw new IllegalArgumentException("tipo de evento de agenda exige o modulo da fonte: " + code);
        }
    }
}
