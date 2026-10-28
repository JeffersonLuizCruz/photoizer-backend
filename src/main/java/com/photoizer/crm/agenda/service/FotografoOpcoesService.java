package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.api.OpcoesFotografoResponse;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Resolve a lista de fotógrafos disponíveis e o responsável padrão dos novos
 * agendamentos. O padrão é o próprio usuário logado quando ele é fotógrafo
 * ativo; caso contrário, o fotógrafo provisionado a partir de Configurações
 * (Nome do Fotógrafo). Sem nenhum dos dois, não há padrão (seleção manual).
 */
@Service
@Transactional(readOnly = true)
public class FotografoOpcoesService {

    private final UserRepository userRepository;

    public FotografoOpcoesService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public OpcoesFotografoResponse opcoes(UUID usuarioLogadoId) {
        return new OpcoesFotografoResponse(listarAtivos(), resolverPadrao(usuarioLogadoId));
    }

    public List<OpcoesFotografoResponse.FotografoOpcao> listarAtivos() {
        return userRepository.findByPapel(Papel.FOTOGRAFO).stream()
            .filter(User::isAtivo)
            .sorted(Comparator.comparing(User::getNome, String.CASE_INSENSITIVE_ORDER))
            .map(u -> new OpcoesFotografoResponse.FotografoOpcao(u.getId(), u.getNome()))
            .toList();
    }

    public UUID resolverPadrao(UUID usuarioLogadoId) {
        if (usuarioLogadoId != null) {
            var logado = userRepository.findById(usuarioLogadoId).orElse(null);
            if (logado != null && logado.isAtivo() && logado.getPapel() == Papel.FOTOGRAFO) {
                return logado.getId();
            }
        }
        return userRepository.findByEmail(User.EMAIL_FOTOGRAFO_CONFIGURADO)
            .filter(User::isAtivo)
            .filter(u -> u.getPapel() == Papel.FOTOGRAFO)
            .map(User::getId)
            .orElse(null);
    }
}
