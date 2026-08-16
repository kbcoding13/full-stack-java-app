package com.example.inventory.config;

import com.example.inventory.category.Category;
import com.example.inventory.category.CategoryRepository;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.stock.MovementType;
import com.example.inventory.stock.StockMovement;
import com.example.inventory.stock.StockMovementRepository;
import com.example.inventory.supplier.Supplier;
import com.example.inventory.supplier.SupplierRepository;
import com.example.inventory.user.Role;
import com.example.inventory.user.User;
import com.example.inventory.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo data for local development, so a fresh database is usable immediately instead of
 * presenting an empty product list.
 *
 * <p>Gated behind the {@code seed} profile rather than a Flyway migration on purpose: migrations
 * run in every environment and this data has no business being in one. Run with
 * {@code ./gradlew bootRun --args='--spring.profiles.active=seed'}.
 *
 * <p>Idempotent per SKU, so re-running it tops up what is missing without duplicating what is
 * already there or skipping everything because one product happens to exist.
 *
 * <p>Opening balances are written as stock movements, never as a quantity column — the same rule
 * the rest of the system follows.
 */
@Component
@Profile("seed")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private record Seed(
            String sku,
            String name,
            String description,
            String price,
            int reorder,
            String category,
            String supplier,
            int opening,
            int sold) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("TL-1001", "Claw Hammer 16oz", "Fibreglass handle", "18.99", 10, "Tools", "Acme Supply Co", 60, 12),
            new Seed("TL-1002", "Cordless Drill 18V", "Two batteries", "129.00", 4, "Tools", "Acme Supply Co", 20, 6),
            new Seed("TL-1003", "Tape Measure 5m", "Auto-lock", "9.50", 15, "Tools", "Northwind Traders", 80, 25),
            new Seed("FS-2001", "Wood Screws 4x40mm", "Box of 200", "6.25", 25, "Fasteners", "Northwind Traders", 200, 60),
            // Deliberately ends below its reorder level so the low-stock filter has something to show.
            new Seed("FS-2002", "Hex Bolts M8", "Box of 100", "11.75", 30, "Fasteners", "Northwind Traders", 40, 28),
            new Seed("SF-3001", "Safety Goggles", "Anti-fog", "7.40", 20, "Safety", "Acme Supply Co", 50, 10),
            new Seed("SF-3002", "Work Gloves L", "Cut resistant", "12.00", 12, "Safety", "Acme Supply Co", 15, 9));

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            StockMovementRepository movementRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Spring calls this through the proxy, so the transaction below is the one that counts —
     * seed() invoked from here runs inside it rather than starting its own.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Returns the number of products created; zero means everything was already present. */
    @Transactional
    public int seed() {
        seedUsers();

        int created = 0;
        for (Seed seed : SEEDS) {
            if (productRepository.findBySkuIgnoreCaseAndDeletedAtIsNull(seed.sku()).isPresent()) {
                continue;
            }
            createProductWithHistory(seed);
            created++;
        }

        if (created > 0) {
            log.info("Seeded {} demo product(s)", created);
        } else {
            log.info("Demo data already present — nothing to seed");
        }
        return created;
    }

    private void seedUsers() {
        if (!userRepository.existsByEmailIgnoreCase("admin@example.com")) {
            userRepository.save(new User(
                    "admin@example.com", passwordEncoder.encode("password123"), "Demo Admin", Role.ADMIN));
            log.info("Seeded admin@example.com / password123");
        }
        if (!userRepository.existsByEmailIgnoreCase("staff@example.com")) {
            userRepository.save(new User(
                    "staff@example.com", passwordEncoder.encode("password123"), "Demo Staff", Role.STAFF));
        }
    }

    private void createProductWithHistory(Seed seed) {
        Product product = new Product(
                seed.sku(), seed.name(), seed.description(), new BigDecimal(seed.price()), seed.reorder());
        product.setCategory(resolveCategory(seed.category()));
        product.setSupplier(resolveSupplier(seed.supplier()));
        productRepository.saveAndFlush(product);

        Instant now = Instant.now();

        movementRepository.save(new StockMovement(
                product,
                MovementType.IN,
                seed.opening(),
                "Opening balance",
                "SEED",
                now.minus(30, ChronoUnit.DAYS),
                "seed@example.com"));

        if (seed.sold() > 0) {
            movementRepository.save(new StockMovement(
                    product,
                    MovementType.OUT,
                    -seed.sold(),
                    "Customer orders",
                    "SEED",
                    now.minus(3, ChronoUnit.DAYS),
                    "seed@example.com"));
        }
    }

    private Category resolveCategory(String name) {
        return categoryRepository
                .findFirstByNameIgnoreCaseAndDeletedAtIsNull(name)
                .orElseGet(() -> categoryRepository.save(new Category(name, null)));
    }

    private Supplier resolveSupplier(String name) {
        return supplierRepository
                .findFirstByNameIgnoreCaseAndDeletedAtIsNull(name)
                .orElseGet(() -> supplierRepository.save(new Supplier(name, null, null, null)));
    }
}
