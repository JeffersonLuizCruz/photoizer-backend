package com.photoizer.crm.foto.event;

import java.util.List;
import java.util.UUID;

/**
 * Publicado após o expurgo dos arquivos de uma galeria finalizada.
 *
 * <p>Diferente de {@link FotosRemovidasEvent}, os registros das fotos
 * permanecem; o ecommerce apenas precisa limpar referências voláteis
 * (carrinho, favoritos, comentários, capa/hero e caches de prévia).
 */
public record GaleriaArquivadaEvent(UUID agendamentoId, List<UUID> fotoIds) {}
