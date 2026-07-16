package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.exception.AgendamentoNaoEncontradoException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusTarefa;
import com.photoizer.crm.agenda.model.Tarefa;
import com.photoizer.crm.agenda.model.TipoTarefa;
import com.photoizer.crm.agenda.model.Usuario;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.TarefaRepository;
import com.photoizer.crm.agenda.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TarefaService {

    private final TarefaRepository tarefaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;

    public TarefaService(TarefaRepository tarefaRepository,
                         AgendamentoRepository agendamentoRepository,
                         UsuarioRepository usuarioRepository) {
        this.tarefaRepository = tarefaRepository;
        this.agendamentoRepository = agendamentoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<Tarefa> listar(UUID agendamentoId) {
        if (agendamentoId != null) {
            return tarefaRepository.findByAgendamentoIdOrderByDataLimiteAsc(agendamentoId);
        }
        return tarefaRepository.findAll();
    }

    public Tarefa criar(UUID agendamentoId, String tipo, UUID responsavelId, LocalDateTime dataLimite) {
        var agendamento = agendamentoRepository.findById(agendamentoId)
            .orElseThrow(() -> new AgendamentoNaoEncontradoException(agendamentoId));

        TipoTarefa tipoTarefa;
        try {
            tipoTarefa = TipoTarefa.valueOf(tipo);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de tarefa inválido: " + tipo);
        }

        Usuario responsavel = null;
        if (responsavelId != null) {
            responsavel = usuarioRepository.findById(responsavelId).orElse(null);
        }

        var tarefa = Tarefa.builder()
            .agendamento(agendamento)
            .tipo(tipoTarefa)
            .responsavel(responsavel)
            .dataLimite(dataLimite)
            .status(StatusTarefa.PENDENTE)
            .build();

        return tarefaRepository.save(tarefa);
    }

    public Tarefa atualizarStatus(UUID id, String status) {
        var tarefa = tarefaRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Tarefa não encontrada: " + id));

        StatusTarefa novoStatus;
        try {
            novoStatus = StatusTarefa.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status de tarefa inválido: " + status);
        }

        tarefa.setStatus(novoStatus);

        if (novoStatus == StatusTarefa.CONCLUIDA) {
            tarefa.setDataConclusao(LocalDateTime.now());
        }

        return tarefaRepository.save(tarefa);
    }
}
