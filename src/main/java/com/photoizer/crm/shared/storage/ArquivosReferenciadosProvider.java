package com.photoizer.crm.shared.storage;

import java.util.Collection;

/**
 * Fornece os caminhos de arquivo que estão referenciados no banco.
 *
 * <p>Cada módulo dono de arquivos implementa esta porta em vez de expor seus
 * repositórios. A limpeza de órfãos agrega todos os providers e só considera
 * órfão o arquivo que nenhum módulo referencia.
 */
public interface ArquivosReferenciadosProvider {

    Collection<String> caminhosReferenciados();
}
