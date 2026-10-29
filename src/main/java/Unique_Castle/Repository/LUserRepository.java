package Unique_Castle.Repository;

import Unique_Castle.Entity.L_User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LUserRepository extends JpaRepository<L_User, Long> {
}
