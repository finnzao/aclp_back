package br.jus.tjba.aclp.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UsuarioEmailTest {

    @Test
    void setterNormalizaEmail() {
        Usuario u = new Usuario();
        u.setEmail("  AdminRioReal@TJBA.jus.br ");
        assertEquals("adminrioreal@tjba.jus.br", u.getEmail());
        u.setEmail(null);
        assertNull(u.getEmail());
    }

    @Test
    void builderEscapaDoSetterMasHookNormaliza() {
        Usuario u = Usuario.builder().email(" MISTO@Caixa.com ").build();
        assertEquals(" MISTO@Caixa.com ", u.getEmail()); // builder grava direto no campo
        u.prePersist();
        assertEquals("misto@caixa.com", u.getEmail());
    }
}
