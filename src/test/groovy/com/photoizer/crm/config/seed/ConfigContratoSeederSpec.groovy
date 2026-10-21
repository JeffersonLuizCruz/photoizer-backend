package com.photoizer.crm.config.seed

import com.photoizer.crm.config.model.ConfigKey
import com.photoizer.crm.config.model.Configuracao
import com.photoizer.crm.config.repository.ConfiguracaoRepository
import spock.lang.Specification
import spock.lang.Subject

class ConfigContratoSeederSpec extends Specification {

    def configuracaoRepository = Mock(ConfiguracaoRepository)

    @Subject
    def seeder = new ConfigContratoSeeder(configuracaoRepository)

    def templateLegado = templateCompleto('Valor pago como reserva ({{percentualEntrada}}%): {{valorEntrada}}')

    def setup() {
        configuracaoRepository.existsById(_) >> true
    }

    def "adiciona o placeholder de status da reserva ao template legado"() {
        given: "um template persistido com a linha antiga e sem o novo placeholder"
        def config = configuracao(templateLegado)
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()) >> Optional.of(config)

        when: "o seeder executa"
        seeder.run()

        then: "a linha é substituída pelo placeholder de status"
        config.valor.contains('{{statusReserva}}')
        config.valor.contains('Valor da reserva ({{percentualEntrada}}%) - {{statusReserva}}: {{valorEntrada}}')
        !config.valor.contains('Valor pago como reserva')
        1 * configuracaoRepository.save(config)
    }

    def "não altera template que já possui o placeholder de status"() {
        given: "um template já atualizado"
        def config = configuracao(templateCompleto(
            'Valor da reserva ({{percentualEntrada}}%) - {{statusReserva}}: {{valorEntrada}}'))
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()) >> Optional.of(config)

        when: "o seeder executa novamente"
        seeder.run()

        then: "nenhuma escrita é feita"
        0 * configuracaoRepository.save(_)
    }

    def "não altera template customizado que não contém a linha antiga"() {
        given: "um template customizado sem a frase legada"
        def config = configuracao(templateCompleto('Valor da reserva: {{valorEntrada}}'))
        configuracaoRepository.findById(ConfigKey.CONTRATO_TEMPLATE.getKey()) >> Optional.of(config)

        when: "o seeder executa"
        seeder.run()

        then: "o template permanece intacto"
        config.valor == templateCompleto('Valor da reserva: {{valorEntrada}}')
        0 * configuracaoRepository.save(_)
    }

    private String templateCompleto(String linhaReserva) {
        """
        Local do ensaio: {{localEnsaio}}
        {{profissionaisEnsaio}}
        Fotógrafo responsável: {{fotografoResponsavel}}
        Horário do ensaio: {{horarioEnsaio}}
        Duração do ensaio: {{duracaoEnsaio}}
        Inclui:
        {{pacoteDescricao}}
        Valor do deslocamento: {{taxaDeslocamento}}
        Valor total do serviço: {{valorTotal}}
        ${linhaReserva}
        """.stripIndent()
    }

    private Configuracao configuracao(String valor) {
        def config = new Configuracao()
        config.setChave(ConfigKey.CONTRATO_TEMPLATE.getKey())
        config.setValor(valor)
        config
    }
}
