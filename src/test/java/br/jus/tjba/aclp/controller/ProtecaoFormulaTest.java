package br.jus.tjba.aclp.controller;

import org.junit.jupiter.api.Test;

import static br.jus.tjba.aclp.controller.ExportacaoController.protegerFormula;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CSV/Formula Injection: valor exportado que comece com = + - @ (ou TAB/CR) é avaliado
 * como fórmula ao abrir a planilha. O prefixo com apóstrofo força texto literal.
 */
class ProtecaoFormulaTest {

    @Test
    void prefixosPerigososSaoNeutralizados() {
        assertEquals("'=HYPERLINK(\"http://malicioso\")", protegerFormula("=HYPERLINK(\"http://malicioso\")"));
        assertEquals("'+1+1", protegerFormula("+1+1"));
        assertEquals("'-2+3", protegerFormula("-2+3"));
        assertEquals("'@SUM(A1)", protegerFormula("@SUM(A1)"));
    }

    @Test
    void textoComumNaoEhAlterado() {
        assertEquals("MARIA CRISTINA DA SILVA", protegerFormula("MARIA CRISTINA DA SILVA"));
        assertEquals("Rua 7 de Setembro, 100", protegerFormula("Rua 7 de Setembro, 100"));
        assertEquals("054.545.665-78", protegerFormula("054.545.665-78"));
    }

    @Test
    void nuloEVazioPassamIntactos() {
        assertNull(protegerFormula(null));
        assertEquals("", protegerFormula(""));
    }
}
