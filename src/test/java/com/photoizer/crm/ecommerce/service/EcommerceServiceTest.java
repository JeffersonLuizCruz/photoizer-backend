package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.config.service.ConfiguracaoService;
import com.photoizer.crm.ecommerce.model.CompraExtra;
import com.photoizer.crm.ecommerce.model.ItemCarrinho;
import com.photoizer.crm.ecommerce.model.StatusCompraExtra;
import com.photoizer.crm.ecommerce.repository.CompraExtraRepository;
import com.photoizer.crm.ecommerce.repository.FavoritoRepository;
import com.photoizer.crm.ecommerce.repository.ItemCarrinhoRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.model.StatusFoto;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.shared.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do EcommerceService (orquestrador).
 * Métodos de seleção, carrinho, download e pagamento foram migrados para seus
 * services especializados (SelecaoFotosService, CarrinhoService, DownloadService,
 * PagamentoExtraService, CompraQueryService).
 */
class EcommerceServiceTest {

    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final FotoEnsaioRepository fotoEnsaioRepository = mock(FotoEnsaioRepository.class);
    private final CompraExtraRepository compraExtraRepository = mock(CompraExtraRepository.class);
    private final ItemCarrinhoRepository itemCarrinhoRepository = mock(ItemCarrinhoRepository.class);
    private final FavoritoRepository favoritoRepository = mock(FavoritoRepository.class);
    private final ConfiguracaoService configuracaoService = mock(ConfiguracaoService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private GaleriaQueryService galeriaQueryService;
    private CarrinhoService carrinhoService;
    private SelecaoFotosService selecaoFotosService;
    private EcommerceService service;
    private UUID agendamentoId;
    private UUID token;

    @BeforeEach
    void setUp() {
        galeriaQueryService = new GaleriaQueryService(
            agendamentoRepository, fotoEnsaioRepository, configuracaoService);
        carrinhoService = new CarrinhoService(itemCarrinhoRepository, galeriaQueryService);
        selecaoFotosService = new SelecaoFotosService(fotoEnsaioRepository, galeriaQueryService, eventPublisher);
        service = new EcommerceService(
            fotoEnsaioRepository, compraExtraRepository, fileStorageService,
            eventPublisher, galeriaQueryService, carrinhoService, selecaoFotosService);
        agendamentoId = UUID.randomUUID();
        token = UUID.randomUUID();
    }

    private void mockAgendamento(Pacote pacote) {
        when(agendamentoRepository.findByTokenGaleria(token))
            .thenReturn(Optional.of(Agendamento.builder().id(agendamentoId).pacote(pacote).build()));
    }

    // ==================== Galeria (delegado) ====================

    @Test
    void getValorUnitarioFotoExtra_usaPrecoDoPacote() {
        var pacote = Pacote.builder().precoFotoExtra(new BigDecimal("12.00")).build();
        when(agendamentoRepository.findById(agendamentoId))
            .thenReturn(Optional.of(Agendamento.builder().id(agendamentoId).pacote(pacote).build()));

        assertEquals(new BigDecimal("12.00"), service.getValorUnitarioFotoExtra(agendamentoId));
    }

    @Test
    void getValorUnitarioFotoExtra_caiParaConfigQuandoPacoteSemPreco() {
        when(agendamentoRepository.findById(agendamentoId))
            .thenReturn(Optional.of(Agendamento.builder().id(agendamentoId)
                .pacote(Pacote.builder().precoFotoExtra(BigDecimal.ZERO).build()).build()));
        when(configuracaoService.getValorDecimal(ConfigKey.VALOR_FOTO_EXTRA))
            .thenReturn(new BigDecimal("15.00"));

        assertEquals(new BigDecimal("15.00"), service.getValorUnitarioFotoExtra(agendamentoId));
    }

    // ==================== Checkout ====================

    @Test
    void checkout_rejeitaCarrinhoVazio() {
        mockAgendamento(Pacote.builder().build());
        var sessionId = UUID.randomUUID();

        when(itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamentoId))
            .thenReturn(List.of());

        assertThrows(com.photoizer.crm.ecommerce.exception.CarrinhoVazioException.class,
            () -> service.checkout(token, sessionId, null));
    }

    @Test
    void checkout_rejeitaFotoDePacoteNoCarrinho() {
        mockAgendamento(Pacote.builder().build());
        var sessionId = UUID.randomUUID();
        var fotoPacote = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(true)
            .visivel(true)
            .build();
        var item = ItemCarrinho.builder()
            .agendamentoId(agendamentoId)
            .fotoId(fotoPacote.getId())
            .sessionId(sessionId)
            .build();

        when(itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamentoId))
            .thenReturn(List.of(item));
        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(fotoPacote));

        assertThrows(com.photoizer.crm.ecommerce.exception.FotoIndisponivelException.class,
            () -> service.checkout(token, sessionId, null));
    }

    @Test
    void checkout_criaCompraExtraComValoresCorretos() {
        mockAgendamento(Pacote.builder().quantidadeFotos(5).precoFotoExtra(new BigDecimal("10.00")).build());
        var sessionId = UUID.randomUUID();
        var fotoId = UUID.randomUUID();
        var foto = FotoEnsaio.builder()
            .id(fotoId)
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(false)
            .visivel(true)
            .build();
        var item = ItemCarrinho.builder()
            .agendamentoId(agendamentoId)
            .fotoId(fotoId)
            .sessionId(sessionId)
            .build();

        when(itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByAuditInfoCreatedAtAsc(sessionId, agendamentoId))
            .thenReturn(List.of(item));
        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(foto));
        when(compraExtraRepository.save(any(CompraExtra.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var compra = service.checkout(token, sessionId, null);

        assertNotNull(compra);
        assertEquals(new BigDecimal("10.00"), compra.getValorTotal());
        assertEquals(1, compra.getQuantidadeFotos());
        assertEquals(StatusCompraExtra.AGUARDANDO_COMPROVANTE, compra.getStatus());
        verify(eventPublisher).publishEvent(any());
    }

    // ==================== Upload Comprovante ====================

    @Test
    void uploadComprovante_rejeitaCompraDeOutroAgendamento() {
        mockAgendamento(Pacote.builder().build());
        var compra = CompraExtra.builder()
            .id(UUID.randomUUID())
            .agendamentoId(UUID.randomUUID())
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build();

        when(compraExtraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));

        assertThrows(com.photoizer.crm.ecommerce.exception.CompraNaoEncontradaException.class,
            () -> service.uploadComprovante(token, compra.getId(), null));
    }

    // ==================== Selecao (delegado para SelecaoFotosService) ====================

    @Test
    void selecionarFotos_delegaParaSelecaoFotosService() {
        mockAgendamento(Pacote.builder().quantidadeFotos(2).build());
        var nova = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(false)
            .visivel(true)
            .build();

        when(fotoEnsaioRepository.findAllById(List.of(nova.getId()))).thenReturn(List.of(nova));
        when(fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(agendamentoId)).thenReturn(0);
        when(fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId)).thenReturn(List.of(nova));

        var resultado = service.selecionarFotos(token, List.of(nova.getId()), true);

        assertNotNull(resultado);
        verify(eventPublisher).publishEvent(any());
    }
}
