package Unique_Castle.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "MEMBER_AUDIT_LOG")
public class MemberAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    private String adminUsername;

    private Long memberId;

    private String username;

    private String flatNumber;

    @Column(nullable = false)
    private String action; // CREATE_MEMBER, UPDATE_MEMBER, ACTIVATE_ACCOUNT, DEACTIVATE_ACCOUNT, RESET_PASSWORD, ARCHIVE_MEMBER, DELETE_MEMBER

    @Column(length = 1000)
    private String details;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
