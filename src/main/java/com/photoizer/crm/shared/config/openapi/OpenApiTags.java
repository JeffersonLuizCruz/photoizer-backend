package com.photoizer.crm.shared.config.openapi;

import io.swagger.v3.oas.models.tags.Tag;

import java.util.List;

/**
 * Catálogo canônico das tags do Swagger UI.
 *
 * <p>A ordem desta lista define a ordem de exibição dos grupos no Swagger UI e
 * as descrições aqui declaradas são a fonte de verdade. Cada controller deve
 * usar exatamente o mesmo {@code name} em {@code @Tag(name = ...)}, caso
 * contrário o grupo aparece duplicado. Ver {@code docs/API_DOCUMENTATION_STANDARD.md}.
 */
public final class OpenApiTags {

    public static final String AUTENTICACAO = "Autenticação";
    public static final String USUARIOS = "Usuários";
    public static final String AUTH_CLIENTE = "Auth Cliente";
    public static final String CLIENTES = "Clientes";
    public static final String AGENDAMENTOS = "Agendamentos";
    public static final String FOTOGRAFOS_AGENDAMENTO = "Fotógrafos do Agendamento";
    public static final String PROPOSTAS_PUBLICAS = "Propostas Públicas";
    public static final String REPASSES = "Repasses";
    public static final String PACOTES = "Pacotes";
    public static final String FOTOS = "Fotos";
    public static final String ECOMMERCE = "Ecommerce";
    public static final String ADMIN_ECOMMERCE = "Admin Ecommerce";
    public static final String ADMIN_COMPRAS = "Admin Compras";
    public static final String ADMIN_COMENTARIOS = "Admin Comentários";
    public static final String ADMIN_ANALYTICS = "Admin Analytics";
    public static final String AVALIACOES = "Avaliações";
    public static final String FINANCEIRO = "Financeiro";
    public static final String RELATORIOS_FINANCEIROS = "Relatórios Financeiros";
    public static final String COMISSOES = "Comissões";
    public static final String INDICADORES = "Indicadores";
    public static final String FOTOGRAFOS = "Fotógrafos";
    public static final String PARCEIROS = "Parceiros";
    public static final String DESPESAS = "Despesas";
    public static final String DASHBOARD = "Dashboard";
    public static final String CONFIG = "Config";
    public static final String TEMPLATE_PROPOSTA = "Template da Proposta";
    public static final String DOCUMENTOS = "Documentos";
    public static final String NOTIFICACOES = "Notificações";
    public static final String SESSAO = "Sessões";
    public static final String DEV = "Dev Pagamentos";

    private OpenApiTags() {
    }

    public static List<Tag> all() {
        return List.of(
            new Tag().name(AUTENTICACAO).description("Login, refresh token, logout e dados do usuário autenticado (staff)."),
            new Tag().name(USUARIOS).description("Gestão de usuários do sistema (staff)."),
            new Tag().name(AUTH_CLIENTE).description("Cadastro e login de clientes do e-commerce."),
            new Tag().name(CLIENTES).description("Gestão de clientes (admin)."),
            new Tag().name(AGENDAMENTOS).description("Gestão de agendamentos: proposta, aprovação, pagamento final e termo."),
            new Tag().name(FOTOGRAFOS_AGENDAMENTO).description("Fotógrafos vinculados a um ensaio (repasses por ensaio)."),
            new Tag().name(PROPOSTAS_PUBLICAS).description("Página pública de assinatura da proposta por token."),
            new Tag().name(REPASSES).description("Gestão de repasses pendentes para fotógrafos."),
            new Tag().name(PACOTES).description("Gestão de pacotes de ensaio."),
            new Tag().name(FOTOS).description("Gestão e publicação de fotos do ensaio."),
            new Tag().name(ECOMMERCE).description("Galeria pública do cliente: seleção, carrinho, checkout e download."),
            new Tag().name(ADMIN_ECOMMERCE).description("Gestão administrativa do e-commerce de fotos."),
            new Tag().name(ADMIN_COMPRAS).description("Gestão administrativa das compras de fotos extras."),
            new Tag().name(ADMIN_COMENTARIOS).description("Moderação de comentários dos clientes por foto."),
            new Tag().name(ADMIN_ANALYTICS).description("Métricas e analytics do e-commerce."),
            new Tag().name(AVALIACOES).description("Avaliações e depoimentos."),
            new Tag().name(FINANCEIRO).description("Controle financeiro: receitas, lançamentos e fotos extras."),
            new Tag().name(RELATORIOS_FINANCEIROS).description("Relatórios financeiros agregados para exportação."),
            new Tag().name(COMISSOES).description("Consulta de comissões por indicação."),
            new Tag().name(INDICADORES).description("CRUD de indicadores (pessoas que indicam clientes)."),
            new Tag().name(FOTOGRAFOS).description("Gestão financeira de fotógrafos (remuneração e repasses)."),
            new Tag().name(PARCEIROS).description("Usuários elegíveis a receber repasse em um ensaio."),
            new Tag().name(DESPESAS).description("Despesas, categorias e comprovantes."),
            new Tag().name(DASHBOARD).description("Métricas e gráficos do dashboard."),
            new Tag().name(CONFIG).description("Configurações globais do sistema."),
            new Tag().name(TEMPLATE_PROPOSTA).description("Template do termo de prestação de serviços."),
            new Tag().name(DOCUMENTOS).description("Geração de contratos e recibos em PDF."),
            new Tag().name(NOTIFICACOES).description("Notificações do sistema para usuários."),
            new Tag().name(SESSAO).description("Sessões fotográficas."),
            new Tag().name(DEV).description("Simulação de pagamento — disponível apenas no perfil de desenvolvimento.")
        );
    }
}
