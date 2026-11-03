package br.com.ziipsan.commercial.fiscal.port;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Área separada da homologação fiscal local (ADR-0033, ajuste v2.9; FISCAL-PORTE-JAVA §3.6).
 *
 * <p>Os XML assinados na produção restrita (e-CNPJ da própria Ziip/TGP) ficam SÓ aqui: fora
 * das tabelas do produto (inclusive {@code fiscal_documents_xml}), fora do armazenamento com
 * retenção imutável e fora dos backups locais. Ao fim de cada ciclo de testes, {@link
 * #purgeCycle(String)} apaga os XML do ciclo e devolve o manifesto da exclusão (ciclo, data,
 * quantidade e hash do lote) — o único registro que fica. Do ciclo só se guardam os vetores
 * de referência sem assinatura, no corpus (P1.10).
 */
public interface HomologationXmlArea {

    /** Manifesto da exclusão de um ciclo: o que foi apagado, sem guardar o conteúdo. */
    record PurgeManifest(String cycle, OffsetDateTime purgedAt, int count, String sha256) {
    }

    /** Grava o XML assinado de um documento, dentro do diretório do ciclo. */
    Path write(String cycle, UUID documentId, byte[] signedXml);

    /**
     * Apaga todos os XML do ciclo e devolve o manifesto. Sem o manifesto, a exclusão não é
     * registrada — e exclusão sem registro é o que a regra proíbe.
     */
    PurgeManifest purgeCycle(String cycle);
}
