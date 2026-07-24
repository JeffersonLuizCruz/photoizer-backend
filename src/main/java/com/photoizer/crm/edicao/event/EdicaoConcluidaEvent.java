package com.photoizer.crm.edicao.event;

import java.util.UUID;

public record EdicaoConcluidaEvent(
    UUID agendamentoId
) {
}
