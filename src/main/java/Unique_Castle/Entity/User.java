package Unique_Castle.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "USERS",
uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"),
                @UniqueConstraint(columnNames = "PhoneNumber")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true, nullable = false)
    private String username;

    private String password;

    @Column(unique = true , nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Role role; // ADMIN / OWNER / EDITOR

    @Column(nullable = true)
    private String name;

    @Column(nullable = true)
    private String email;

    @Column(nullable = false)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE / INACTIVE

    @OneToMany(mappedBy = "owner")
    @JsonIgnore
    private List<Flat> flats; // List of flats for this owner

    public enum Role {
        ADMIN,
        OWNER,
        EDITOR
    }

    // UserDetails methods omitted
}
