package com.photoizer.crm.indicador.seed;

import com.photoizer.crm.indicador.repository.IndicadorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * PATTERN: Composite Seeder (CommandLineRunner + @Order)
 *
 * Seeder responsável por limpar indicadores duplicados (nome + telefone).
 * Não cria dados — apenas opera de limpeza/migração.
 *
 * Idempotência: query HQL GROUP BY ... HAVING COUNT > 1; no-op se nenhum duplicado.
 * @Order(20) — independente de outros seeders.
 * @Profile("!prod") — não roda em produção (evita modificação acidental).
 */
@Component
@Order(20)
@Profile("!prod")
public class IndicadorCleanupSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(IndicadorCleanupSeeder.class);

    private final IndicadorRepository indicadorRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public IndicadorCleanupSeeder(IndicadorRepository indicadorRepository) {
        this.indicadorRepository = indicadorRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        limparIndicadoresDuplicados();
    }

    private void limparIndicadoresDuplicados() {
        var duplicados = entityManager.createQuery(
            "SELECT i.nome, i.telefone FROM Indicador i GROUP BY i.nome, i.telefone HAVING COUNT(i) > 1",
            Object[].class
        ).getResultList();

        if (duplicados.isEmpty()) return;

        int totalRemovidos = 0;
        for (var par : duplicados) {
            var nome = (String) ((Object[]) par)[0];
            var telefone = (String) ((Object[]) par)[1];
            var indicadores = indicadorRepository.findAllByNomeAndTelefone(nome, telefone);
            indicadores.sort(Comparator.comparing(
                i -> i.getAuditInfo().getCreatedAt() != null ? i.getAuditInfo().getCreatedAt() : LocalDateTime.MIN));
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
