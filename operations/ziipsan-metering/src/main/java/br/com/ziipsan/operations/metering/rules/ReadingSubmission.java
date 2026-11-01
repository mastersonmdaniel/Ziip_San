package br.com.ziipsan.operations.metering.rules;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha do retorno de leitura (campo, coletor, celular ou telemetria), como chegou.
 *
 * <p>Os campos podem estar ausentes ou inválidos: o objetivo do {@link ReadingValidator} é
 * justamente dizer o que está errado. O indicador de "confirmação de leitura" do arquivo posicional
 * do legado não existe aqui: um booleano tipado não tem valor inválido.
 *
 * @param unitId      unidade consumidora; nula se não foi possível identificá-la
 * @param kind        tipo de medição; nulo se desconhecido
 * @param readingDate data da leitura; nula se ausente ou ilegível
 * @param reading     leitura do mostrador; nula quando só há anormalidade
 * @param anomalyCode código da anormalidade lançada pelo leiturista; nulo se nenhuma
 */
public record ReadingSubmission(UUID unitId, MeasurementKind kind, LocalDate readingDate, Integer reading,
                                String anomalyCode) {
}
