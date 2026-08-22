package com.photoizer.crm.documento.service;

import com.photoizer.crm.agenda.model.Agendamento;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * PATTERN: Strategy Pattern - Implementação para Contratos
 *
 * Gera conteúdo específico para contratos de prestação de serviços fotográficos.
 * Extrai dados do Agendamento e formata conforme layout definido.
 *
 * Motivo: Isola a lógica de formatação de contrato do DocumentoService,
 * permitindo mudanças de layout sem afetar outros tipos de PDF.
 * O construtor recebe o contexto tipado (Agendamento), garantindo
 * type-safety e eliminando casting genérico.
 */
@Component
public class ContratoPdfStrategy implements PdfContentStrategy {

    @Override
    public String getTipo() {
        return "contrato";
    }

    @Override
    public String getTitulo() {
        return "CONTRATO DE PRESTAÇÃO DE SERVIÇOS FOTOGRÁFICOS";
    }

    @Override
    public List<String> getLinhas(Object contexto) {
        var agendamento = (Agendamento) contexto;
        var cliente = agendamento.getCliente();
        var pacote = agendamento.getPacote();

        return List.of(
            "Cliente: " + cliente.getNome(),
            "CPF: " + cliente.getCpf(),
            "Telefone: " + cliente.getTelefone(),
            "",
            "Pacote: " + pacote.getNome(),
            "Data do ensaio: " + agendamento.getDataHoraEnsaio(),
            "Local: " + agendamento.getLocalEnsaio(),
            "Endereço: " + agendamento.getEnderecoCompleto(),
            "",
            "Valor total: R$ " + formatarValor(agendamento.getValorTotal()),
            "Entrada exigida: R$ " + formatarValor(agendamento.getValorEntradaExigido()),
            "Valor restante: R$ " + formatarValor(agendamento.getValorRestante()),
            "Taxa de deslocamento: R$ " + formatarValor(agendamento.getTaxaDeslocamento())
        );
    }

    private String formatarValor(BigDecimal valor) {
        if (valor == null) return "0,00";
        return valor.setScale(2, RoundingMode.HALF_UP)
            .toPlainString().replace(".", ",");
    }
}
