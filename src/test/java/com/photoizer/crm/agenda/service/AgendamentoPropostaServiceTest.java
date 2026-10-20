package com.photoizer.crm.agenda.service;

import com.photoizer.crm.agenda.api.AgendamentoMapper;
import com.photoizer.crm.agenda.event.AgendamentoCanceladoEvent;
import com.photoizer.crm.agenda.exception.PropostaNaoEditavelException;
import com.photoizer.crm.agenda.exception.PropostaNaoRemovivelException;
import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.agenda.repository.AgendamentoFotografoRepository;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.agenda.repository.ReatribuicaoAgendamentoRepository;
import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.cliente.repository.ClienteRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.pacote.service.PacoteQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regras de alteração e remoção de proposta/pré-reserva:
 * edição apenas antes da assinatura e remoção como soft-cancel auditável.
 */
class AgendamentoPropostaServiceTest {

    private final ClienteRepository clienteRepository = mock(ClienteRepository.class);
    private final PacoteQueryService pacoteQueryService = mock(PacoteQueryService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final FotoEnsaioRepository fotoEnsaioRepository = mock(FotoEnsaioRepository.class);
    private final ConfiguracaoService configuracaoService = mock(ConfiguracaoService.class);
    private final AgendamentoFotografoRepository agendamentoFotografoRepository = mock(AgendamentoFotografoRepository.class);
    private final ReatribuicaoAgendamentoRepository reatribuicaoAgendamentoRepository = mock(ReatribuicaoAgendamentoRepository.class);
    private final DisponibilidadeService disponibilidadeService = mock(DisponibilidadeService.class);
    private final PartilhaService partilhaService = mock(PartilhaService.class);
    private final AgendamentoMapper agendamentoMapper = mock(AgendamentoMapper.class);

    private final AgendamentoService service = new AgendamentoService(
        clienteRepository,
        pacoteQueryService,
        userRepository,
        agendamentoRepository,
        eventPublisher,
        fotoEnsaioRepository,
        configuracaoService,
        agendamentoFotografoRepository,
        reatribuicaoAgendamentoRepository,
        disponibilidadeService,
        partilhaService,
        new AgendamentoValoresCalculator(),
        agendamentoMapper);

    @Test
    @DisplayName("altera proposta em PRE_RESERVA recalculando valores e removendo token do link")
    void alteraPropostaEmPreReserva() {
        var agendamento = agendamento(StatusAgendamento.PRE_RESERVA);
        var tokenOriginal = agendamento.getTokenProposta();
        var pacote = pacote(new BigDecimal("2000.00"));
        var fotografo = new User("foto@test.com", "hash", "Foto", Papel.FOTOGRAFO);

        when(agendamentoRepository.findByIdWithLock(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pacoteQueryService.buscarEntityPorId(pacote.getId())).thenReturn(pacote);
        when(userRepository.findById(fotografo.getId())).thenReturn(Optional.of(fotografo));
        when(configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO)).thenReturn(new BigDecimal("100.00"));
        when(configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA)).thenReturn(new BigDecimal("30.00"));
        when(agendamentoFotografoRepository.findByAgendamentoIdWithFotografo(agendamento.getId())).thenReturn(List.of());

        var command = new CriarPropostaCommand(
            pacote.getId(), null, fotografo.getId(), LocalDateTime.now().plusDays(5),
            120, "Praia", null, false, "Cláusula X", "Obs Y",
            null, null, null, List.of());

        service.atualizarProposta(agendamento.getId(), command);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.PRE_RESERVA);
        assertThat(agendamento.getValorTotal()).isEqualByComparingTo("2000.00");
        assertThat(agendamento.getValorEntradaExigido()).isEqualByComparingTo("600.00");
        assertThat(agendamento.getDuracaoMinutos()).isEqualTo(120);
        assertThat(agendamento.getClausulasPersonalizadas()).isEqualTo("Cláusula X");
        assertThat(agendamento.getLocalEnsaio()).isEqualTo("Praia");
        assertThat(agendamento.getTokenProposta()).isEqualTo(tokenOriginal);
        verify(partilhaService).calcularPartilhaFotografo(agendamento);
        verify(agendamentoRepository).save(agendamento);
    }

    @Test
    @DisplayName("cria proposta com pacote de R$ 0,01 usando entrada mínima")
    void criaPropostaComPacoteDeUmCentavo() {
        var pacote = pacote(new BigDecimal("0.01"));
        var fotografo = new User("foto@test.com", "hash", "Foto", Papel.FOTOGRAFO);

        when(pacoteQueryService.buscarEntityPorId(pacote.getId())).thenReturn(pacote);
        when(userRepository.findById(fotografo.getId())).thenReturn(Optional.of(fotografo));
        when(configuracaoService.getValorDecimal(ConfigKey.TAXA_DESLOCAMENTO)).thenReturn(new BigDecimal("0.00"));
        when(configuracaoService.getValorDecimal(ConfigKey.PERCENTUAL_ENTRADA)).thenReturn(new BigDecimal("30.00"));
        when(configuracaoService.getValorInteiro(ConfigKey.CONTRATO_DIAS_VALIDADE)).thenReturn(7);
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var command = new CriarPropostaCommand(
            pacote.getId(), null, fotografo.getId(), LocalDateTime.now().plusDays(5),
            360, "Estúdio", null, true, null, null,
            null, null, null, List.of());

        var agendamento = service.criarProposta(command);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.PRE_RESERVA);
        assertThat(agendamento.getValorTotal()).isEqualByComparingTo("0.01");
        assertThat(agendamento.getValorEntradaExigido()).isEqualByComparingTo("0.01");
        assertThat(agendamento.getValorEntradaPago()).isEqualByComparingTo("0.00");
        assertThat(agendamento.getValorRestante()).isEqualByComparingTo("0.01");
        assertThat(agendamento.getTokenProposta()).isNotBlank();
    }

    @Test
    @DisplayName("bloqueia alteração de proposta já assinada")
    void bloqueiaAlteracaoAposAssinatura() {
        var agendamento = agendamento(StatusAgendamento.AGUARDANDO_APROVACAO);
        when(agendamentoRepository.findByIdWithLock(agendamento.getId())).thenReturn(Optional.of(agendamento));

        var command = new CriarPropostaCommand(
            UUID.randomUUID(), null, UUID.randomUUID(), LocalDateTime.now().plusDays(5),
            60, "Estúdio", null, true, null, null, null, null, null, List.of());

        assertThatThrownBy(() -> service.atualizarProposta(agendamento.getId(), command))
            .isInstanceOf(PropostaNaoEditavelException.class);

        verifyNoInteractions(pacoteQueryService);
    }

    @Test
    @DisplayName("remove proposta em análise com soft-cancel auditável e publica evento")
    void removePropostaEmAnalise() {
        var agendamento = agendamento(StatusAgendamento.PRE_RESERVA);
        when(agendamentoRepository.findByIdWithLock(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.removerProposta(agendamento.getId(), "Duplicada", null);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agendamento.getMotivoRecusa()).isEqualTo("Duplicada");
        assertThat(agendamento.getDataRecusa()).isNotNull();
        assertThat(agendamento.getRecusadoPor()).isNull();
        verify(eventPublisher).publishEvent(any(AgendamentoCanceladoEvent.class));
    }

    @Test
    @DisplayName("usa motivo padrão e registra autor da remoção")
    void removePropostaComMotivoPadraoEAutor() {
        var agendamento = agendamento(StatusAgendamento.PRE_RESERVA);
        var autor = new User("admin@test.com", "hash", "Admin", Papel.ADMIN);
        var autorId = UUID.randomUUID();
        when(agendamentoRepository.findByIdWithLock(agendamento.getId())).thenReturn(Optional.of(agendamento));
        when(agendamentoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(autorId)).thenReturn(Optional.of(autor));

        service.removerProposta(agendamento.getId(), "  ", autorId);

        assertThat(agendamento.getStatus()).isEqualTo(StatusAgendamento.CANCELADO);
        assertThat(agendamento.getMotivoRecusa()).isEqualTo("Proposta removida pelo staff");
        assertThat(agendamento.getRecusadoPor()).isEqualTo("Admin");
    }

    @Test
    @DisplayName("bloqueia remoção de agendamento que não é proposta")
    void bloqueiaRemocaoForaDaPreReserva() {
        var agendamento = agendamento(StatusAgendamento.CONFIRMADO);
        when(agendamentoRepository.findByIdWithLock(agendamento.getId())).thenReturn(Optional.of(agendamento));

        assertThatThrownBy(() -> service.removerProposta(agendamento.getId(), "x", null))
            .isInstanceOf(PropostaNaoRemovivelException.class);

        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
    }

    private Pacote pacote(BigDecimal valorBase) {
        return Pacote.builder()
            .id(UUID.randomUUID())
            .nome("Pacote")
            .valorBase(valorBase)
            .ativo(true)
            .build();
    }

    private Agendamento agendamento(StatusAgendamento status) {
        return Agendamento.builder()
            .id(UUID.randomUUID())
            .status(status)
            .dataHoraEnsaio(LocalDateTime.now().plusDays(10))
            .duracaoMinutos(60)
            .localEnsaio("Estúdio")
            .valorTotal(new BigDecimal("1000.00"))
            .valorEntradaExigido(new BigDecimal("300.00"))
            .valorEntradaPago(BigDecimal.ZERO)
            .valorRestante(new BigDecimal("1000.00"))
            .valorExtras(BigDecimal.ZERO)
            .taxaDeslocamento(BigDecimal.ZERO)
            .valorTotalFinal(new BigDecimal("1000.00"))
            .percentualEntrada(new BigDecimal("30.00"))
            .autorizaUsoImagem(false)
            .ensaioDestaque(false)
            .tokenProposta("token-abc")
            .tokenGaleria(UUID.randomUUID())
            .build();
    }
}
