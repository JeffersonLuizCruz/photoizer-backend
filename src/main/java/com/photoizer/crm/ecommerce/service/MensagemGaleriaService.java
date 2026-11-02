package com.photoizer.crm.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Modelo de mensagem enviada ao cliente com o link da galeria.
 *
 * <p>O texto fica centralizado no backend para que a Central de Galerias, o
 * workspace da galeria e o resumo do e-commerce copiem exatamente o mesmo
 * conteudo. O placeholder {@code {{link}}} e substituido pelo frontend, pois
 * somente ele conhece a origem publica do CRM (e o token nao precisa trafegar
 * para o backend).
 *
 * <p>As URLs das redes sociais vem do {@code application.properties}; a
 * assinatura faz parte do texto padrao do estudio.
 */
@Service
public class MensagemGaleriaService {

    static final String PLACEHOLDER_LINK = "{{link}}";

    private static final String TEMPLATE = """
        Olá! 🤍

        Suas fotos já estão disponíveis para seleção no link abaixo.

        {{link}}

        ⚠️ O link ficará disponível por apenas 1 semana, então aproveite para escolher suas fotos com calma.

        📸 Selecione as fotos do seu pacote e, se desejar, adicione extras.

        Assim que você enviar a seleção, o estúdio segue com a edição e combina a entrega com você.

        Obrigada por confiar no meu trabalho! ❤️

        Instagram:
        {{instagram}}

        Facebook:
        {{facebook}}

        E depois me conta o que achou das fotos e do atendimento. Vou adorar saber! 🥰

        📸 Ranny Amaral ❤️
        """;

    private final String instagramUrl;
    private final String facebookUrl;

    public MensagemGaleriaService(
            @Value("${app.social.instagram-url}") String instagramUrl,
            @Value("${app.social.facebook-url}") String facebookUrl) {
        this.instagramUrl = instagramUrl.trim();
        this.facebookUrl = facebookUrl.trim();
    }

    /**
     * Retorna o modelo com as redes sociais resolvidas e o placeholder
     * {@link #PLACEHOLDER_LINK} preservado para o frontend substituir.
     */
    public String mensagem() {
        return TEMPLATE
            .replace("{{instagram}}", instagramUrl)
            .replace("{{facebook}}", facebookUrl)
            .strip();
    }
}
