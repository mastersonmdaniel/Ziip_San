package br.com.ziipsan.core.agenda;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de agenda como a agenda o EXIBE (modelo de leitura da P3.39): id e
 * tenant da fonte, tipo registrado, titulo ja resolvido pela fonte, janela
 * {@code startsAt}/{@code endsAt} ({@code endsAt} nulo = evento pontual ou de
 * dia inteiro, conforme a fonte declarar no titulo/endereco), o objeto de
 * origem (tipo + id) e o endereco do registro (a rota que abre o evento na
 * tela do modulo dono — mesmo papel do {@code recordAddress} do item de
 * trabalho da P2.28).
 *
 * <p>A agenda NAO e dona deste dado: ela nao cria, nao altera e nao guarda
 * eventos; cada {@link AgendaEvent} e projetado na leitura a partir do
 * registro da fonte. Invariantes: janela coerente (fim nunca antes do
 * inicio) e endereco obrigatorio — evento sem caminho de volta para a fonte
 * nao entra na agenda.
 */
public record AgendaEvent(UUID id, UUID tenantId, String typeCode, String title,
                          Instant startsAt, Instant endsAt, String sourceObjectType,
                          UUID sourceObjectId, String recordAddress) {

    public AgendaEvent {
        if (id == null || tenantId == null) {
            throw new IllegalArgumentException("evento de agenda exige id e tenant");
        }
        if (typeCode == null || typeCode.isBlank()) {
            throw new IllegalArgumentException("evento de agenda exige o tipo registrado");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("evento de agenda exige titulo");
        }
        if (startsAt == null) {
            throw new IllegalArgumentException("evento de agenda exige o inicio");
        }
        if (endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("evento de agenda com fim antes do inicio");
        }
        if (sourceObjectType == null || sourceObjectType.isBlank() || sourceObjectId == null) {
            throw new IllegalArgumentException("evento de agenda exige o objeto de origem (tipo e id)");
        }
        if (recordAddress == null || recordAddress.isBlank()) {
            throw new IllegalArgumentException("evento de agenda exige o endereco do registro de origem");
        }
    }
}
