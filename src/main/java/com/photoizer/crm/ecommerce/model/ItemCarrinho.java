package com.photoizer.crm.ecommerce.model;

import com.photoizer.crm.shared.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "itens_carrinho")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class ItemCarrinho extends BaseEntity {

    @NotNull
    @Column(nullable = false)
    private UUID agendamentoId;

    @NotNull
    @Column(nullable = false)
    private UUID fotoId;

    @NotNull
    @Column(nullable = false)
    private UUID sessionId;
}
