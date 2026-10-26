package com.photoizer.crm.config.service;

import com.photoizer.crm.config.model.ConfigKey;
import com.photoizer.crm.shared.exception.BadRequestException;
import com.photoizer.crm.shared.storage.FileStorageService;
import com.photoizer.crm.shared.storage.FileValidator;
import com.photoizer.crm.shared.storage.StoragePathResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.Locale;

/**
 * Gerencia a logo da contratada exibida na proposta pública.
 *
 * <p>O arquivo enviado é validado como imagem, salvo em {@code uploads/} e o
 * caminho relativo fica na configuração {@link ConfigKey#LOGO_CONTRATADA}.
 * Quando não há upload (ou o arquivo sumiu), serve a arte padrão empacotada no
 * classpath ({@value #LOGO_PADRAO}), garantindo que a proposta nunca fique sem
 * marca.
 */
@Service
public class LogoContratadaService {

    static final String LOGO_PADRAO = "branding/logo-padrao.png";
    private static final MediaType IMAGE_WEBP = MediaType.parseMediaType("image/webp");

    private final ConfiguracaoService configuracaoService;
    private final FileStorageService fileStorageService;
    private final FileValidator fileValidator;
    private final StoragePathResolver pathResolver;

    public LogoContratadaService(ConfiguracaoService configuracaoService,
                                 FileStorageService fileStorageService,
                                 FileValidator fileValidator,
                                 StoragePathResolver pathResolver) {
        this.configuracaoService = configuracaoService;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.pathResolver = pathResolver;
    }

    /**
     * Valida, salva a nova logo e substitui a anterior na configuração.
     *
     * @return caminho relativo persistido (para o frontend atualizar a prévia)
     */
    public String salvar(MultipartFile arquivo) {
        try {
            fileValidator.validate(arquivo, "logo");
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        var anterior = configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA);
        var caminho = fileStorageService.salvar(arquivo);
        configuracaoService.atualizar(ConfigKey.LOGO_CONTRATADA, caminho);

        if (!anterior.isBlank() && !anterior.equals(caminho)) {
            fileStorageService.deletar(anterior);
        }
        return caminho;
    }

    /**
     * Remove a logo personalizada e volta a usar a arte padrão.
     */
    public void remover() {
        var atual = configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA);
        configuracaoService.atualizar(ConfigKey.LOGO_CONTRATADA, "");
        if (!atual.isBlank()) {
            fileStorageService.deletar(atual);
        }
    }

    /**
     * Resolve a arte atual: upload configurado ou logo padrão do classpath.
     */
    public LogoArquivo carregar() {
        var caminho = configuracaoService.getValor(ConfigKey.LOGO_CONTRATADA);
        if (!caminho.isBlank()) {
            var arquivo = pathResolver.resolver(caminho);
            if (arquivo != null
                && arquivo.startsWith(pathResolver.getUploadDir())
                && Files.isRegularFile(arquivo)) {
                return new LogoArquivo(
                    new FileSystemResource(arquivo),
                    contentType(caminho),
                    nomeArquivo(caminho));
            }
        }
        return new LogoArquivo(
            new ClassPathResource(LOGO_PADRAO),
            MediaType.IMAGE_PNG,
            "logo-padrao.png");
    }

    private MediaType contentType(String caminho) {
        var nome = caminho.toLowerCase(Locale.ROOT);
        if (nome.endsWith(".jpg") || nome.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (nome.endsWith(".webp")) {
            return IMAGE_WEBP;
        }
        return MediaType.IMAGE_PNG;
    }

    private String nomeArquivo(String caminho) {
        var nome = caminho.substring(caminho.lastIndexOf('/') + 1);
        var indice = nome.indexOf('_');
        return indice >= 0 && indice < nome.length() - 1 ? nome.substring(indice + 1) : nome;
    }

    /** Arte resolvida pronta para ser servida via HTTP. */
    public record LogoArquivo(Resource resource, MediaType contentType, String filename) {
    }
}
