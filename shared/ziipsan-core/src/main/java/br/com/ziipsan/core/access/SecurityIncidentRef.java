package br.com.ziipsan.core.access;

import java.util.Objects;
import java.util.UUID;

/**
 * Referência ao incidente de segurança ao qual uma sessão de emergência se
 * vincula (P3.17; registro do incidente da P3.10, ligado à ocorrência da
 * P2.25).
 *
 * <p>A emergência exige incidente de segurança <b>declarado</b>: o registro
 * existe e a declaração foi feita ({@code declared = true}, com o instante
 * {@code declaredAt}). Registro inexistente, não declarado ou de outro
 * tenant não abre emergência — a ausência nunca vira declaração por omissão
 * (ver {@link AccessPolicy}).
 *
 * <p>Quem resolve o {@code incidentId} para esta referência é a porta
 * {@link SecurityIncidentLookup}: a P3.10 entregou o registro do incidente
 * como modelo ({@code PersonalDataIncident}, com {@code occurrenceId} da
 * P2.25) sem confirmar tabela própria de persistência, por isso esta tarefa
 * não inventa uma tabela de incidente nem uma consulta a ela — a fiação do
 * lookup sobre o registro real é pendência declarada de integração.
 */
public record SecurityIncidentRef(UUID incidentId, UUID tenantId, boolean declared, java.time.Instant declaredAt) {

    public SecurityIncidentRef {
        Objects.requireNonNull(incidentId, "incidentId é obrigatório");
        Objects.requireNonNull(tenantId, "tenantId do incidente é obrigatório");
        if (declared && declaredAt == null) {
            throw new IllegalArgumentException(
                    "incidente declarado exige declaredAt: declaração sem instante não é declaração");
        }
        if (!declared && declaredAt != null) {
            throw new IllegalArgumentException(
                    "incidente não declarado não pode ter declaredAt");
        }
    }
}
