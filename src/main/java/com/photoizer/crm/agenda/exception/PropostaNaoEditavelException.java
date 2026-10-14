package com.photoizer.crm.agenda.exception;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

/**
 * Lançada quando se tenta alterar uma proposta que não está mais em
 * pré-reserva (ex.: já assinada pelo cliente ou já decidida). A edição só é
 * permitida enquanto o cliente ainda não assinou, para preservar a integridade
 * do snapshot/termo assinado.
 */
public class PropostaNaoEditavelException extends UnprocessableException {

    public PropostaNaoEditavelException(StatusAgendamento statusAtual) {
        super(ErrorCode.PROPOSTA_ESTADO_INVALIDO,
            "Somente propostas em pré-reserva (não assinadas) podem ser alteradas. Status atual: "
                + statusAtual);
    }
}
