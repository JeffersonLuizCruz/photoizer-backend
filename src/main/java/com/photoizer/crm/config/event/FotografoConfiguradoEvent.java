package com.photoizer.crm.config.event;

/**
 * Evento publicado quando o nome do fotógrafo é configurado na tela de
 * Configuração (chave {@code nomeFotografo}).
 *
 * PATTERN: Application Event (Modulith) — o módulo config é fundacional e não
 * pode depender de módulos de domínio. Quem reage a este evento (ex.: módulo
 * fotografo) provisiona/atualiza o usuário com papel FOTOGRAFO.
 */
public record FotografoConfiguradoEvent(String nome) {
}
