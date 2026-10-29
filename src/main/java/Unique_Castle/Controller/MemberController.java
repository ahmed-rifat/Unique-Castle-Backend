package Unique_Castle.Controller;

import Unique_Castle.Entity.MemberAuditLog;
import Unique_Castle.RequestData.FlatMemberRequest;
import Unique_Castle.RequestData.ResetPasswordRequest;
import Unique_Castle.ResponseData.FlatMemberResponse;
import Unique_Castle.Service.MemberService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
public class MemberController {

    @Autowired
    private MemberService memberService;

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ADMIN")
                        || a.getAuthority().equalsIgnoreCase("SUPER_ADMIN")
                        || a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                        || a.getAuthority().equalsIgnoreCase("ROLE_SUPER_ADMIN"));
    }

    @GetMapping(value = {"/admin/members", "/members"})
    public ResponseEntity<?> getAllMembers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("status", "false", "message", "Admin access required"));
        }
        List<FlatMemberResponse> members = memberService.getAllMembers();
        if (name != null && !name.isBlank()) {
            String query = name.trim().toLowerCase();
            members = members.stream()
                    .filter(m -> m.getName() != null && m.getName().toLowerCase().contains(query))
                    .collect(java.util.stream.Collectors.toList());
        } else if (search != null && !search.isBlank()) {
            String query = search.trim().toLowerCase();
            members = members.stream()
                    .filter(m -> (m.getName() != null && m.getName().toLowerCase().contains(query))
                            || (m.getUsername() != null && m.getUsername().toLowerCase().contains(query))
                            || (m.getFlatNumber() != null && m.getFlatNumber().toLowerCase().contains(query))
                            || (m.getPhoneNumber() != null && m.getPhoneNumber().toLowerCase().contains(query)))
                    .collect(java.util.stream.Collectors.toList());
        }
        return ResponseEntity.ok(Map.of("status", "true", "data", members));
    }

    @GetMapping("/admin/members/{id}")
    public ResponseEntity<?> getMemberById(@PathVariable Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("status", "false", "message", "Admin access required"));
        }
        try {
            FlatMemberResponse member = memberService.getMemberById(id);
            return ResponseEntity.ok(Map.of("status", "true", "data", member));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("status", "false", "message", e.getMessage()));
        }
    }

    @PostMapping(value = {"/admin/members", "/members"})
    public ResponseEntity<?> createMember(@Valid @RequestBody FlatMemberRequest request, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("status", "false", "message", "Admin access required"));
        }
        try {
            String adminUser = authentication != null ? authentication.getName() : "admin";
            FlatMemberResponse created = memberService.createMember(request, adminUser);
            return ResponseEntity.ok(Map.of("status", "true", "message", "Member registered successfully", "data", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "false", "message", e.getMessage()));
        }
    }

    @PutMapping(value = {"/admin/members/{id}", "/members/{id}"})
    public ResponseEntity<?> updateMember(@PathVariable Long id, @RequestBody FlatMemberRequest request, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("status", "false", "message", "Admin access required"));
        }
        try {
            String adminUser = authentication != null ? authentication.getName() : "admin";
            FlatMemberResponse updated = memberService.updateMember(id, request, adminUser);
            return ResponseEntity.ok(Map.of("status", "true", "message", "Member updated successfully", "data", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "false", "message", e.getMessage()));
        }
    }

    @PatchMapping(value = {"/admin/members/{id}/status", "/members/{id}/status"})
    public ResponseEntity<?> toggleStatus(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("status", "false", "message", "Admin access required"));
        }
        String status = body.getOrDefault("status", "Active");
        try {
            String adminUser = authentication != null ? authentication.getName() : "admin";
            FlatMemberResponse updated = memberService.toggleAccountStatus(id, status, adminUser);
            return ResponseEntity.ok(Map.of("status", "true", "message", "Account status updated", "data", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", e.getMessage()));
        }
    }

    @PostMapping(value = {"/admin/members/{id}/reset-password", "/members/{id}/reset-password"})
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("status", "false", "message", "Admin access required"));
        }
        try {
            String adminUser = authentication != null ? authentication.getName() : "admin";
            Map<String, Object> result = memberService.resetPassword(id, request, adminUser);
            return ResponseEntity.ok(Map.of("status", "true", "message", result.get("message")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("status", "false", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "false", "message", e.getMessage()));
        }
    }

    @DeleteMapping(value = {"/admin/members/{id}", "/members/{id}"})
    public ResponseEntity<?> deleteMember(@PathVariable Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("status", "false", "message", "Admin access required"));
        }
        try {
            String adminUser = authentication != null ? authentication.getName() : "admin";
            Map<String, Object> result = memberService.deleteOrArchiveMember(id, adminUser);
            return ResponseEntity.ok(Map.of(
                    "status", "true",
                    "action", result.get("action"),
                    "message", result.get("message")
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "false", "message", e.getMessage()));
        }
    }

    @GetMapping(value = {"/admin/members/audit-logs", "/members/audit-logs"})
    public ResponseEntity<?> getAuditLogs(Authentication authentication) {
        if (!isAdmin(authentication)) {
            return ResponseEntity.status(403).body(Map.of("status", "false", "message", "Admin access required"));
        }
        List<MemberAuditLog> logs = memberService.getAuditLogs();
        return ResponseEntity.ok(Map.of("status", "true", "data", logs));
    }
}
