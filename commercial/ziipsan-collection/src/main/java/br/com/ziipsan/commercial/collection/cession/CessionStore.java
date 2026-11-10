package br.com.ziipsan.commercial.collection.cession;

import br.com.ziipsan.commercial.collection.cession.CessionRegistry.Registration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ED-103/E4+E3 (decisao 62) — loja da mecanica de cessao: registos de
 * cessao e trilha de auditoria de exportacoes. Produtor (execucao
 * ED-103, decisao 62): sessao 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
 *
 * <p>Contrato de atomicidade (o desenho que impoe a trava): 
 * {@link #tryCreateActive} e {@link #replace} sao operacoes atomicas —
 * verificar-ausencia-de-activo+e+criar e comparar-e+trocar acontecem sem
 * janela para outra thread. A implementacao em memoria cumpre-o com um
 * unico cadeado; a persistencia SQL (ficheiro em {@code db/pendente})
 * impoe o mesmo desenho pelo indice unico parcial sobre registos
 * activos, por (tenant, recebivel).
 */
public interface CessionStore {

    /**
     * Cria o registo SE nao existir registo activo do mesmo recebivel no
     * tenant. Devolve false (sem criar nada) quando a trava dispara.
     */
    boolean tryCreateActive(Registration registration);

    /** Registo activo (RESERVADO/CEDIDO) do recebivel, se existir. */
    Optional<Registration> activeRegistration(UUID tenantId, String receivableId);

    /** Substitui {@code current} por {@code next} se o registo guardado ainda for {@code current}. */
    boolean replace(Registration current, Registration next);

    /** Todos os registos do tenant (activos e terminais), ordem de criacao. */
    List<Registration> registrations(UUID tenantId);

    /** Grava um registo da trilha de auditoria de exportacao (E3). */
    void appendExportAudit(ExportAudit audit);

    /** Trilha de auditoria de exportacoes do tenant, ordem de gravacao. */
    List<ExportAudit> exportAudits(UUID tenantId);
}
