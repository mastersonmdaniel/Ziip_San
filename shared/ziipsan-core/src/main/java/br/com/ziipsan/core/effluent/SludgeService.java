package br.com.ziipsan.core.effluent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Servico de destinacao do lodo e dos residuos da estacao (OPE-25,
 * RAS-17), sobre os parametros do tenant e a porta da agenda:
 * <ol>
 *   <li>o destino e validado contra
 *       {@code waste.sludge_destination_catalog} — fora do catalogo,
 *       a saida recusa (nenhum destino presumido);</li>
 *   <li>a saida grava transportador, destino e documento de
 *       transporte no {@link SludgeRecord};</li>
 *   <li>o MTR do gerador: origem em
 *       {@code waste.mtr_exempt_origins} nasce isenta; as demais
 *       criam o item MANUAL na agenda da ED-98 pela porta e o
 *       vinculo {@link MtrLink} pendente — a emissao automatica e
 *       da ED-104, fora desta tarefa.</li>
 * </ol>
 */
public final class SludgeService {

    private final EffluentPorts.ManualAgendaPort agenda;

    public SludgeService(EffluentPorts.ManualAgendaPort agenda) {
        if (agenda == null) throw new IllegalArgumentException(
                "porta da agenda da ED-98 e obrigatoria (item manual de MTR, RAS-17)");
        this.agenda = agenda;
    }

    /**
     * Executa a saida do registro e devolve o vinculo de MTR
     * resultante. Os parametros chegam crus do tenant e sao
     * validados aqui ({@link EffluentParameters}): catalogo ausente
     * ou origens nao declaradas sao erro explicito, nunca fallback.
     */
    public MtrLink dispatch(SludgeRecord record, List<String> rawDestinationCatalog,
                            List<String> rawMtrExemptOrigins, String transporterRef,
                            String destinationCode, String transportDocumentRef, Instant at) {
        if (record == null) throw new IllegalArgumentException("registro de residuo e obrigatorio");
        List<String> catalog = EffluentParameters.sludgeDestinationCatalog(rawDestinationCatalog);
        List<String> exemptOrigins = EffluentParameters.mtrExemptOrigins(rawMtrExemptOrigins);
        if (!EffluentParameters.destinationAllowed(catalog, destinationCode))
            throw new IllegalArgumentException("destino " + destinationCode
                    + " fora de " + EffluentParameters.SLUDGE_DESTINATION_CATALOG
                    + " do tenant: destinacao recusada (OPE-25)");
        record.dispatch(transporterRef, destinationCode, transportDocumentRef, at);
        if (EffluentParameters.mtrExempt(exemptOrigins, record.originCode())) {
            return MtrLink.exempt(UUID.randomUUID(), record.tenantId(), record.id());
        }
        String itemRef = agenda.createManualMtrItem(record.tenantId(), record.id(),
                "Emitir MTR como gerador do residuo " + record.residueKind()
                        + " da unidade " + record.unitId() + " (saida " + record.id() + ")");
        return MtrLink.pendingManual(UUID.randomUUID(), record.tenantId(), record.id(), itemRef);
    }
}
