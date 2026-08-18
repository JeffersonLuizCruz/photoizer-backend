package com.photoizer.crm.fotografo.service;

import com.photoizer.crm.fotografo.api.FotografoEnsaiosResponse;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class FotografoCsvExporter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SEPARATOR = ";";

    public byte[] exportar(List<FotografoEnsaiosResponse> ensaios, String fotografoNome) {
        try (var baos = new ByteArrayOutputStream();
             var writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {

            writer.write('\uFEFF');
            writer.write("Fotógrafo" + SEPARATOR
                + "Ensaio" + SEPARATOR
                + "Cliente" + SEPARATOR
                + "Data" + SEPARATOR
                + "Valor Pacote" + SEPARATOR
                + "Custos Fotógrafo" + SEPARATOR
                + "Partilha" + SEPARATOR
                + "Repassado" + SEPARATOR
                + "Lucro CRM" + SEPARATOR
                + "Status\n");

            for (var e : ensaios) {
                writer.write(fotografoNome + SEPARATOR
                    + e.agendamentoId() + SEPARATOR
                    + e.clienteNome() + SEPARATOR
                    + e.dataHoraEnsaio().format(DATE_FORMAT) + SEPARATOR
                    + formatar(e.valorTotal()) + SEPARATOR
                    + formatar(e.custosFotografo()) + SEPARATOR
                    + formatar(e.partilhaFotografo()) + SEPARATOR
                    + formatar(e.repassarFotografo()) + SEPARATOR
                    + formatar(e.lucroCrm()) + SEPARATOR
                    + e.status() + "\n");
            }

            writer.flush();
            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Erro ao gerar CSV do fotógrafo", ex);
        }
    }

    private String formatar(java.math.BigDecimal valor) {
        if (valor == null) return "0,00";
        return String.format("%.2f", valor).replace(".", ",");
    }
}