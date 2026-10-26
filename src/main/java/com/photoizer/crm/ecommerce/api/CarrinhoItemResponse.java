package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CarrinhoItemResponse(
    FotoEnsaioResponse foto,
    int quantidadeTotal,
    int pacoteQuantidadeFotos,
    BigDecimal valorUnitario,
    BigDecimal subtotal
) {
    /**
     * PATTERN: Factory Method
     * Corrigido: subtotal agora é valorUnitario * quantidadeTotal (antes retornava apenas valorUnitario).
     */
    public static CarrinhoItemResponse of(FotoEnsaioResponse foto, int quantidadeTotal, int pacoteQuantidadeFotos, BigDecimal valorUnitario) {
        // O subtotal e por item; o total do carrinho e somado em CarrinhoResponse.
        var subtotal = valorUnitario.setScale(2, RoundingMode.HALF_UP);
        return new CarrinhoItemResponse(
            foto,
            quantidadeTotal,
            pacoteQuantidadeFotos,
            valorUnitario,
            subtotal
        );
    }
}
