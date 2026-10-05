package com.photoizer.crm.tools;

import jakarta.persistence.Entity;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.SourceType;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExceptionHandler;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaCreator;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.hibernate.tool.schema.spi.ScriptSourceInput;
import org.hibernate.tool.schema.spi.ScriptTargetOutput;
import org.hibernate.tool.schema.spi.SourceDescriptor;
import org.hibernate.tool.schema.spi.TargetDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Ferramenta de uso unico: gera o DDL PostgreSQL (baseline Flyway) a partir das
 * entidades JPA, sem precisar de um banco vivo. NAO e um teste funcional.
 *
 * Uso: mvn -Dtest=SchemaBaselineGenerator test
 * Saida: target/schema-postgres.sql (revisar antes de virar db/migration/V1__baseline.sql)
 *
 * <p>O nome NAO termina em "Test" de proposito: o Surefire nao o executa no
 * build padrao; rode explicitamente com -Dtest=SchemaBaselineGenerator.
 */
class SchemaBaselineGenerator {

    @Test
    void gerar() throws Exception {
        var configValues = new HashMap<String, Object>();
        configValues.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configValues.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        configValues.put("hibernate.format_sql", "true");
        configValues.put("hibernate.physical_naming_strategy",
            "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl");
        configValues.put("hibernate.implicit_naming_strategy",
            "org.springframework.boot.hibernate.SpringImplicitNamingStrategy");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
            .applySetting("hibernate.format_sql", "true")
            .applySetting("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
            .applySetting("hibernate.implicit_naming_strategy",
                "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
            .build();

        var sources = new MetadataSources(registry);

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var bd : scanner.findCandidateComponents("com.photoizer.crm")) {
            sources.addAnnotatedClass(Class.forName(bd.getBeanClassName()));
        }

        // Entidades do Spring Modulith (Event Publication Registry) ficam fora do
        // pacote do CRM; precisam ser registradas explicitamente para entrarem no baseline.
        sources.addAnnotatedClass(org.springframework.modulith.events.jpa.updating.DefaultJpaEventPublication.class);
        sources.addAnnotatedClass(org.springframework.modulith.events.jpa.archiving.ArchivedJpaEventPublication.class);

        Metadata metadata = sources.buildMetadata();

        var script = new StringBuilder();
        ScriptTargetOutput targetOutput = new ScriptTargetOutput() {
            public void prepare() {}
            public void accept(String command) { script.append(command); }
            public void release() {}
        };

        TargetDescriptor target = new TargetDescriptor() {
            public EnumSet<TargetType> getTargetTypes() { return EnumSet.of(TargetType.SCRIPT); }
            public ScriptTargetOutput getScriptTargetOutput() { return targetOutput; }
        };

        SourceDescriptor source = new SourceDescriptor() {
            public SourceType getSourceType() { return SourceType.METADATA; }
            public ScriptSourceInput getScriptSourceInput() { return null; }
        };

        ExceptionHandler exceptionHandler = ExceptionHandlerLoggedImpl.INSTANCE;
        ExecutionOptions options = new ExecutionOptions() {
            public Map<String, Object> getConfigurationValues() { return configValues; }
            public boolean shouldManageNamespaces() { return false; }
            public ExceptionHandler getExceptionHandler() { return exceptionHandler; }
        };

        var tool = registry.getService(SchemaManagementTool.class);
        SchemaCreator creator = tool.getSchemaCreator(configValues);
        creator.doCreation(metadata, options, ContributableMatcher.ALL, source, target);

        Path out = Path.of("target", "schema-postgres.sql");
        Files.createDirectories(out.getParent());
        try {
            Files.writeString(out, script.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("DDL gerado em: " + out.toAbsolutePath());
    }
}
