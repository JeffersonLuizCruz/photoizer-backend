package com.photoizer.crm.fotografo.api;

import com.photoizer.crm.auth.api.UserResponse;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-28T23:12:19-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 25.0.4 (Amazon.com Inc.)"
)
@Component
public class FotografoMapperImpl implements FotografoMapper {

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UUID id = null;
        String email = null;
        String nome = null;
        Papel papel = null;
        String telefone = null;
        boolean ativo = false;

        id = user.getId();
        email = user.getEmail();
        nome = user.getNome();
        papel = user.getPapel();
        telefone = user.getTelefone();
        ativo = user.isAtivo();

        UserResponse userResponse = new UserResponse( id, email, nome, papel, telefone, ativo );

        return userResponse;
    }
}
