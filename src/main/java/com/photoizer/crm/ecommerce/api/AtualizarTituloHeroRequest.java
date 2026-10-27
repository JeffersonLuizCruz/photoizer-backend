package com.photoizer.crm.ecommerce.api;

import jakarta.validation.constraints.Size;

/**
 * Texto customizado exibido no hero da galeria.
 *
 * <p>{@code null} ou vazio remove o texto customizado e volta a exibir o nome
 * do cliente.
 */
public record AtualizarTituloHeroRequest(
    @Size(max = 120, message = "O texto do hero deve ter no máximo 120 caracteres")
    String tituloHero
) {}
