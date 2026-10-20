package com.photoizer.crm.bloqueio.service;

import com.photoizer.crm.bloqueio.exception.AgendamentoNoPeriodoException;
import com.photoizer.crm.bloqueio.exception.BloqueioNaoEncontradoException;
import com.photoizer.crm.bloqueio.exception.PeriodoInvalidoException;
import com.photoizer.crm.bloqueio.exception.PeriodoJaBloqueadoException;
import com.photoizer.crm.bloqueio.model.BloqueioAgenda;
import com.photoizer.crm.bloqueio.repository.BloqueioAgendaRepository;
import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.ForbiddenException;
import com.photoizer.crm.shared.exception.UnprocessableException;
import com.photoizer.crm.shared.port.AgendamentoOcupacaoPort;
import com.photoizer.crm.shared.port.UsuarioStaffPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BloqueioAgendaService {

    private static final int MAX_DIAS = 90;

    private final BloqueioAgendaRepository bloqueioRepository;
    private final AgendamentoOcupacaoPort agendamentoOcupacaoPort;
    private final UsuarioStaffPort usuarioStaffPort;

    public BloqueioAgendaService(BloqueioAgendaRepository bloqueioRepository,
                                 AgendamentoOcupacaoPort agendamentoOcupacaoPort,
                                 UsuarioStaffPort usuarioStaffPort) {
        this.bloqueioRepository = bloqueioRepository;
        this.agendamentoOcupacaoPort = agendamentoOcupacaoPort;
        this.usuarioStaffPort = usuarioStaffPort;
    }

    @Transactional(readOnly = true)
    public List<BloqueioAgenda> listar(LocalDateTime inicio, LocalDateTime fim, UUID profissionalId) {
        validarPeriodoConsulta(inicio, fim);
        if (profissionalId != null) {
            return bloqueioRepository.findAtivosAplicaveisNoPeriodo(inicio, fim, profissionalId);
        }
        return bloqueioRepository.findAtivosNoPeriodo(inicio, fim);
    }

    @Transactional(readOnly = true)
    public BloqueioAgenda buscarPorId(UUID id) {
        return bloqueioRepository.findByIdAndAtivoTrue(id)
            .orElseThrow(() -> new BloqueioNaoEncontradoException(id));
    }

    public BloqueioAgenda criar(BloqueioAgendaCommand command, UUID criadoPorId) {
        validarPeriodo(command.inicio(), command.fim());
        validarProfissional(command.profissionalId());
        validarSobreposicaoComBloqueio(command, null);
        validarConflitoComAgendamento(command);

        var bloqueio = BloqueioAgenda.builder()
            .titulo(command.titulo().trim())
            .descricao(normalizarDescricao(command.descricao()))
            .inicio(command.inicio())
            .fim(command.fim())
            .profissionalId(command.profissionalId())
            .criadoPorId(criadoPorId)
            .ativo(true)
            .build();

        return bloqueioRepository.save(bloqueio);
    }

    public BloqueioAgenda atualizar(UUID id, BloqueioAgendaCommand command,
                                    UUID solicitanteId, boolean admin) {
        var bloqueio = buscarPorId(id);
        validarPermissao(bloqueio, solicitanteId, admin);
        validarPeriodo(command.inicio(), command.fim());
        validarProfissional(command.profissionalId());
        validarSobreposicaoComBloqueio(command, id);
        validarConflitoComAgendamento(command);

        bloqueio.setTitulo(command.titulo().trim());
        bloqueio.setDescricao(normalizarDescricao(command.descricao()));
        bloqueio.setInicio(command.inicio());
        bloqueio.setFim(command.fim());
        bloqueio.setProfissionalId(command.profissionalId());
        return bloqueioRepository.save(bloqueio);
    }

    public void cancelar(UUID id, UUID solicitanteId, boolean admin) {
        var bloqueio = buscarPorId(id);
        validarPermissao(bloqueio, solicitanteId, admin);
        bloqueio.cancelar(solicitanteId);
        bloqueioRepository.save(bloqueio);
    }

    private void validarPeriodoConsulta(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null || !inicio.isBefore(fim)) {
            throw new PeriodoInvalidoException("Informe um período válido para consulta (início anterior ao fim).");
        }
    }

    private void validarPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null) {
            throw new PeriodoInvalidoException("Início e fim do compromisso são obrigatórios.");
        }
        if (!inicio.isBefore(fim)) {
            throw new PeriodoInvalidoException("O início do compromisso deve ser anterior ao fim.");
        }
        if (Duration.between(inicio, fim).toDays() > MAX_DIAS) {
            throw new PeriodoInvalidoException(
                "O compromisso não pode exceder " + MAX_DIAS + " dias.");
        }
    }

    private void validarProfissional(UUID profissionalId) {
        if (profissionalId != null && !usuarioStaffPort.existeStaffAtivo(profissionalId)) {
            throw new UnprocessableException(
                ErrorCode.ARGUMENTO_INVALIDO, "Profissional informado não existe ou está inativo.");
        }
    }

    private void validarSobreposicaoComBloqueio(BloqueioAgendaCommand command, UUID ignorarId) {
        var sobrepostos = bloqueioRepository.findAtivosAplicaveisNoPeriodo(
                command.inicio(), command.fim(), command.profissionalId())
            .stream()
            .filter(b -> ignorarId == null || !b.getId().equals(ignorarId))
            .toList();

        if (!sobrepostos.isEmpty()) {
            var conflito = sobrepostos.getFirst();
            throw new PeriodoJaBloqueadoException(
                "Já existe um compromisso sobreposto (\"" + conflito.getTitulo() + "\") no período informado.");
        }
    }

    private void validarConflitoComAgendamento(BloqueioAgendaCommand command) {
        var conflitos = agendamentoOcupacaoPort.buscarConflitos(
            command.inicio(), command.fim(), command.profissionalId());

        if (!conflitos.isEmpty()) {
            var conflito = conflitos.getFirst();
            throw new AgendamentoNoPeriodoException(
                "Existe um ensaio agendado no período informado em "
                    + conflito.dataHoraEnsaio() + ". Reagende ou cancele o ensaio antes de criar o compromisso.");
        }
    }

    private void validarPermissao(BloqueioAgenda bloqueio, UUID solicitanteId, boolean admin) {
        if (admin) {
            return;
        }
        if (solicitanteId == null || !solicitanteId.equals(bloqueio.getCriadoPorId())) {
            throw new ForbiddenException(
                ErrorCode.ACESSO_NEGADO, "Apenas o criador do compromisso ou um administrador pode alterá-lo.");
        }
    }

    private String normalizarDescricao(String descricao) {
        return descricao == null || descricao.isBlank() ? null : descricao.trim();
    }
}
