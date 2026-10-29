package Unique_Castle.Entity;

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
@Table(name = "L_FLATS")
public class L_Flat {

    @Id
    private Long flatId;

    @Column(nullable = false, unique = true)
    private String flatNumber;



}


