package Unique_Castle.Config;

import Unique_Castle.Entity.ServiceFee;
import Unique_Castle.Repository.ServiceFeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
@Transactional
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private ServiceFeeRepository serviceFeeRepository;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Application started. Preserving existing database users, flats, and records without modification.");

        // Ensure October 2026 Service Fee exists if no fee configured for the month
        LocalDate october2026 = LocalDate.of(2026, 10, 1);
        serviceFeeRepository.findByMonth(october2026)
                .or(() -> serviceFeeRepository.findByCurrentMonth(october2026, october2026.plusMonths(1)))
                .orElseGet(() -> {
                    ServiceFee sf = ServiceFee.builder()
                            .month(october2026)
                            .DueDate(LocalDate.of(2026, 10, 10))
                            .caretakerFee(BigDecimal.valueOf(800))
                            .wasteFee(BigDecimal.valueOf(200))
                            .liftFee(BigDecimal.valueOf(500))
                            .waterFee(BigDecimal.ZERO)
                            .electricityFee(BigDecimal.ZERO)
                            .otherFee(BigDecimal.valueOf(1000))
                            .totalFee(BigDecimal.valueOf(2500))
                            .status("PUBLISHED")
                            .publishedDate(october2026)
                            .build();
                    ServiceFee saved = serviceFeeRepository.save(sf);
                    logger.info("Initialized default ServiceFee for October 2026: BDT 2,500");
                    return saved;
                });
    }
}
