package br.jus.tjba.aclp.controller;

import br.jus.tjba.aclp.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        Map<String, String> healthData = Map.of(
                "status", "OK",
                "timestamp", String.valueOf(System.currentTimeMillis())
        );
        return ResponseEntity.ok(
                ApiResponse.success("API funcionando corretamente", healthData)
        );
    }

    @GetMapping("/info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> info() {
        Map<String, Object> infoData = Map.of(
                "application", "ACLP",
                "version", "1.0.0",
                "timestamp", System.currentTimeMillis()
        );
        return ResponseEntity.ok(
                ApiResponse.success("Informações do sistema", infoData)
        );
    }
}