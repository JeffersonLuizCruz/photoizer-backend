# CarolCRM (backend)

API REST do CRM Photoizer para estúdio de fotografia (clientes, agenda, pacotes,
financeiro, e-commerce, comissões e notificações).

Stack: Java 25 · Spring Boot 4.1 · Spring Modulith · Spring Data JPA · Spring Security ·
PostgreSQL 16 + Flyway.

---

## Pré-requisitos

- **Docker** e **Docker Compose** (usados para subir o PostgreSQL).
- **JDK 25**.
- **Maven 3.9.x** do sistema — **não existe `./mvnw`** neste repositório (apenas `mvnw.cmd`, do Windows).

---

## Como executar (Docker para o banco + backend local)

Todos os ambientes usam **PostgreSQL + Flyway** (incluindo `dev`); não há mais H2.
O profile `dev` já é o padrão (`spring.profiles.active=dev`).

### 1. Subir o PostgreSQL

Dentro de `photoizer-backend/`:

```bash
docker compose -f docker-compose.dev.yml up -d
```

Isso sobe um Postgres 16 em `localhost:5432` com:

| Banco | Usuário | Senha |
|-------|---------|-------|
| `photoizer` | `photoizer` | `photoizer` |

O volume persistente fica em `photoizer-dev-pgdata`.

### 2. Subir o backend

```bash
mvn spring-boot:run
```

Na primeira subida, o **Flyway** aplica as migrações de `src/main/resources/db/migration`
(começando por `V1__baseline.sql`) e o `AuthUserSeeder` cria os usuários de desenvolvimento
(tabela `users` vazia).

- API: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Healthcheck: <http://localhost:8080/actuator/health>

### 3. Encerrar

```bash
# parar o backend: Ctrl+C no terminal do mvn

# parar o banco
docker compose -f docker-compose.dev.yml down

# parar e apagar os dados (reset total do schema na próxima subida)
docker compose -f docker-compose.dev.yml down -v
```

---

## Usuários para autenticação

A senha inicial de todos é `dev123`.

**Semeados pelo Flyway em todos os ambientes** (`V2__seed_usuarios_iniciais.sql` — dev, homolog e prod),
criados junto com o schema:

| Papel | E-mail | Senha |
|-------|--------|-------|
| ADMIN | `admin@photoizer.com` | `dev123` |
| AGENDADOR | `agendamento@photoizer.com` | `dev123` |

**Somente em `dev`** (`AuthUserSeeder`, complementa os papéis de teste):

| Papel | E-mail | Senha |
|-------|--------|-------|
| FOTOGRAFO | `fotografo.teste@photoizer.com` | `dev123` |
| EDITOR | `editor.teste@photoizer.com` | `dev123` |

> **Troque a senha após o primeiro acesso.** Ainda **não existe** tela/endpoint de troca de senha;
> a troca exige um endpoint novo ou um `UPDATE` direto na tabela `users` (com hash BCrypt).
> Demais usuários (fotógrafos/editores/agendadores reais) devem ser cadastrados pela tela de
> Parceiros/Usuários. Para recriar os usuários de dev do zero, faça o reset com `down -v` (acima).

**AGENDADOR sem métricas internas:** o papel `AGENDADOR` não tem acesso às métricas
financeiras internas do estúdio (receita, despesas, comissões, repasses, lucro e faturamento).
Ele continua enxergando os valores de atendimento ao cliente (valor do pacote, entrada e saldo
do agendamento).

### Cliente (e-commerce)

Não há cliente semeado. O cliente se autorregistra em `POST /api/v1/auth/cliente/registro`
e faz login em `POST /api/v1/auth/cliente/login` (no frontend, rota `/acesso-cliente`).

---

## Alternativa: backend em container

```bash
docker build -t photoizer-backend .

# usa o Postgres local do compose; em dev o segredo JWT tem default descartável
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/photoizer \
  -e SPRING_DATASOURCE_USERNAME=photoizer \
  -e SPRING_DATASOURCE_PASSWORD=photoizer \
  photoizer-backend
```

---

## Outros comandos

```bash
mvn test                       # testes (H2 em memória; não exige Postgres)
mvn clean package              # build + testes
mvn -DskipTests package        # build sem testes
java -jar target/*.jar         # executa o JAR gerado
```

Variáveis de ambiente aceitas (ver `.env.example`): `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`,
`ADMIN_EMAIL`/`ADMIN_PASSWORD` (bootstrap em homolog/prod), `UPLOAD_DIR`, `LOG_PATH`.
