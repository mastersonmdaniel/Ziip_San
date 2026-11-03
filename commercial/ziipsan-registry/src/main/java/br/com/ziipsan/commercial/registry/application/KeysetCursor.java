package br.com.ziipsan.commercial.registry.application;

import java.util.UUID;

/**
 * Cursor da paginação keyset (P0.22): o par (valor da ordenação, id) da última linha
 * da página anterior. A ordenação é sempre crescente, com desempate pelo id, então o
 * par identifica exatamente onde a próxima página começa — sem COUNT e sem offset.
 *
 * <p>Na API o cursor viaja opaco (base64url do valor + "." + id, ver
 * {@link CustomerService}); aqui dentro ele já vem decodificado e validado.
 */
public record KeysetCursor(String sortValue, UUID id) {
}
