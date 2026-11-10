package br.com.ziipsan.grs.mtr.service;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E6.
import br.com.ziipsan.core.effluent.MtrLink;
import br.com.ziipsan.core.effluent.SludgeRecord;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import br.com.ziipsan.grs.mtr.domain.WasteGenerator;
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import br.com.ziipsan.grs.mtr.repository.WasteGeneratorRepository;
import br.com.ziipsan.grs.mtr.repository.WasteMtrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Lodo de ETA/ETE da ED-105 → MTR com o próprio tenant como gerador (JOR-17, ED-104 E6).
 *
 * <p>Consome o núcleo {@code core.effluent} por leitura: a partir de um {@link SludgeRecord}
 * (e do seu {@link MtrLink}), emite o MTR cujo gerador é o gerador-do-tenant registado e liga o
 * vínculo por {@link MtrLink#linkAutomatic}. Sem registo de lodo não há emissão por esta via;
 * vínculo EXEMPT é recusado pelo próprio núcleo; registo já com MTR ligado devolve o existente
 * (idempotência pela coluna sludge_record_id). A quantidade do registo tem de estar em kg —
 * o MTR do módulo é denominado em kg e nenhuma conversão de unidade é inventada.
 */
@Service
@RequiredArgsConstructor
public class SludgeMtrEmissionService {

    private final MtrService mtrService;
    private final WasteMtrRepository mtrRepository;
    private final WasteGeneratorRepository generatorRepository;

    /**
     * @param tenantGeneratorId gerador registado que representa o próprio tenant (JOR-17)
     * @param request           destino/transporte/classe declarados por quem opera a emissão
     */
    @Transactional
    public WasteMtr emitFromSludge(SludgeRecord record, MtrLink link, UUID tenantGeneratorId,
                                   EmissionRoute request) {
        UUID tenantId = requireTenant();
        if (record == null) {
            throw new IllegalArgumentException("sem registo de lodo não há emissão de MTR por esta via (E6)");
        }
        if (!record.tenantId().equals(tenantId)) {
            throw new IllegalArgumentException("registo de lodo de outro tenant");
        }
        if (!"kg".equalsIgnoreCase(record.quantityUnit())) {
            throw new IllegalArgumentException("quantidade do registo de lodo fora de kg ('"
                    + record.quantityUnit() + "'): nenhuma conversão é inventada — pese ou registe em kg");
        }
        WasteGenerator generator = generatorRepository.findByIdAndTenantId(tenantGeneratorId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Generator not found"));
        if (!generator.getId().equals(tenantGeneratorId)) {
            throw new IllegalStateException("gerador do tenant inconsistente");
        }

        WasteMtr existing = mtrRepository.findBySludgeRecordIdAndTenantId(tenantId, record.id()).orElse(null);
        if (existing != null) {
            return existing;
        }

        WasteMtr mtr = mtrService.createMtr(new MtrService.CreateMtrRequest(
                tenantGeneratorId, request.transporterId(), request.destinationId(), request.wasteClassId(),
                record.quantity(), "A_GRANEL", null, null, null,
                Map.of("origem", "ED-105", "sludgeRecordId", record.id().toString(),
                        "residueKind", record.residueKind().name(), "originCode", record.originCode()),
                null, record.id()));

        if (link != null) {
            link.linkAutomatic(mtr.getMtrNumber(), Instant.now());
        }
        return mtr;
    }

    /** Rota declarada da emissão: quem transporta, para onde vai e com que classe codificada. */
    public record EmissionRoute(UUID transporterId, UUID destinationId, UUID wasteClassId) {
        public EmissionRoute {
            if (transporterId == null || destinationId == null || wasteClassId == null) {
                throw new IllegalArgumentException("rota da emissão exige transportador, destinador e classe");
            }
        }
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("sem tenant no contexto");
        }
        return tenantId;
    }
}
