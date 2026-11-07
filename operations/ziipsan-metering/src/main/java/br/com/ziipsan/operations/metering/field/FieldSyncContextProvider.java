package br.com.ziipsan.operations.metering.field;

import java.util.List;
import java.util.UUID;

/**
 * Porta de montagem do {@link FieldSyncContext}: a implementacao de producao le os
 * parametros vigentes do tenant (read.ocr_mode, read.ocr_tolerance_digits), os digitos
 * dos hidrometros instalados e a leitura anterior de cada instalacao da rota. A ED-18
 * define a porta e a regra; a ligacao com as consultas do cadastro/metering entra na
 * integracao da camada (ver relatorio).
 */
public interface FieldSyncContextProvider {

    FieldSyncContext contextFor(UUID tenantId, List<FieldReadingSubmission> submissions);
}
