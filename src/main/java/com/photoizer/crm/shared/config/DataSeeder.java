package com.photoizer.crm.shared.config;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.config.model.Configuracao;
import com.photoizer.crm.config.repository.ConfiguracaoRepository;
import com.photoizer.crm.indicador.repository.IndicadorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final ConfiguracaoRepository configuracaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final IndicadorRepository indicadorRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public DataSeeder(UserRepository userRepository,
                      ConfiguracaoRepository configuracaoRepository,
                      PasswordEncoder passwordEncoder,
                      IndicadorRepository indicadorRepository) {
        this.userRepository = userRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.indicadorRepository = indicadorRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.saveAll(List.of(
                new User("admin@photoizer.com", passwordEncoder.encode("dev123"), "Administrador", Papel.ADMIN),
                new User("carol@photoizer.com", passwordEncoder.encode("dev123"), "Carol (Fotógrafa)", Papel.FOTOGRAFO),
                new User("joao@photoizer.com", passwordEncoder.encode("dev123"), "João (Editor)", Papel.EDITOR),
                new User("maria@photoizer.com", passwordEncoder.encode("dev123"), "Maria (Assistente)", Papel.EDITOR),
                new User("agendador@photoizer.com", passwordEncoder.encode("dev123"), "Lucas (Agendador)", Papel.AGENDADOR)
            ));
        }

        if (configuracaoRepository.count() == 0) {
            var c1 = new Configuracao();
            c1.setChave("valorUnitarioFotoExtra");
            c1.setValor("15.00");
            var c2 = new Configuracao();
            c2.setChave("valorUnitarioVideoExtra");
            c2.setValor("50.00");
            var c3 = new Configuracao();
            c3.setChave("percentualComissao");
            c3.setValor("10.00");
            var c4 = new Configuracao();
            c4.setChave("percentualEntrada");
            c4.setValor("30.00");
            var c5 = new Configuracao();
            c5.setChave("taxaDeslocamentoPadrao");
            c5.setValor("0.00");
            configuracaoRepository.saveAll(List.of(c1, c2, c3, c4, c5));
        }

        limparIndicadoresDuplicados();
    }

    private void limparIndicadoresDuplicados() {
        var duplicados = entityManager.createQuery(
            "SELECT i.nome, i.telefone FROM Indicador i GROUP BY i.nome, i.telefone HAVING COUNT(i) > 1",
            Object[].class
        ).getResultList();

        if (!duplicados.isEmpty()) {
            int totalRemovidos = 0;
            for (var par : duplicados) {
                var nome = (String) ((Object[]) par)[0];
                var telefone = (String) ((Object[]) par)[1];
                var indicadores = indicadorRepository.findAllByNomeAndTelefone(nome, telefone);
                indicadores.sort(Comparator.comparing(
                    i -> i.getCreatedAt() != null ? i.getCreatedAt() : LocalDateTime.MIN));
                var manter = indicadores.removeLast();
                for (var remover : indicadores) {
                    indicadorRepository.delete(remover);
                }
                log.warn("Indicadores duplicados '{}' ({}): mantido ID {}, removidos {} registro(s)",
                    nome, telefone, manter.getId(), indicadores.size());
                totalRemovidos += indicadores.size();
            }
            log.info("Limpeza de indicadores duplicados concluída: {} registros removidos", totalRemovidos);
        }
    }
}
