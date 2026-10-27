package com.photoizer.crm.ecommerce.service;

import com.photoizer.crm.agenda.model.StatusAgendamento;
import com.photoizer.crm.ecommerce.model.EtapaGaleria;
import com.photoizer.crm.ecommerce.model.FaseGaleria;
import com.photoizer.crm.ecommerce.model.ModoGaleria;
import com.photoizer.crm.ecommerce.model.StatusSelecao;

/**
 * Resolve a etapa operacional de uma galeria a partir dos fatos do ensaio.
 *
 * <p>Regra pura e sem dependencia de banco para ser testada isoladamente. A
 * precedencia e: arquivamento, selecao ativa, ausencia de fotos, ausencia de
 * publicacao, comercializacao e, por fim, aguardando escolha do cliente.
 */
public final class GaleriaPipelineEtapaResolver {

    private GaleriaPipelineEtapaResolver() {
    }

    public record DadosEtapa(
        StatusAgendamento statusAgendamento,
        ModoGaleria modo,
        FaseGaleria fase,
        long totalFotos,
        long fotosPublicadas,
        long fotosArquivadas,
        StatusSelecao selecaoStatus
    ) {
    }

    public static EtapaGaleria resolver(DadosEtapa dados) {
        if (dados.totalFotos() > 0 && dados.fotosArquivadas() >= dados.totalFotos()) {
            return EtapaGaleria.ARQUIVADA;
        }

        if (dados.selecaoStatus() != null && dados.selecaoStatus().estaAtiva()) {
            if (dados.selecaoStatus() == StatusSelecao.ENVIADA
                && !dados.modo().requerEdicao()) {
                return EtapaGaleria.SELECAO_AGUARDANDO_APROVACAO;
            }
            return EtapaGaleria.SELECAO_EM_EDICAO;
        }

        if (dados.totalFotos() == 0) {
            return EtapaGaleria.AGUARDANDO_UPLOAD;
        }

        if (dados.fotosPublicadas() == 0) {
            return EtapaGaleria.FOTOS_NAO_PUBLICADAS;
        }

        if (dados.fase() == FaseGaleria.COMERCIALIZACAO
            || dados.selecaoStatus() == StatusSelecao.PUBLICADA) {
            return dados.statusAgendamento() == StatusAgendamento.FINALIZADO
                ? EtapaGaleria.CONCLUIDA
                : EtapaGaleria.AGUARDANDO_DOWNLOAD;
        }

        return EtapaGaleria.AGUARDANDO_SELECAO;
    }
}
