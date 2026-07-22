package com.photoizer.crm.shared.config;

import com.photoizer.crm.agenda.model.Usuario;
import com.photoizer.crm.agenda.repository.UsuarioRepository;
import com.photoizer.crm.config.model.Configuracao;
import com.photoizer.crm.config.repository.ConfiguracaoRepository;
import com.photoizer.crm.pacote.model.Pacote;
import com.photoizer.crm.pacote.repository.PacoteRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private final PacoteRepository pacoteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracaoRepository configuracaoRepository;

    public DataSeeder(PacoteRepository pacoteRepository, UsuarioRepository usuarioRepository,
                      ConfiguracaoRepository configuracaoRepository) {
        this.pacoteRepository = pacoteRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracaoRepository = configuracaoRepository;
    }

    @Override
    public void run(String... args) {
        if (pacoteRepository.count() == 0) {
            pacoteRepository.saveAll(List.of(
                Pacote.builder()
                    .nome("Ensaio Básico")
                    .descricao("Ensaio fotográfico simples com fotos digitais")
                    .quantidadeFotos(15)
                    .quantidadeVideos(0)
                    .valorBase(new BigDecimal("250.00"))
                    .duracaoEstimada("1h")
                    .bloqueiaDiaInteiro(false)
                    .ativo(true)
                    .build(),
                Pacote.builder()
                    .nome("Ensaio Premium")
                    .descricao("Ensaio com fotos e um vídeo de making of")
                    .quantidadeFotos(30)
                    .quantidadeVideos(1)
                    .valorBase(new BigDecimal("450.00"))
                    .duracaoEstimada("2h")
                    .bloqueiaDiaInteiro(false)
                    .ativo(true)
                    .build(),
                Pacote.builder()
                    .nome("Ensaio Completo")
                    .descricao("Ensaio completo com fotos, vídeos e dia exclusivo")
                    .quantidadeFotos(50)
                    .quantidadeVideos(2)
                    .valorBase(new BigDecimal("700.00"))
                    .duracaoEstimada("4h")
                    .bloqueiaDiaInteiro(true)
                    .ativo(true)
                    .build(),
                Pacote.builder()
                    .nome("Book Profissional")
                    .descricao("Book fotográfico profissional com equipe completa")
                    .quantidadeFotos(80)
                    .quantidadeVideos(3)
                    .valorBase(new BigDecimal("1200.00"))
                    .duracaoEstimada("6h")
                    .bloqueiaDiaInteiro(true)
                    .ativo(true)
                    .build()
            ));
        }

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
