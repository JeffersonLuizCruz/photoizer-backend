package com.photoizer.crm.ecommerce.security;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.ecommerce.exception.FotoIndisponivelException;
import com.photoizer.crm.ecommerce.exception.LimitePacoteExcedidoException;
import com.photoizer.crm.ecommerce.service.CarrinhoService;
import com.photoizer.crm.ecommerce.service.GaleriaQueryService;
import com.photoizer.crm.ecommerce.service.SelecaoFotosService;
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.pacote.model.Pacote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes de segurança da regra de negócio da loja:
 * - C2: limite do pacote imposto no servidor (selecionarFotos).
 * - C1: download permitido somente para foto do pacote ou PAGA.
 * - M3: preço por pacote tem precedência sobre config global.
 * - M4: carrinho rejeita fotos de outra galeria/ocultas/não publicadas.
 */
@ExtendWith(MockitoExtension.class)
class RegrasSegurancaLojaTest {

    @Mock
    private FotoEnsaioRepository fotoEnsaioRepository;
    @Mock
    private GaleriaQueryService galeriaQueryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ItemCarrinhoRepository itemCarrinhoRepository;
    @Mock
    private AgendamentoRepository agendamentoRepository;
    @Mock
    private ConfiguracaoService configuracaoService;

    @InjectMocks
    private SelecaoFotosService selecaoFotosService;
    @InjectMocks
    private CarrinhoService carrinhoService;

    private UUID token;
    private UUID agendamentoId;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID();
        agendamentoId = UUID.randomUUID();
    }

    private Agendamento agendamentoComLimite(int quantidadeFotos) {
        var pacote = Pacote.builder()
            .quantidadeFotos(quantidadeFotos)
            .precoFotoExtra(new BigDecimal("20.00"))
            .build();
        return Agendamento.builder()
            .id(agendamentoId)
            .pacote(pacote)
            .build();
    }

    private FotoEnsaio foto(UUID id, boolean selecionada) {
        return FotoEnsaio.builder()
            .id(id)
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(selecionada)
            .visivel(true)
            .build();
    }

    @Test
    void selecionarFotos_rejeitaQuandoExcedeLimiteDoPacote() {
        var agendamento = agendamentoComLimite(2);
        when(galeriaQueryService.buscarAgendamentoPorToken(token)).thenReturn(agendamento);
        when(fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(agendamentoId)).thenReturn(1);

        var fotoA = foto(UUID.randomUUID(), false);
        var fotoB = foto(UUID.randomUUID(), false);
        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(fotoA, fotoB));

        assertThatThrownBy(() -> selecaoFotosService.selecionarFotos(token, List.of(fotoA.getId(), fotoB.getId()), true))
            .isInstanceOf(LimitePacoteExcedidoException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void selecionarFotos_permiteDentroDoLimite() {
        var agendamento = agendamentoComLimite(3);
        when(galeriaQueryService.buscarAgendamentoPorToken(token)).thenReturn(agendamento);
        when(fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(agendamentoId)).thenReturn(1);

        var fotoA = foto(UUID.randomUUID(), false);
        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(fotoA));
        when(fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId)).thenReturn(List.of(fotoA));

        selecaoFotosService.selecionarFotos(token, List.of(fotoA.getId()), true);

        verify(eventPublisher).publishEvent(any(com.photoizer.crm.ecommerce.event.FotosSelecionadasEvent.class));
    }

    @Test
    void isDownloadPermitido_liberaApenasPacoteOuPaga() {
        var doPacote = foto(UUID.randomUUID(), true);
        var paga = foto(UUID.randomUUID(), false);
        paga.setStatus(StatusFoto.PAGA);
        var publicada = foto(UUID.randomUUID(), false);

        var service = new GaleriaQueryService(agendamentoRepository, fotoEnsaioRepository, configuracaoService);

        assertThat(service.isDownloadPermitido(doPacote)).isTrue();
        assertThat(service.isDownloadPermitido(paga)).isTrue();
        assertThat(service.isDownloadPermitido(publicada)).isFalse();
    }

    @Test
    void getValorUnitarioFotoExtra_usaPrecoDoPacoteQuandoPositivo() {
        var agendamento = agendamentoComLimite(2);
        when(agendamentoRepository.findById(agendamentoId)).thenReturn(Optional.of(agendamento));

        var service = new GaleriaQueryService(agendamentoRepository, fotoEnsaioRepository, configuracaoService);

        assertThat(service.getValorUnitarioFotoExtra(agendamentoId))
            .isEqualByComparingTo(new BigDecimal("20.00"));
        verify(configuracaoService, never()).getValorDecimal(any());
    }

    @Test
    void adicionarAoCarrinho_rejeitaFotoDeOutraGaleria() {
        var agendamento = agendamentoComLimite(2);
        when(galeriaQueryService.buscarAgendamentoPorToken(token)).thenReturn(agendamento);

        var fotoOutraGaleria = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(UUID.randomUUID())
            .status(StatusFoto.PUBLICADA)
            .visivel(true)
            .build();
        when(galeriaQueryService.buscarFotoPorId(fotoOutraGaleria.getId())).thenReturn(fotoOutraGaleria);

        assertThatThrownBy(() -> carrinhoService.adicionarAoCarrinho(token, UUID.randomUUID(), fotoOutraGaleria.getId()))
            .isInstanceOf(FotoIndisponivelException.class);

        verify(itemCarrinhoRepository, never()).save(any());
    }

    @Test
    void adicionarAoCarrinho_rejeitaFotoOcultaOuNaoPublicada() {
        var agendamento = agendamentoComLimite(2);
        when(galeriaQueryService.buscarAgendamentoPorToken(token)).thenReturn(agendamento);

        var oculta = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .visivel(false)
            .build();
        when(galeriaQueryService.buscarFotoPorId(oculta.getId())).thenReturn(oculta);

        assertThatThrownBy(() -> carrinhoService.adicionarAoCarrinho(token, UUID.randomUUID(), oculta.getId()))
            .isInstanceOf(FotoIndisponivelException.class);
    }

    @Test
    void adicionarAoCarrinho_rejeitaFotoJaNoPacote() {
        var agendamento = agendamentoComLimite(2);
        when(galeriaQueryService.buscarAgendamentoPorToken(token)).thenReturn(agendamento);

        var doPacote = foto(UUID.randomUUID(), true);
        when(galeriaQueryService.buscarFotoPorId(doPacote.getId())).thenReturn(doPacote);

        assertThatThrownBy(() -> carrinhoService.adicionarAoCarrinho(token, UUID.randomUUID(), doPacote.getId()))
            .isInstanceOf(FotoIndisponivelException.class);

        verify(itemCarrinhoRepository, never()).save(any());
    }

    @Test
    void buscarAgendamentoPorToken_rejeitaTokenExpirado() {
        var expirado = Agendamento.builder()
            .id(agendamentoId)
            .pacote(Pacote.builder().quantidadeFotos(2).precoFotoExtra(new BigDecimal("20.00")).build())
            .tokenGaleria(token)
            .tokenExpiracao(LocalDateTime.now().minusDays(1))
            .build();
        when(agendamentoRepository.findByTokenGaleria(token)).thenReturn(Optional.of(expirado));

        var service = new GaleriaQueryService(agendamentoRepository, fotoEnsaioRepository, configuracaoService);

        assertThatThrownBy(() -> service.buscarAgendamentoPorToken(token))
            .isInstanceOf(com.photoizer.crm.ecommerce.exception.TokenExpiradoException.class);
    }
}
