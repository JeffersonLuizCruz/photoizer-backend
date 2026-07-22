package com.photoizer.crm.shared.config;

import com.photoizer.crm.agenda.model.Usuario;
import com.photoizer.crm.agenda.repository.UsuarioRepository;
import com.photoizer.crm.config.model.Configuracao;
import com.photoizer.crm.config.repository.ConfiguracaoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final ConfiguracaoRepository configuracaoRepository;

    public DataSeeder(UsuarioRepository usuarioRepository,
                      ConfiguracaoRepository configuracaoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.configuracaoRepository = configuracaoRepository;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() == 0) {
            usuarioRepository.saveAll(List.of(
                Usuario.builder()
                    .nome("Carol (Fotógrafa)")
                    .email("carol@photoizer.com")
                    .papel("FOTOGRAFA")
                    .build(),
                Usuario.builder()
                    .nome("João (Editor)")
                    .email("joao@photoizer.com")
                    .papel("EDITOR")
                    .build(),
                Usuario.builder()
                    .nome("Maria (Assistente)")
                    .email("maria@photoizer.com")
                    .papel("ASSISTENTE")
                    .build()
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
            configuracaoRepository.saveAll(List.of(c1, c2, c3));
        }
    }
}
