package com.photoizer.crm.fotografo.service;

import com.photoizer.crm.auth.api.UserResponse;
import com.photoizer.crm.auth.service.UserService;
import com.photoizer.crm.despesa.model.Despesa;
import com.photoizer.crm.fotografo.api.AtualizarFotografoRequest;
import com.photoizer.crm.fotografo.api.CriarFotografoRequest;
import com.photoizer.crm.fotografo.api.FotografoMapper;
import com.photoizer.crm.agenda.exception.FotografoNaoEncontradoException;
import com.photoizer.crm.fotografo.exception.FotografoComEnsaiosVinculadosException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service de CRUD para fotógrafos.
 *
 * Design Pattern: Facade + Delegation — delega operações de mutação
 * de User para o UserService do módulo auth (Single Source of Truth),
 * mantendo neste módulo apenas a lógica específica de fotógrafos.
 *
 * Separado de FotografoQueryService (SRP): este service cuida de
 * criação/atualização/remoção; o QueryService cuida de relatórios.
 */
@Service
public class FotografoService {

    private final FotografoDataFacade dataFacade;
    private final UserService userService;
    private final FotografoMapper fotografoMapper;

    public FotografoService(FotografoDataFacade dataFacade, UserService userService, FotografoMapper fotografoMapper) {
        this.dataFacade = dataFacade;
        this.userService = userService;
        this.fotografoMapper = fotografoMapper;
    }

    public List<UserResponse> listarFotografos() {
        return dataFacade.findFotografos().stream()
            .map(fotografoMapper::toResponse)
            .toList();
    }

    public UserResponse buscarPorId(UUID id) {
        return dataFacade.findFotografoById(id)
            .map(fotografoMapper::toResponse)
            .orElseThrow(() -> new FotografoNaoEncontradoException(id));
    }

    public List<UserResponse> listarParceiros() {
        var papéis = List.of(
            com.photoizer.crm.auth.model.Papel.FOTOGRAFO,
            com.photoizer.crm.auth.model.Papel.EDITOR,
            com.photoizer.crm.auth.model.Papel.AGENDADOR
        );
        return papéis.stream()
            .map(dataFacade::findUsuariosByPapel)
            .flatMap(List::stream)
            .distinct()
            .map(fotografoMapper::toResponse)
            .toList();
    }

    @Transactional
    public UserResponse criar(CriarFotografoRequest request) {
        return userService.criarFotografo(
            request.email(), request.senha(), request.nome(), request.telefone());
    }

    @Transactional
    public UserResponse atualizar(UUID id, AtualizarFotografoRequest request) {
        return userService.atualizarFotografo(id, request.nome(), request.email(), request.telefone());
    }

    @Transactional
    public void toggleStatus(UUID id) {
        userService.toggleStatus(id);
    }

    @Transactional
    public void remover(UUID id) {
        dataFacade.findFotografoById(id)
            .orElseThrow(() -> new FotografoNaoEncontradoException(id));

        var links = dataFacade.findLinksByFotografoId(id);
        if (!links.isEmpty()) {
            throw new FotografoComEnsaiosVinculadosException(links.size());
        }
        userService.remover(id);
    }

    public List<Despesa> listarCustos(UUID fotografoId) {
        return dataFacade.findDespesasByFotografoId(fotografoId);
    }
}
