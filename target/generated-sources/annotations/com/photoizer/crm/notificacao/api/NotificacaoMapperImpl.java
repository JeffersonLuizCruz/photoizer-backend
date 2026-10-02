package com.photoizer.crm.notificacao.api;

import com.photoizer.crm.notificacao.model.Notificacao;
import com.photoizer.crm.notificacao.model.TipoNotificacao;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-10-01T23:19:42-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 26.0.1 (Homebrew)"
)
@Component
public class NotificacaoMapperImpl implements NotificacaoMapper {

    @Override
    public NotificacaoResponse toResponse(Notificacao notificacao) {
        if ( notificacao == null ) {
            return null;
        }

        UUID id = null;
        String titulo = null;
        String mensagem = null;
        String link = null;
        TipoNotificacao tipo = null;
        boolean lida = false;
        LocalDateTime createdAt = null;

        id = notificacao.getId();
        titulo = notificacao.getTitulo();
        mensagem = notificacao.getMensagem();
        link = notificacao.getLink();
        tipo = notificacao.getTipo();
        lida = notificacao.isLida();
        createdAt = notificacao.getCreatedAt();

        NotificacaoResponse notificacaoResponse = new NotificacaoResponse( id, titulo, mensagem, link, tipo, lida, createdAt );

        return notificacaoResponse;
    }
}
