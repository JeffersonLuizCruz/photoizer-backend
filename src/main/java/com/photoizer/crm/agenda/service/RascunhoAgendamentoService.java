package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.model.RascunhoAgendamento;
import com.photoizer.crm.agenda.repository.RascunhoAgendamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class RascunhoAgendamentoService {

    private final RascunhoAgendamentoRepository repository;

    public RascunhoAgendamentoService(RascunhoAgendamentoRepository repository) {
        this.repository = repository;
    }

    public RascunhoAgendamento salvarRascunho(
        UUID usuarioId,
        String clienteId,
        String nome,
        String telefone,
        String email,
        String cpf,
        String cidade,
        String estado,
        String origem,
        String pacoteId,
        String data,
        String hora,
        String localEnsaio,
        String enderecoCompleto,
        String editorId,
        BigDecimal custoDeslocamento,
        Boolean repassarDeslocamento,
        Boolean autorizaUsoImagem,
        String indicadorId,
        String indicadorNome,
        String indicadorTelefone,
        String observacoes,
        Integer currentStep,
        String comprovanteName,
        Boolean confirmado
    ) {
        var draft = repository.findByUsuarioId(usuarioId)
            .orElseGet(() -> RascunhoAgendamento.builder()
                .usuarioId(usuarioId)
                .build()
            );

        draft.setClienteId(clienteId);
        draft.setNome(nome);
        draft.setTelefone(telefone);
        draft.setEmail(email);
        draft.setCpf(cpf);
        draft.setCidade(cidade);
        draft.setEstado(estado);
        draft.setOrigem(origem);
        draft.setPacoteId(pacoteId);
        draft.setData(data != null ? LocalDate.parse(data) : null);
        draft.setHora(hora);
        draft.setLocalEnsaio(localEnsaio);
        draft.setEnderecoCompleto(enderecoCompleto);
        draft.setEditorId(editorId);
        draft.setCustoDeslocamento(custoDeslocamento);
        draft.setRepassarDeslocamento(repassarDeslocamento);
        draft.setAutorizaUsoImagem(autorizaUsoImagem);
        draft.setIndicadorId(indicadorId);
        draft.setIndicadorNome(indicadorNome);
        draft.setIndicadorTelefone(indicadorTelefone);
        draft.setObservacoes(observacoes);
        draft.setCurrentStep(currentStep != null ? currentStep : 0);
        draft.setComprovanteName(comprovanteName);
        draft.setConfirmado(confirmado != null ? confirmado : false);

        return repository.save(draft);
    }

    @Transactional(readOnly = true)
    public Optional<RascunhoAgendamento> buscarPorUsuario(UUID usuarioId) {
        return repository.findByUsuarioId(usuarioId);
    }

    public void deletarPorUsuario(UUID usuarioId) {
        repository.deleteByUsuarioId(usuarioId);
    }
}
