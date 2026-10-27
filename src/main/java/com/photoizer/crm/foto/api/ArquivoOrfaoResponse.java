package com.photoizer.crm.foto.api;

import java.time.LocalDateTime;

/**
 * Arquivo encontrado no diretório de uploads sem referência no banco.
 *
 * @param reconhecido segue um padrão de nome conhecido do sistema
 * @param elegivel    pode ser apagado agora (reconhecido e com mais de 24h)
 */
public record ArquivoOrfaoResponse(
    String caminho,
    long bytes,
    LocalDateTime modificadoEm,
    boolean reconhecido,
    boolean elegivel
) {}
