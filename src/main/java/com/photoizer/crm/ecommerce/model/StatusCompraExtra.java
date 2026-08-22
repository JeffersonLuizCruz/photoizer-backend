package com.photoizer.crm.ecommerce.model;

/**
 * PATTERN: State Pattern
 * Cada enum value encapsula as transições de estado válidas para uma CompraExtra.
 * Motivo: centralizar a validação de transições de estado em um único lugar,
 * eliminando os if/else espalhados no EcommerceService. Cada estado define
 * quais operações são permitidas e para qual estado pode transicionar.
 */
public enum StatusCompraExtra {

    AGUARDANDO_COMPROVANTE {
        @Override
        public StatusCompraExtra proximoAoComprovanteEnviado() {
            return AGUARDANDO_CONFIRMACAO;
        }

        @Override
        public StatusCompraExtra proximoAoCancelar() {
            return CANCELADA;
        }

        @Override
        public boolean podeSerCancelada() {
            return true;
        }
    },

    AGUARDANDO_CONFIRMACAO {
        @Override
        public StatusCompraExtra proximoAoComprovanteEnviado() {
            return this;
        }

        @Override
        public StatusCompraExtra proximoAoCancelar() {
            return CANCELADA;
        }

        @Override
        public boolean podeSerCancelada() {
            return true;
        }
    },

    PAGA {
        @Override
        public StatusCompraExtra proximoAoComprovanteEnviado() {
            throw new IllegalStateException("Compra já paga não pode receber comprovante");
        }

        @Override
        public StatusCompraExtra proximoAoCancelar() {
            throw new IllegalStateException("Compra já paga não pode ser cancelada");
        }

        @Override
        public boolean podeSerCancelada() {
            return false;
        }
    },

    CANCELADA {
        @Override
        public StatusCompraExtra proximoAoComprovanteEnviado() {
            throw new IllegalStateException("Compra cancelada não pode receber comprovante");
        }

        @Override
        public StatusCompraExtra proximoAoCancelar() {
            throw new IllegalStateException("Compra já cancelada");
        }

        @Override
        public boolean podeSerCancelada() {
            return false;
        }
    };

    public abstract StatusCompraExtra proximoAoComprovanteEnviado();

    public abstract StatusCompraExtra proximoAoCancelar();

    public abstract boolean podeSerCancelada();
}
