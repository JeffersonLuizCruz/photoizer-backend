package com.photoizer.crm.ecommerce.service

import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.ecommerce.model.EtapaGaleria
import com.photoizer.crm.ecommerce.model.FaseGaleria
import com.photoizer.crm.ecommerce.model.ModoGaleria
import com.photoizer.crm.ecommerce.model.StatusSelecao
import com.photoizer.crm.ecommerce.service.GaleriaPipelineEtapaResolver.DadosEtapa
import spock.lang.Specification
import spock.lang.Unroll

class GaleriaPipelineEtapaResolverSpec extends Specification {

    @Unroll
    def "deriva a etapa #esperada para status=#statusAgendamento, modo=#modo, fase=#fase"(
            StatusAgendamento statusAgendamento,
            ModoGaleria modo,
            FaseGaleria fase,
            long total,
            long publicadas,
            long arquivadas,
            StatusSelecao selecao,
            long fotosPacote,
            long fotosPacoteBaixadas,
            long comprasPendentes,
            EtapaGaleria esperada) {
        expect: "a etapa é calculada a partir dos fatos do ensaio"
        GaleriaPipelineEtapaResolver.resolver(new DadosEtapa(
            statusAgendamento, modo, fase, total, publicadas, arquivadas, selecao,
            fotosPacote, fotosPacoteBaixadas, comprasPendentes
        )) == esperada

        where:
        statusAgendamento           | modo                   | fase                 | total | publicadas | arquivadas | selecao                  | fotosPacote | fotosPacoteBaixadas | comprasPendentes || esperada
        StatusAgendamento.REALIZADO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO  | 0     | 0          | 0          | null                     | 0           | 0                   | 0                || EtapaGaleria.AGUARDANDO_UPLOAD
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO  | 10    | 0          | 0          | null                     | 0           | 0                   | 0                || EtapaGaleria.FOTOS_NAO_PUBLICADAS
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO  | 10    | 10         | 0          | null                     | 0           | 0                   | 0                || EtapaGaleria.AGUARDANDO_SELECAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO_ENVIADA | 10 | 10 | 0 | StatusSelecao.ENVIADA | 10 | 0 | 0 || EtapaGaleria.SELECAO_EM_EDICAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.EM_EDICAO | 10 | 10 | 0 | StatusSelecao.EM_EDICAO | 10 | 0 | 0 || EtapaGaleria.SELECAO_EM_EDICAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.EM_EDICAO | 10 | 10 | 0 | StatusSelecao.CONCLUIDA | 10 | 0 | 0 || EtapaGaleria.SELECAO_EM_EDICAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.SEM_EDICAO | FaseGaleria.SELECAO_ENVIADA | 10 | 10 | 0 | StatusSelecao.ENVIADA | 10 | 0 | 0 || EtapaGaleria.SELECAO_AGUARDANDO_APROVACAO

        // Comercialização: aguardando download, parcialmente baixado e totalmente baixado.
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | 10 | 0  | 0 || EtapaGaleria.AGUARDANDO_DOWNLOAD
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | 10 | 5  | 0 || EtapaGaleria.AGUARDANDO_DOWNLOAD
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | 10 | 10 | 0 || EtapaGaleria.CLIENTE_BAIXOU
        StatusAgendamento.FINALIZADO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | 10 | 0  | 0 || EtapaGaleria.CONCLUIDA
        StatusAgendamento.FINALIZADO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | 10 | 10 | 0 || EtapaGaleria.CLIENTE_BAIXOU

        // Compra de extra pendente tem prioridade sobre a etapa de entrega.
        StatusAgendamento.FINALIZADO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | 10 | 10 | 1 || EtapaGaleria.COMPRA_EXTRA_PENDENTE
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | 10 | 0  | 2 || EtapaGaleria.COMPRA_EXTRA_PENDENTE

        // Arquivamento vence qualquer pendência.
        StatusAgendamento.FINALIZADO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO | 10 | 10 | 10 | null | 0 | 0 | 1 || EtapaGaleria.ARQUIVADA

        // Fluxo legado sem fotos continua aguardando upload.
        StatusAgendamento.EM_EDICAO | ModoGaleria.DIRETO | FaseGaleria.COMERCIALIZACAO | 0 | 0 | 0 | null | 0 | 0 | 0 || EtapaGaleria.AGUARDANDO_UPLOAD
    }
}
