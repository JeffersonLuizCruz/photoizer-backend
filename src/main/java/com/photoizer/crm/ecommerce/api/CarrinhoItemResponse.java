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
        var subtotal = valorUnitario.multiply(BigDecimal.valueOf(quantidadeTotal))
            .setScale(2, RoundingMode.HALF_UP);
        return new CarrinhoItemResponse(
            foto,
            quantidadeTotal,
            pacoteQuantidadeFotos,
            valorUnitario,
            subtotal
        );
    }
}
