package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;

public record CarrinhoItemResponse(
    FotoEnsaioResponse foto,
    int quantidadeTotal,
    int pacoteQuantidadeFotos,
    BigDecimal valorUnitario,
    BigDecimal subtotal
) {
    public static CarrinhoItemResponse of(FotoEnsaioResponse foto, int quantidadeTotal, int pacoteQuantidadeFotos, BigDecimal valorUnitario) {
        return new CarrinhoItemResponse(
            foto,
            quantidadeTotal,
            pacoteQuantidadeFotos,
            valorUnitario,
            valorUnitario.multiply(BigDecimal.valueOf(1))
        );
    }
}
