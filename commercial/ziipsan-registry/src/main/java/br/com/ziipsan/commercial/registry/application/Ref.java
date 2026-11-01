package br.com.ziipsan.commercial.registry.application;

import java.util.UUID;

/** Referência resumida a outra entidade, para a tela exibir sem nova consulta ({@code Ref} em types.ts). */
public record Ref(UUID id, Object code, String name) {
}
