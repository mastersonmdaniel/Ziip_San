package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.ConsumptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumptionRecordRepository extends JpaRepository<ConsumptionRecord, UUID> {

    /**
     * Idempotencia: a unica linha da unidade, servico e referencia (dia 1), se existir.
     *
     * <p><b>Atencao (DAD-14):</b> depois do versionamento pode haver VARIAS linhas da
     * mesma (unidade, servico, referencia) -- uma por versao. Este metodo so e seguro
     * onde ainda nao houve retificacao; quem quer a versao vigente usa
     * {@link #findCurrentByUnitIdAndServiceKindAndReference}.
     */
    Optional<ConsumptionRecord> findByUnitIdAndServiceKindAndReference(UUID unitId,
                                                                       ConsumptionRecord.ServiceKind serviceKind,
                                                                       LocalDate reference);

    /** Historico de uma unidade e servico, do mais recente para o mais antigo (entrada da media). */
    List<ConsumptionRecord> findByUnitIdAndServiceKindAndReferenceBeforeOrderByReferenceDesc(
            UUID unitId, ConsumptionRecord.ServiceKind serviceKind, LocalDate before);

    // ---- Versionamento (V4_4 do billing, P2.5, DAD-14) ----

    /** A versao vigente (superseded_by_id NULL) da unidade, servico e referencia, se existir. */
    Optional<ConsumptionRecord> findByUnitIdAndServiceKindAndReferenceAndSupersededByIdIsNull(
            UUID unitId, ConsumptionRecord.ServiceKind serviceKind, LocalDate reference);

    /** Todas as versoes de uma referencia, da original a vigente (a cadeia da retificacao). */
    List<ConsumptionRecord> findByUnitIdAndServiceKindAndReferenceOrderByVersionNoAsc(
            UUID unitId, ConsumptionRecord.ServiceKind serviceKind, LocalDate reference);

    /** Todas as versoes (vigentes e substituidas) de uma unidade e servico ate uma referencia. */
    List<ConsumptionRecord> findByUnitIdAndServiceKindAndReferenceLessThanEqual(
            UUID unitId, ConsumptionRecord.ServiceKind serviceKind, LocalDate reference);

    /** As versoes que uma conta faturou. */
    List<ConsumptionRecord> findByBillId(UUID billId);
}
