package br.com.ziipsan.commercial.collection.legacydebit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Porta do arquivo FEBRABAN 150 (remessa/retorno) - P3.24.
 *
 * <p><b>[SEM FONTE - layout fisico]</b> Nenhuma fonte do repo/plano traz o
 * leiaute posicional oficial do FEBRABAN 150 (posicoes, filler, codigos de
 * ocorrencia do retorno). Como no precedente da ED-30 (layouts de biro),
 * NENHUM campo posicional nem codigo foi inventado: esta porta transporta
 * os registros LOGICOS B, C, E e F; o adaptador que serializa o arquivo
 * posicional nasce com o manual do banco do piloto.
 *
 * <p>Papel de cada registro NESTE pacote (mapeamento proposto, declarado
 * no relatorio, a conferir contra o manual na integracao):
 * B = inclusao de optante conferido; C = cancelamento de optante;
 * E = debito de um ciclo; F = trailer do lote (contagem e soma de controle).
 * O codigo de ocorrencia do retorno e guardado VERBATIM ({@code outcomeCode}),
 * sem tabela de codigos presumida.
 */
public interface Febraban150Port {

    enum RecordType { B, C, E, F }

    /** Registro logico da remessa. {@code debitId} so existe em registros E. */
    record RemittanceRecord(RecordType type, String enrollmentId, UUID debitId,
                            BigDecimal amount, LocalDate debitDate, int sequence) {
    }

    /** Lote logico de remessa (a ordem dos registros e a da lista). */
    record RemittanceBatch(UUID tenantId, String receiverBankCode, List<RemittanceRecord> records) {
    }

    /** Entrada logica do retorno, ja lida pelo adaptador do banco. */
    record ReturnEntry(UUID debitId, String enrollmentId, BigDecimal amount,
                       String outcomeCode, boolean settled) {
    }

    /** Serializa e entrega o lote (adaptador real = manual do banco; sem envio nesta tarefa). */
    void writeRemittance(RemittanceBatch batch);

    /** Le o retorno do banco (adaptador real = manual do banco). */
    List<ReturnEntry> readReturn(UUID tenantId, String fileReference);
}
