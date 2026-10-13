package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.event.PagamentoFinalRegistradoEvent;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.shared.model.FormaPagamento;
import com.photoizer.crm.shared.storage.FileStorageService;
import com.photoizer.crm.shared.storage.FileValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regressão: o registro do pagamento final não deve exigir anexo.
 * Sem comprovante, o ensaio transita para EM_EDICAO com urlComprovanteFinal nulo.
 */
class AgendamentoStatusLifecyclePagamentoFinalTest {

    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final AgendamentoFotografoRepository agendamentoFotografoRepository = mock(AgendamentoFotografoRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final FileValidator fileValidator = mock(FileValidator.class);

    private final AgendamentoStatusLifecycle service = new AgendamentoStatusLifecycle(
        agendamentoRepository,
        agendamentoFotografoRepository,
        mock(DisponibilidadeService.class),
        eventPublisher,
        fileStorageService,
        fileValidator);

    @Test
    @DisplayName("registra pagamento final sem comprovante e finaliza para EM_EDICAO")
    void registraSemComprovante() {
        var agendamento = agendamentoEmAguardandoPagamentoFinal();
        stubPersistencia(agendamento);

        var resultado = service.registrarPagamentoFinal(agendamento.getId(), null, FormaPagamento.PIX);

        assertThat(resultado.getStatus()).isEqualTo(StatusAgendamento.EM_EDICAO);
        assertThat(resultado.getUrlComprovanteFinal()).isNull();
        assertThat(resultado.getValorRestante()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resultado.getDataPagamentoFinal()).isNotNull();
        verify(fileStorageService, never()).salvar(any());
        verify(eventPublisher).publishEvent(any(PagamentoFinalRegistradoEvent.class));
    }

    @Test
    @DisplayName("quando há comprovante, salva o arquivo e mantém a URL")
    void registraComComprovante() {
        var agendamento = agendamentoEmAguardandoPagamentoFinal();
        stubPersistencia(agendamento);

        var comprovante = mock(MultipartFile.class);
        when(comprovante.isEmpty()).thenReturn(false);
        when(fileStorageService.salvar(comprovante)).thenReturn("uploads/comprovante.pdf");

        var resultado = service.registrarPagamentoFinal(agendamento.getId(), comprovante, FormaPagamento.PIX);

        assertThat(resultado.getStatus()).isEqualTo(StatusAgendamento.EM_EDICAO);
        assertThat(resultado.getUrlComprovanteFinal()).isEqualTo("uploads/comprovante.pdf");
        verify(fileValidator).validate(comprovante, "any");
    }

    private void stubPersistencia(Agendamento agendamento) {
        when(agendamentoRepository.findByIdWithLock(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(agendamento)).thenReturn(agendamento);
        when(agendamentoFotografoRepository.findByAgendamentoId(agendamento.getId())).thenReturn(List.of());
    }

    private Agendamento agendamentoEmAguardandoPagamentoFinal() {
        return Agendamento.builder()
            .id(UUID.randomUUID())
            .status(StatusAgendamento.AGUARDANDO_PAGAMENTO_FINAL)
            .dataHoraEnsaio(LocalDateTime.now().minusDays(1))
            .duracaoMinutos(60)
            .localEnsaio("Estúdio")
            .valorTotal(new BigDecimal("1000.00"))
            .valorEntradaExigido(new BigDecimal("300.00"))
            .valorEntradaPago(new BigDecimal("300.00"))
            .valorRestante(new BigDecimal("700.00"))
            .valorTotalFinal(new BigDecimal("1000.00"))
            .build();
    }
}
