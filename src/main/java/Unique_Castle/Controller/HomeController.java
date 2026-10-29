package Unique_Castle.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "Unique Castle Apartment Management API");
        response.put("timestamp", LocalDateTime.now());
        response.put("documentation", "/api/swagger-ui/index.html");
        response.put("frontend", "http://localhost:5173");

        Map<String, String> demoAccounts = new LinkedHashMap<>();
        demoAccounts.put("admin", "admin / password123 (or 123456)");
        demoAccounts.put("owner", "owner1 / password123 (Flat 4B)");
        response.put("demoAccounts", demoAccounts);

        return ResponseEntity.ok(response);
    }
}
