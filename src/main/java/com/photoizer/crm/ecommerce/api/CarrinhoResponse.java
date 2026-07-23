package com.photoizer.crm.ecommerce.api;

import com.photoizer.crm.foto.api.FotoEnsaioResponse;

import java.math.BigDecimal;
import java.util.List;

public record CarrinhoResponse(
    List<CarrinhoItemResponse> itens,
    int quantidade,
    BigDecimal valorUnitario,
    BigDecimal subtotal
) {
    public static CarrinhoResponse of(List<CarrinhoItemResponse> itens, BigDecimal valorUnitario) {
        var quantidade = itens.size();
        var subtotal = valorUnitario.multiply(BigDecimal.valueOf(quantidade));
        return new CarrinhoResponse(itens, quantidade, valorUnitario, subtotal);
    }
}
