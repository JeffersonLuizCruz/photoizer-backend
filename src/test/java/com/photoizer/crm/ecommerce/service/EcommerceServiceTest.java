package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.Agendamento;
import com.photoizer.crm.agenda.repository.AgendamentoRepository;
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
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EcommerceServiceTest {

    private final AgendamentoRepository agendamentoRepository = mock(AgendamentoRepository.class);
    private final FotoEnsaioRepository fotoEnsaioRepository = mock(FotoEnsaioRepository.class);
    private final CompraExtraRepository compraExtraRepository = mock(CompraExtraRepository.class);
    private final ItemCarrinhoRepository itemCarrinhoRepository = mock(ItemCarrinhoRepository.class);
    private final FavoritoRepository favoritoRepository = mock(FavoritoRepository.class);
    private final ConfiguracaoService configuracaoService = mock(ConfiguracaoService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private EcommerceService service;
    private UUID agendamentoId;
    private UUID token;

    @BeforeEach
    void setUp() {
        service = new EcommerceService(
            agendamentoRepository, fotoEnsaioRepository, compraExtraRepository,
            itemCarrinhoRepository, favoritoRepository, configuracaoService,
            fileStorageService, eventPublisher);
        agendamentoId = UUID.randomUUID();
        token = UUID.randomUUID();
    }

    private void mockAgendamento(Pacote pacote) {
        when(agendamentoRepository.findByTokenGaleria(token))
            .thenReturn(Optional.of(Agendamento.builder().id(agendamentoId).pacote(pacote).build()));
    }

    private FotoEnsaio foto(boolean selecionadaPacote, boolean visivel) {
        return FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(selecionadaPacote)
            .visivel(visivel)
            .build();
    }

    @Test
    void selecionarFotos_rejeitaQuandoExcedeLimiteDoPacote() {
        mockAgendamento(Pacote.builder().quantidadeFotos(2).build());
        var nova = foto(false, true);

        when(fotoEnsaioRepository.findAllById(List.of(nova.getId()))).thenReturn(List.of(nova));
        when(fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(agendamentoId)).thenReturn(2);

        assertThrows(IllegalArgumentException.class,
            () -> service.selecionarFotos(token, List.of(nova.getId()), true));
    }

    @Test
    void selecionarFotos_permiteQuandoDentroDoLimite() {
        mockAgendamento(Pacote.builder().quantidadeFotos(2).build());
        var nova = foto(false, true);

        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(nova));
        when(fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(agendamentoId)).thenReturn(1);
        when(fotoEnsaioRepository.saveAll(anyList())).thenReturn(List.of(nova));

        var resultado = service.selecionarFotos(token, List.of(nova.getId()), true);

        assertTrue(resultado.get(0).isSelecionadaPacote());
    }

    @Test
    void getValorUnitarioFotoExtra_usaPrecoDoPacote() {
        var pacote = Pacote.builder().precoFotoExtra(new BigDecimal("12.00")).build();
        when(agendamentoRepository.findById(agendamentoId))
            .thenReturn(Optional.of(Agendamento.builder().id(agendamentoId).pacote(pacote).build()));

        assertEquals(new BigDecimal("12.00"), service.getValorUnitarioFotoExtra(agendamentoId));
        verify(configuracaoService, never()).getValorDecimal(anyString(), any());
    }

    @Test
    void getValorUnitarioFotoExtra_caiParaConfigQuandoPacoteSemPreco() {
        when(agendamentoRepository.findById(agendamentoId))
            .thenReturn(Optional.of(Agendamento.builder().id(agendamentoId).pacote(Pacote.builder().precoFotoExtra(BigDecimal.ZERO).build()).build()));
        when(configuracaoService.getValorDecimal("valorUnitarioFotoExtra", new BigDecimal("15.00")))
            .thenReturn(new BigDecimal("15.00"));

        assertEquals(new BigDecimal("15.00"), service.getValorUnitarioFotoExtra(agendamentoId));
    }

    @Test
    void downloadFoto_negadoParaFotoNaoLiberada() {
        mockAgendamento(Pacote.builder().build());
        var foto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(false)
            .visivel(true)
            .build();

        when(fotoEnsaioRepository.findById(foto.getId())).thenReturn(Optional.of(foto));

        assertThrows(RuntimeException.class, () -> service.downloadFoto(token, foto.getId()));
    }

    @Test
    void getDownloadableFotos_ignoraFotosOcultas() {
        mockAgendamento(Pacote.builder().build());
        var visivelSelecionada = foto(true, true);
        var ocultaSelecionada = foto(true, false);

        when(fotoEnsaioRepository.findByAgendamentoIdOrderByOrdemAsc(agendamentoId))
            .thenReturn(List.of(visivelSelecionada, ocultaSelecionada));

        var resultado = service.getDownloadableFotos(token);

        assertEquals(1, resultado.size());
        assertEquals(visivelSelecionada.getId(), resultado.get(0).getId());
    }

    @Test
    void selecionarFotos_naoRetornaFotosDeOutraGaleria() {
        mockAgendamento(Pacote.builder().quantidadeFotos(5).build());
        var minhaFoto = foto(false, true);
        var outraFoto = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(UUID.randomUUID())
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(false)
            .visivel(true)
            .build();

        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(minhaFoto, outraFoto));
        when(fotoEnsaioRepository.countSelecionadasPacoteByAgendamentoId(agendamentoId)).thenReturn(0);
        when(fotoEnsaioRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.selecionarFotos(token, List.of(minhaFoto.getId(), outraFoto.getId()), true);

        assertEquals(1, resultado.size());
        assertEquals(minhaFoto.getId(), resultado.get(0).getId());
    }

    @Test
    void adicionarAoCarrinho_rejeitaFotoJaNoPacote() {
        mockAgendamento(Pacote.builder().build());
        var fotoPacote = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(true)
            .visivel(true)
            .build();

        when(fotoEnsaioRepository.findById(fotoPacote.getId())).thenReturn(Optional.of(fotoPacote));

        assertThrows(IllegalArgumentException.class,
            () -> service.adicionarAoCarrinho(token, UUID.randomUUID(), fotoPacote.getId()));
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

        when(itemCarrinhoRepository.findBySessionIdAndAgendamentoIdOrderByCreatedAtAsc(sessionId, agendamentoId))
            .thenReturn(List.of(item));
        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(fotoPacote));

        assertThrows(IllegalArgumentException.class, () -> service.checkout(token, sessionId, null));
    }

    @Test
    void selecionarFotos_rejeitaRemoverFotoJaBaixada() {
        mockAgendamento(Pacote.builder().build());
        var baixada = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(true)
            .dataDownload(LocalDateTime.now())
            .visivel(true)
            .build();

        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(baixada));

        assertThrows(IllegalArgumentException.class,
            () -> service.selecionarFotos(token, List.of(baixada.getId()), false));
    }

    @Test
    void selecionarFotos_permiteRemoverFotoNaoBaixada() {
        mockAgendamento(Pacote.builder().build());
        var naoBaixada = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .selecionadaPacote(true)
            .visivel(true)
            .build();

        when(fotoEnsaioRepository.findAllById(anyList())).thenReturn(List.of(naoBaixada));
        when(fotoEnsaioRepository.saveAll(anyList())).thenReturn(List.of(naoBaixada));

        var resultado = service.selecionarFotos(token, List.of(naoBaixada.getId()), false);

        assertEquals(1, resultado.size());
        assertTrue(!resultado.get(0).isSelecionadaPacote());
    }

    @Test
    void buscarComprovantePathPorId_retornaNullQuandoNaoHaComprovante() {
        var compra = CompraExtra.builder().id(UUID.randomUUID()).urlComprovante(null).build();
        when(compraExtraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));

        assertNull(service.buscarComprovantePathPorId(compra.getId()));
    }

    @Test
    void buscarComprovantePathPorId_retornaPathDoComprovante() {
        var compra = CompraExtra.builder().id(UUID.randomUUID()).urlComprovante("uploads/x.pdf").build();
        when(compraExtraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));

        assertEquals(Path.of("uploads/x.pdf"), service.buscarComprovantePathPorId(compra.getId()));
    }

    @Test
    void simularPagamento_marcaPagaELiberaFotos() {
        mockAgendamento(Pacote.builder().build());
        var compra = CompraExtra.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build();
        var fotoExtra = FotoEnsaio.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusFoto.PUBLICADA)
            .compraExtraId(compra.getId())
            .selecionadaPacote(false)
            .visivel(true)
            .build();

        when(compraExtraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));
        when(compraExtraRepository.save(compra)).thenReturn(compra);
        when(fotoEnsaioRepository.findAll()).thenReturn(List.of(fotoExtra));
        when(fotoEnsaioRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.simularPagamento(token, compra.getId());

        assertEquals(StatusCompraExtra.PAGA, resultado.getStatus());
        assertTrue(resultado.getDataPagamento() != null);
        assertEquals(StatusFoto.PAGA, fotoExtra.getStatus());
    }

    @Test
    void simularPagamento_rejeitaCompraDeOutroAgendamento() {
        mockAgendamento(Pacote.builder().build());
        var compra = CompraExtra.builder()
            .id(UUID.randomUUID())
            .agendamentoId(UUID.randomUUID())
            .status(StatusCompraExtra.AGUARDANDO_COMPROVANTE)
            .build();

        when(compraExtraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));

        assertThrows(RuntimeException.class, () -> service.simularPagamento(token, compra.getId()));
        verify(compraExtraRepository, never()).save(any());
    }

    @Test
    void simularPagamento_idempotenteQuandoJaPaga() {
        mockAgendamento(Pacote.builder().build());
        var compra = CompraExtra.builder()
            .id(UUID.randomUUID())
            .agendamentoId(agendamentoId)
            .status(StatusCompraExtra.PAGA)
            .dataPagamento(LocalDateTime.now())
            .build();

        when(compraExtraRepository.findById(compra.getId())).thenReturn(Optional.of(compra));

        var resultado = service.simularPagamento(token, compra.getId());

        assertEquals(StatusCompraExtra.PAGA, resultado.getStatus());
        verify(compraExtraRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
