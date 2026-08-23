package com.photoizer.crm.documento.service;

import com.photoizer.crm.documento.model.TipoDocumento;

import java.util.List;

/**
 * PATTERN: Strategy Pattern (Generic Type Safety)
 *
 * Define contrato para geracao de conteudo PDF por tipo de documento.
 * Cada tipo (contrato, recibo, etc.) implementa esta interface
 * para fornecer titulo e linhas especificas.
 *
 * Motivo: Permite adicionar novos tipos de PDF sem modificar DocumentoService
 * (principio Open/Closed). A logica de formatacao fica isolada em classes
 * especificas, facilitando testes e manutencao.
 *
 * Type-Safety: O generic T elimina casting manual em cada implementacao.
 * Cada estrategia declara o tipo de contexto que recebe (tipicamente a entidade
 * de dominio), garantindo compile-time checking.
 *
 * Uso do Spring: Cada implementacao e um @Component, permitindo injecao
 * por tipo ou por lista (para descoberta automatica).
 *
 * Como extender:
 * Para adicionar um novo tipo de PDF (ex: "certificado"), basta criar uma
 * nova classe que implementa esta interface e anotar com @Component.
 * O DocumentoService descobrira automaticamente via injecao de Collection.
 *
 * Exemplo:
 * &#64;Component
 * public class CertificadoPdfStrategy implements PdfContentStrategy&lt;Agendamento&gt; {
 *     &#64;Override
 *     public TipoDocumento getTipo() { return TipoDocumento.CERTIFICADO; }
 *     // ...
 * }
 */
public interface PdfContentStrategy<T> {

    /**
     * Identificador unico do tipo de PDF.
     * Usado para resolucao da estrategia no DocumentoService.
     */
    TipoDocumento getTipo();

    /**
     * Titulo do documento PDF (aparece em negrito no topo).
     */
    String getTitulo();

    /**
     * Gera as linhas de conteudo do PDF.
     *
     * @param contexto objeto com dados necessarios para formatacao
     *                 (tipicamente a entidade de dominio, ex: Agendamento)
     * @return lista de linhas do documento
     */
    List<String> getLinhas(T contexto);
}
