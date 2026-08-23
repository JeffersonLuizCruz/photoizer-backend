package com.photoizer.crm.despesa.model;

import com.photoizer.crm.despesa.exception.StatusDespesaInvalidoException;

/**
 * PATTERN: State Pattern
 *
 * Cada enum value encapsula as transições de estado válidas para uma Despesa.
 * Motivo: centralizar a validação de transições de estado em um único lugar,
 * eliminando if/else espalhados no DespesaService (DEBT.md §10 — "máquinas
 * de estado sem validação central"). Cada estado define quais operações
 * são permitidas e para qual estado pode transicionar.
 *
 * Consistente com StatusCompraExtra (ecommerce) e StatusEdicao (edicao),
 * que já adotam o mesmo padrão no projeto.
 *
 * Transições válidas:
 *   PENDENTE → PAGO (pagamento direto)
 *   PENDENTE → RECORRENTE (configuração de recorrência)
 *   RECORRENTE → (geração automática cria ocorrência PENDENTE separada)
 *   PAGO → (estado final, sem transição)
 */
public enum StatusDespesa {

    PAGO {
        @Override
        public StatusDespesa transicionarParaPagamento() {
            throw new StatusDespesaInvalidoException(this, "marcar como paga");
        }

        @Override
        public boolean podeSerPaga() {
            return false;
        }
    },

    PENDENTE {
        @Override
        public StatusDespesa transicionarParaPagamento() {
            return PAGO;
        }

        @Override
        public boolean podeSerPaga() {
            return true;
        }
    },

    RECORRENTE {
        @Override
        public StatusDespesa transicionarParaPagamento() {
            throw new StatusDespesaInvalidoException(this, "marcar como paga");
        }

        @Override
        public boolean podeSerPaga() {
            return false;
        }
    };

    /**
     * Transiciona para PAGO se a transição for válida.
     * @return novo status após transição
     * @throws StatusDespesaInvalidoException se a transição não for permitida
     */
    public abstract StatusDespesa transicionarParaPagamento();

    /**
     * Verifica se este status permite operação de pagamento.
     */
    public abstract boolean podeSerPaga();
}
