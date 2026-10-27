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
    def "deriva a etapa #esperada para status=#statusAgendamento, modo=#modo, fase=#fase"() {
        expect: "a etapa é calculada a partir dos fatos do ensaio"
        GaleriaPipelineEtapaResolver.resolver(
            new DadosEtapa(statusAgendamento, modo, fase, total, publicadas, arquivadas, selecao)
        ) == esperada

        where:
        statusAgendamento        | modo                | fase                 | total | publicadas | arquivadas | selecao         | esperada
        StatusAgendamento.REALIZADO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO | 0     | 0          | 0          | null            | EtapaGaleria.AGUARDANDO_UPLOAD
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO | 10    | 0          | 0          | null            | EtapaGaleria.FOTOS_NAO_PUBLICADAS
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO | 10    | 10         | 0          | null            | EtapaGaleria.AGUARDANDO_SELECAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO_ENVIADA | 10 | 10 | 0 | StatusSelecao.ENVIADA | EtapaGaleria.SELECAO_EM_EDICAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.EM_EDICAO | 10 | 10 | 0 | StatusSelecao.EM_EDICAO | EtapaGaleria.SELECAO_EM_EDICAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.EM_EDICAO | 10 | 10 | 0 | StatusSelecao.CONCLUIDA | EtapaGaleria.SELECAO_EM_EDICAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.SEM_EDICAO | FaseGaleria.SELECAO_ENVIADA | 10 | 10 | 0 | StatusSelecao.ENVIADA | EtapaGaleria.SELECAO_AGUARDANDO_APROVACAO
        StatusAgendamento.EM_EDICAO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | EtapaGaleria.AGUARDANDO_DOWNLOAD
        StatusAgendamento.FINALIZADO | ModoGaleria.COM_EDICAO | FaseGaleria.COMERCIALIZACAO | 10 | 10 | 0 | StatusSelecao.PUBLICADA | EtapaGaleria.CONCLUIDA
        StatusAgendamento.FINALIZADO | ModoGaleria.COM_EDICAO | FaseGaleria.SELECAO | 10 | 10 | 10 | null | EtapaGaleria.ARQUIVADA
        StatusAgendamento.EM_EDICAO | ModoGaleria.DIRETO | FaseGaleria.COMERCIALIZACAO | 0 | 0 | 0 | null | EtapaGaleria.AGUARDANDO_UPLOAD
    }
}
