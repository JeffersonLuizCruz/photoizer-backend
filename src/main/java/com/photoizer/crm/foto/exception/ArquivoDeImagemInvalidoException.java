package com.photoizer.crm.foto.exception;

import com.photoizer.crm.shared.exception.ErrorCode;
import com.photoizer.crm.shared.exception.UnprocessableException;

/**
 * Arquivo enviado não é uma imagem legível (formato não suportado, conteúdo
 * corrompido ou extensão que não corresponde ao conteúdo real).
 *
 * A mensagem inclui o nome do arquivo para que a staff consiga removê-lo da
 * seleção e reenviar o restante do lote.
 */
public class ArquivoDeImagemInvalidoException extends UnprocessableException {

    public ArquivoDeImagemInvalidoException(String fileName) {
        super(ErrorCode.ARQUIVO_DE_IMAGEM_INVALIDO, mensagem(fileName));
    }

    public ArquivoDeImagemInvalidoException(String fileName, Throwable causa) {
        super(ErrorCode.ARQUIVO_DE_IMAGEM_INVALIDO, mensagem(fileName));
        initCause(causa);
    }

    private static String mensagem(String fileName) {
        var nome = fileName == null || fileName.isBlank() ? "arquivo" : fileName;
        return "Arquivo de imagem inválido ou corrompido: " + nome;
    }
}
