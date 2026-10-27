package com.photoizer.crm.ecommerce.api;

/**
 * Contadores da Central de Galerias. Alimenta os cards do topo e o badge do
 * menu sem precisar carregar a fila inteira.
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
    int extrasPendentesEdicao
) {
}
