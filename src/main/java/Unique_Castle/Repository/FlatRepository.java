package Unique_Castle.Repository;

import Unique_Castle.Entity.Flat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FlatRepository extends JpaRepository<Flat, Long> {

    boolean existsByFlatNumber(String flatNumber);

    Optional<Flat> findByFlatNumber(String flatNumber);

    Optional<Flat> findByFlatNumberIgnoreCase(String flatNumber);

    List<Flat> findByOwnerUserId(Long ownerId);

    List<Flat> findByOwnerPhoneNumber(String phoneNumber);

    List<Flat> findByIsArchivedFalse();

    Optional<Flat> findByFlatNumberIgnoreCaseAndIsArchivedFalse(String flatNumber);

    Page findAll(Pageable pageable);

}
