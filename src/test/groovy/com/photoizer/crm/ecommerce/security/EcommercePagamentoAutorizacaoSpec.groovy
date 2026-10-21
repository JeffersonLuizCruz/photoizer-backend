package com.photoizer.crm.ecommerce.security

import com.photoizer.crm.ecommerce.api.AdminPagamentoEcommerceController
import com.photoizer.crm.ecommerce.api.AdminComprasController
import jakarta.annotation.security.RolesAllowed
import spock.lang.Specification

class EcommercePagamentoAutorizacaoSpec extends Specification {

    def "configuracao de pagamento exige ADMIN/FOTOGRAFO"() {
        given:
        def roles = AdminPagamentoEcommerceController.getAnnotation(RolesAllowed)

        expect:
        roles != null
        roles.value().toList().sort() == ["ADMIN", "FOTOGRAFO"]
    }

    def "atualizacao de link de pagamento usa os mesmos papeis das compras"() {
        given:
        def roles = AdminComprasController.getAnnotation(RolesAllowed)

        expect:
        roles != null
        roles.value().toList().sort() == ["ADMIN", "FOTOGRAFO"]
    }
}
