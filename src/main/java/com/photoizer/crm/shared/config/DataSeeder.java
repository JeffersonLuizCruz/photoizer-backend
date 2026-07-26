package com.photoizer.crm.shared.config;

import com.photoizer.crm.auth.model.Papel;
import com.photoizer.crm.auth.model.User;
import com.photoizer.crm.auth.repository.UserRepository;
import com.photoizer.crm.config.model.Configuracao;
import com.photoizer.crm.config.repository.ConfiguracaoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ConfiguracaoRepository configuracaoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      ConfiguracaoRepository configuracaoRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
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
            var c6 = new Configuracao();
            c6.setChave("notificarAutomaticamente");
            c6.setValor("true");
            configuracaoRepository.saveAll(List.of(c1, c2, c3, c4, c5, c6));
        }
    }
}
