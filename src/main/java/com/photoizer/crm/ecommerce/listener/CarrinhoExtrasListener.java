package com.photoizer.crm.ecommerce.listener;

import com.photoizer.crm.ecommerce.event.ItemCarrinhoAdicionadoEvent;
import com.photoizer.crm.ecommerce.event.ItemCarrinhoRemovidoEvent;
import com.photoizer.crm.ecommerce.service.SelecaoEnsaioService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém o snapshot da seleção ativa sincronizado com o carrinho.
 *
 * <p>Durante a seleção/edição, o carrinho funciona como a lista de extras que
 * serão editados junto com o pacote. Adicionar ou remover um item do carrinho
 * precisa refletir no snapshot imediatamente: assim o estúdio vê a lista certa
 * no ZIP/manifest e a publicação fica bloqueada até editar o extra novo.
 *
 * <p>Fora de uma seleção ativa (fase SELECAO antes do envio, comercialização ou
 * seleção publicada) as chamadas são no-op.
 */
@Component
public class CarrinhoExtrasListener {

    private final SelecaoEnsaioService selecaoEnsaioService;

    public CarrinhoExtrasListener(SelecaoEnsaioService selecaoEnsaioService) {
        this.selecaoEnsaioService = selecaoEnsaioService;
    }

    @EventListener
    @Transactional
    public void onItemAdicionado(ItemCarrinhoAdicionadoEvent event) {
        selecaoEnsaioService.incluirExtra(event.agendamentoId(), event.fotoId());
    }

    @EventListener
    @Transactional
    public void onItemRemovido(ItemCarrinhoRemovidoEvent event) {
        selecaoEnsaioService.removerExtra(event.agendamentoId(), event.fotoId());
    }
}
