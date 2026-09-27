package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

public class AgendamentoNaoPermitidoParaPublicacaoException extends UnprocessableException {
    public AgendamentoNaoPermitidoParaPublicacaoException() {
        super(ErrorCode.AGENDAMENTO_NAO_PERMITIDO_PARA_PUBLICACAO,
            "A publicação da galeria está disponível após o registro do pagamento final do ensaio.");
    }
}
