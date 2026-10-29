package com.photoizer.crm.ecommerce.api;

/**
 * Contadores da Central de Galerias. Alimenta os cards do topo e o badge do
 * menu sem precisar carregar a fila inteira.
 *
 * <p>`aguardandoUpload` permanece por compatibilidade e vale zero: galerias sem
 * fotos no CRM nao entram na fila por padrao. A contagem real fica em
 * `semFotosNoCrm`, usada pelo link de conferencia.
 */
public record GaleriaPipelineResumoResponse(
    int total,
    int aguardandoUpload,
    int fotosNaoPublicadas,
    int aguardandoSelecao,
    int selecaoEmEdicao,
    int selecaoAguardandoAprovacao,
    int aguardandoDownload,
    int concluidas,
    int arquivadas,
    int atrasadas,
    int comprasPendentes,
    int extrasPendentesEdicao,
    int semFotosNoCrm,
    int clientesBaixaram,
    int comprasAguardandoComprovante,
    int comprasAguardandoConfirmacao
) {
}
