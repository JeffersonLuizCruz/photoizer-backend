package com.photoizer.crm.fotografo.service;

import com.photoizer.crm.agenda.model.AgendamentoFotografo;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.despesa.repository.DespesaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fachada de dados para acesso a repositórios de outros módulos.
 *
 * Design Pattern: Facade Pattern — encapsula dependências cross-module
 * (agenda, despesa, auth) em uma única interface de acesso a dados.
 * Motivo: o módulo fotografo não possui entidade própria e precisa
 * acessar dados de 3 módulos diferentes. Esta facade:
 * 1. Centraliza as dependências em um único ponto
 * 2. Facilita futura migração para Port & Adapter (Hexagonal)
 * 3. Reduz acoplamento entre fotografo e módulos remotos
 * 4. Torna mais fácil a substituição por Application Events quando
 *    o módulo agenda estiver totalmente desacoplado
 */
@Component
public class FotografoDataFacade {

    private final UserRepository userRepository;
    private final AgendamentoFotografoRepository agendamentoFotografoRepository;
    private final DespesaRepository despesaRepository;

    public FotografoDataFacade(UserRepository userRepository,
                               AgendamentoFotografoRepository agendamentoFotografoRepository,
                               DespesaRepository despesaRepository) {
        this.userRepository = userRepository;
        this.agendamentoFotografoRepository = agendamentoFotografoRepository;
        this.despesaRepository = despesaRepository;
    }

    public List<User> findFotografos() {
        return userRepository.findByPapel(Papel.FOTOGRAFO);
    }

    public Optional<User> findFotografoById(UUID id) {
        return userRepository.findById(id);
    }

    public List<User> findUsuariosByPapel(Papel papel) {
        return userRepository.findByPapel(papel);
    }

    public List<AgendamentoFotografo> findLinksByFotografoId(UUID fotografoId) {
        return agendamentoFotografoRepository.findByFotografoIdOrderByAgendamentoDataHoraEnsaioDesc(fotografoId);
    }

    public List<AgendamentoFotografo> findLinksByFotografoIdWithAgendamento(UUID fotografoId) {
        return agendamentoFotografoRepository.findByFotografoIdWithAgendamento(fotografoId);
    }

    public BigDecimal calcularCustosFotografo(UUID agendamentoId, UUID fotografoId) {
        return despesaRepository.sumValorByAgendamentoIdAndFotografoId(agendamentoId, fotografoId);
    }

    public List<Despesa> findDespesasByFotografoId(UUID fotografoId) {
        return despesaRepository.findByFotografoIdOrderByDataDesc(fotografoId);
    }
}
