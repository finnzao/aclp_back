package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.service.SetupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller para servir páginas HTML do setup
 */
@Controller
@RequestMapping("/setup")
@RequiredArgsConstructor
@Slf4j
public class SetupViewController {

    private final SetupService setupService;

    @GetMapping
    public String setupPage(Model model) {
        log.info("Acessando página de setup");

        // Verificar se setup ainda é necessário
        if (!setupService.isSetupRequired()) {
            log.info("Setup já concluído, redirecionando para home");
            return "redirect:/";
        }

        // Adicionar informações para a view
        model.addAttribute("appName", "ACLP - Sistema TJBA");
        model.addAttribute("setupRequired", true);

        return "setup/wizard";
    }

    @GetMapping("/success")
    public String setupSuccess(Model model) {
        log.info("Acessando página de sucesso do setup");

        model.addAttribute("appName", "ACLP - Sistema TJBA");

        return "setup/success";
    }
}