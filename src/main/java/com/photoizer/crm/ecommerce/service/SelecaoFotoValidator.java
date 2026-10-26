package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import org.springframework.stereotype.Component;

/**
 * Regras únicas para uma foto entrar na seleção do cliente.
 *
 * <p>Centraliza a validação que antes existia apenas no envio da seleção.
 * Sem isso, o endpoint de seleção aceitava fotos ocultas ou ainda não
 * publicadas, permitindo inclusive baixar o original de uma foto INEDITA.
 */
@Component
public class SelecaoFotoValidator {

    public void validar(FotoEnsaio foto, Agendamento agendamento) {
        if (!foto.getAgendamentoId().equals(agendamento.getId())) {
            throw new FotoIndisponivelException("Foto não pertence a esta galeria");
        }
        if (!foto.isVisivel()) {
            throw new FotoIndisponivelException("Foto oculta não pode ser selecionada");
        }
        if (foto.getStatus() != StatusFoto.PUBLICADA) {
            throw new FotoIndisponivelException(
                "Foto ainda não foi publicada e não pode ser selecionada");
        }
        if (foto.getCompraExtraId() != null) {
            throw new FotoIndisponivelException(
                "Foto já vinculada a uma compra não pode ser selecionada");
        }
        if (foto.getDataDownload() != null) {
            throw new FotoIndisponivelException(
                "Foto já baixada não pode ser selecionada");
        }
        if (foto.getNumeroGaleria() == null) {
            throw new FotoIndisponivelException(
                "Foto ainda não possui numeração de galeria");
        }
    }
}
