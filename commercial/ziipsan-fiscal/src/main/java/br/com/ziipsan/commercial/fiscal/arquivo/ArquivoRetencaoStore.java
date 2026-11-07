package br.com.ziipsan.commercial.fiscal.arquivo;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de persistência dos registros de retenção da P2.18, no mesmo desenho
 * da porta de documentos da P2.6 ({@code NfageDocumentoStore}): o serviço
 * consulta o registro existente do documento antes de arquivar (idempotência
 * e recusa de sobrescrita) e grava o registro novo ao arquivar.
 *
 * <p>A spec da P2.18 <b>não reserva migration</b>: a implementação persistente
 * desta porta (tabela própria ou projeção sobre o dicionário) é decisão da
 * integração, junto da ligação das demais portas — ver RELATORIO-P2.18.md.
 * Os testes usam um duplo em memória que impõe a mesma unicidade
 * (tenant + chave de acesso).
 */
public interface ArquivoRetencaoStore {

    /** Registro de retenção do documento, se já arquivado. */
    Optional<RegistroRetencaoXml> buscar(UUID tenantId, String accessKey);

    /** Grava o registro de um arquivamento concluído. */
    void registrar(RegistroRetencaoXml registro);
}
