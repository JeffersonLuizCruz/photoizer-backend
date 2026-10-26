package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.ecommerce.exception.SelecaoNaoEncontradaException;
import com.photoizer.crm.ecommerce.model.SelecaoEnsaioItem;
import com.photoizer.crm.ecommerce.model.TipoItemSelecao;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioItemRepository;
import com.photoizer.crm.ecommerce.repository.SelecaoEnsaioRepository;
import com.photoizer.crm.foto.model.FotoEnsaio;
import com.photoizer.crm.foto.repository.FotoEnsaioRepository;
import com.photoizer.crm.shared.storage.FileValidator;
import com.photoizer.crm.shared.storage.StoragePathResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Gera o ZIP de originais para o staff editar. O ZIP contem apenas a selecao
 * enviada e um manifest com numero, fotoId, tipo e nome original.
 */
@Service
@Transactional
public class SelecaoDownloadService {

    private final SelecaoEnsaioRepository selecaoRepository;
    private final SelecaoEnsaioItemRepository itemRepository;
    private final FotoEnsaioRepository fotoRepository;
    private final SelecaoEnsaioService selecaoService;
    private final GaleriaEnsaioService galeriaService;
    private final StoragePathResolver pathResolver;
    private final FileValidator fileValidator;

    public SelecaoDownloadService(SelecaoEnsaioRepository selecaoRepository,
                                  SelecaoEnsaioItemRepository itemRepository,
                                  FotoEnsaioRepository fotoRepository,
                                  SelecaoEnsaioService selecaoService,
                                  GaleriaEnsaioService galeriaService,
                                  StoragePathResolver pathResolver,
                                  FileValidator fileValidator) {
        this.selecaoRepository = selecaoRepository;
        this.itemRepository = itemRepository;
        this.fotoRepository = fotoRepository;
        this.selecaoService = selecaoService;
        this.galeriaService = galeriaService;
        this.pathResolver = pathResolver;
        this.fileValidator = fileValidator;
    }

    public record ItemZip(String entryName, Path path, SelecaoEnsaioItem item, FotoEnsaio foto, boolean presente) {}

    public record SelecaoZip(String fileName, List<ItemZip> itens) {}

    public SelecaoZip preparar(UUID agendamentoId, UUID selecaoId) {
        var galeria = galeriaService.obter(agendamentoId);
        if (!galeria.getModo().requerEdicao()) {
            throw new com.photoizer.crm.ecommerce.exception.SelecaoNaoEditavelException(
                "Esta galeria não passa por edição; não há originais para baixar");
        }
        var selecao = selecaoRepository.findById(selecaoId)
            .orElseThrow(() -> new SelecaoNaoEncontradaException(selecaoId));
        if (!selecao.getAgendamentoId().equals(agendamentoId)) {
            throw new SelecaoNaoEncontradaException(selecaoId);
        }

        var itens = itemRepository.findBySelecaoIdOrderByNumeroGaleriaAsc(selecaoId);
        var fotos = fotoRepository.findAllById(itens.stream().map(SelecaoEnsaioItem::getFotoId).toList())
            .stream().collect(Collectors.toMap(FotoEnsaio::getId, Function.identity()));

        var itensZip = itens.stream().map(item -> {
            var foto = fotos.get(item.getFotoId());
            var path = foto == null ? null : pathResolver.resolver(foto.getOriginalPath());
            var nome = fileValidator.sanitizeFilename(
                item.getNomeArquivo() != null ? item.getNomeArquivo() : "foto.jpg");
            var entry = String.format("%03d_%s_%s", item.getNumeroGaleria(), item.getFotoId(), nome);
            return new ItemZip(entry, path, item, foto, path != null && Files.exists(path));
        }).toList();

        selecaoService.registrarDownloadOriginais(agendamentoId, selecaoId);
        return new SelecaoZip("selecao_" + selecao.getNumero() + ".zip", itensZip);
    }

    public void escreverZip(SelecaoZip zip, OutputStream outputStream) throws IOException {
        try (var zos = new ZipOutputStream(outputStream)) {
            for (var item : zip.itens()) {
                if (item.presente()) {
                    zos.putNextEntry(new ZipEntry(item.entryName()));
                    Files.copy(item.path(), zos);
                    zos.closeEntry();
                }
            }
            zos.putNextEntry(new ZipEntry("manifest.csv"));
            escreverManifest(zip, zos);
            zos.closeEntry();
        }
    }

    private void escreverManifest(SelecaoZip zip, OutputStream outputStream) throws IOException {
        Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        writer.write('\ufeff'); // BOM para abrir no Excel em pt-BR
        writer.write("numero_galeria;foto_id;tipo;ordem;nome_original;nome_esperado_editado;presente\n");
        for (var item : zip.itens()) {
            writer.write(String.join(";",
                String.valueOf(item.item().getNumeroGaleria()),
                item.item().getFotoId().toString(),
                item.item().getTipo() == TipoItemSelecao.PACOTE ? "PACOTE" : "EXTRA",
                String.valueOf(item.item().getOrdem()),
                csv(item.item().getNomeArquivo()),
                item.entryName(),
                item.presente() ? "SIM" : "NAO"));
            writer.write('\n');
        }
        writer.flush();
    }

    private String csv(String valor) {
        if (valor == null) return "";
        var limpo = valor.replace("\"", "\"\"");
        return limpo.contains(";") || limpo.contains("\"") || limpo.contains("\n")
            ? "\"" + limpo + "\""
            : limpo;
    }
}
