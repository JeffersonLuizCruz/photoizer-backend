package com.photoizer.crm.documento.service;

import java.util.List;

/**
 * PATTERN: Strategy Pattern
 *
 * Define contrato para estratégias de geração de conteúdo PDF.
 * Cada tipo de documento (contrato, recibo, etc.) implementa esta interface
 * para fornecer título e linhas específicas.
 *
 * Motivo: Permite adicionar novos tipos de PDF sem modificar DocumentoService
 * (princípio Open/Closed). A lógica de formatação fica isolada em classes
 * específicas, facilitando testes e manutenção.
 *
 * Uso do Spring: Cada implementação é um @Component, permitindo injeção
 * por tipo ou por lista (para descoberta automática).
 *
 * Como extender:
 * Para adicionar um novo tipo de PDF (ex: "certificado"), basta criar uma
 * nova classe que implementa esta interface e anotar com @Component.
 * O DocumentoService descobrirá automaticamente via injeção de Collection.
 *
 * Exemplo:
 * @Component
 * public class CertificadoPdfStrategy implements PdfContentStrategy {
 *     @Override
 *     public String getTipo() { return "certificado"; }
 *     // ...
 * }
 */
public interface PdfContentStrategy {

    /**
     * Identificador único do tipo de PDF.
     * Usado para resolução da estratégia no DocumentoService.
     */
    String getTipo();

    /**
     * Título do documento PDF (aparece em negrito no topo).
     */
    String getTitulo();

    /**
     * Gera as linhas de conteúdo do PDF.
     *
     * @param contexto objeto com dados necessários para formatação
     *                 (tipicamente a entidade de domínio, ex: Agendamento)
     * @return lista de linhas do documento
     */
    List<String> getLinhas(Object contexto);
}
