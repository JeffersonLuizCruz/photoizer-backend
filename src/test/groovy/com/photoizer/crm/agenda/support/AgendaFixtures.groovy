package com.photoizer.crm.agenda.support

import com.photoizer.crm.agenda.model.Agendamento
import com.photoizer.crm.agenda.model.AgendamentoFotografo
import com.photoizer.crm.agenda.model.RepasseStatus
import com.photoizer.crm.agenda.model.StatusAgendamento
import com.photoizer.crm.auth.model.Papel
import com.photoizer.crm.auth.model.User
import com.photoizer.crm.cliente.model.Cliente
import com.photoizer.crm.cliente.model.OrigemCliente
import com.photoizer.crm.pacote.model.Pacote
import org.springframework.test.util.ReflectionTestUtils

import java.time.LocalDateTime
import java.util.UUID

final class AgendaFixtures {

    private AgendaFixtures() {
    }

    static Pacote pacote(Map<String, Object> overrides = [:]) {
        def builder = Pacote.builder()
            .id(UUID.randomUUID())
            .nome("Pacote Ensaio")
            .descricao("Pacote de ensaio")
            .quantidadeFotos(20)
            .quantidadeVideos(1)
            .valorBase(new BigDecimal("1000.00"))
            .precoFotoExtra(new BigDecimal("20.00"))
            .cor("#64748B")
            .bloqueiaDiaInteiro(false)
            .ativo(true)
        overrides.each { chave, valor -> builder."$chave"(valor) }
        builder.build()
    }

    static User fotografo(String nome = "Fotógrafo Teste", UUID id = UUID.randomUUID()) {
        def user = new User("fotografo-${UUID.randomUUID()}@teste.com", "hash", nome, Papel.FOTOGRAFO)
        ReflectionTestUtils.setField(user, "id", id)
        user
    }

    /** Usuário novo, sem id, para persistência real em testes de integração. */
    static User novoFotografo(String nome = "Fotógrafo Teste") {
        new User("fotografo-${UUID.randomUUID()}@teste.com", "hash", nome, Papel.FOTOGRAFO)
    }

    static User usuario(String nome, Papel papel, UUID id = UUID.randomUUID()) {
        def user = new User("${papel.name().toLowerCase()}-${UUID.randomUUID()}@teste.com", "hash", nome, papel)
        ReflectionTestUtils.setField(user, "id", id)
        user
    }

    static Cliente cliente(Map<String, Object> overrides = [:]) {
        def builder = Cliente.builder()
            .id(UUID.randomUUID())
            .nome("Maria Souza")
            .telefone("(11) 99999-9999")
            .email("maria@teste.com")
            .origem(OrigemCliente.OUTROS)
        overrides.each { chave, valor -> builder."$chave"(valor) }
        builder.build()
    }

    static Agendamento agendamento(Map<String, Object> overrides = [:]) {
        def dataHora = (LocalDateTime) overrides.remove("dataHoraEnsaio") ?: LocalDateTime.now().plusDays(10)
        def builder = Agendamento.builder()
            .id(UUID.randomUUID())
            .pacote(pacote())
            .dataHoraEnsaio(dataHora)
            .duracaoMinutos(60)
            .localEnsaio("Estúdio")
            .valorTotal(new BigDecimal("1000.00"))
            .valorEntradaExigido(new BigDecimal("300.00"))
            .valorEntradaPago(BigDecimal.ZERO)
            .valorRestante(new BigDecimal("1000.00"))
            .valorExtras(BigDecimal.ZERO)
            .taxaDeslocamento(BigDecimal.ZERO)
            .valorTotalFinal(new BigDecimal("1000.00"))
            .percentualEntrada(new BigDecimal("30.00"))
            .status(StatusAgendamento.PRE_RESERVA)
            .autorizaUsoImagem(false)
            .ensaioDestaque(false)
            .tokenProposta("token-abc")
            .tokenGaleria(UUID.randomUUID())
        overrides.each { chave, valor -> builder."$chave"(valor) }
        builder.build()
    }

    static AgendamentoFotografo repasse(Agendamento agendamento, User fotografo = null,
                                        RepasseStatus status = RepasseStatus.PENDENTE,
                                        BigDecimal valor = new BigDecimal("100.00")) {
        AgendamentoFotografo.builder()
            .id(UUID.randomUUID())
            .agendamento(agendamento)
            .fotografo(fotografo)
            .tipoValor(com.photoizer.crm.shared.model.TipoRepasse.FIXO)
            .valorRepassar(valor)
            .status(status)
            .build()
    }
}
