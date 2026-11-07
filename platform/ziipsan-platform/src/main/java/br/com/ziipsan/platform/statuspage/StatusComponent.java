package br.com.ziipsan.platform.statuspage;

/**
 * P3.18: componente exibido na página pública de status
 * ({@code support.status_component}, core {@code V10_4}).
 *
 * <p>Não há campo de status neste record — de propósito. O status é
 * derivado por {@link PublicStatusService} a partir da sonda, dos
 * incidentes abertos e das janelas ativas; um campo editável permitiria
 * a página dizer "operacional" com a sonda fora.
 *
 * @param probeKey chave da sonda que alimenta o componente; {@code null}
 *                 para componente sem sonda (o status deriva só de
 *                 incidentes e janelas, como nas páginas de status em que
 *                 o incidente é a fonte)
 */
public record StatusComponent(
        String componentKey,
        String name,
        String description,
        String probeKey,
        int sortOrder,
        boolean visible) {

    public StatusComponent {
        if (componentKey == null || !componentKey.matches("^[a-z0-9][a-z0-9._-]{0,99}$")) {
            throw new IllegalArgumentException("component_key inválido: " + componentKey);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do componente " + componentKey + " é obrigatório");
        }
        if (probeKey != null && probeKey.isBlank()) {
            throw new IllegalArgumentException(
                    "probe_key do componente " + componentKey + " não pode ser vazio (use null = sem sonda)");
        }
    }

    public boolean hasProbe() {
        return probeKey != null;
    }
}
