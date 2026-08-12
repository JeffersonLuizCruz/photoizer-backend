package com.photoizer.crm.contrato.event;

import java.util.UUID;

public record ContratoDevolvidoEvent(UUID contratoId, String tipoMotivo, String motivo) {
}