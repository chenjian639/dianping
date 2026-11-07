package com.dianping.service;

import com.dianping.dto.CanteenDTO;
import com.dianping.dto.NearbyCanteenQuery;
import com.dianping.dto.RestaurantLocationDTO;
import com.dianping.exception.BusinessException;
import com.dianping.mapper.CanteenMapper;
import com.dianping.mq.CanteenGeoConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic WGS84 fixtures near (0,0), not real campus coordinates. */
@Testcontainers
@SpringBootTest(classes = CanteenLocationIT.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CanteenLocationIT {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
    @MapperScan("com.dianping.mapper")
    @Import({CanteenService.class, CanteenGeoIndexService.class, NearbyCanteenService.class,
            RestaurantLocationService.class, OutboxEventService.class})
    static class Config {}

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("locations_test").withUsername("campus").withPassword("test_only");
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("spring.rabbitmq.username", () -> "test");
        registry.add("spring.rabbitmq.password", () -> "test");
    }

    @Autowired CanteenService service;
    @Autowired CanteenGeoIndexService index;
    @Autowired NearbyCanteenService nearby;
    @Autowired RestaurantLocationService locations;
    @Autowired CanteenMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void reset() {
        db.update("DELETE FROM restaurant");
        db.update("DELETE FROM canteen");
        db.update("DELETE FROM outbox_event");
        db.update("UPDATE canteen_geo_state SET revision=0 WHERE id=1");
        redis.delete(List.of(CanteenGeoIndexService.GEO_KEY, CanteenGeoIndexService.READY_KEY));
        db.update("INSERT INTO restaurant(id,name) VALUES(1,'Legacy stall'),(2,'Other stall')");
    }

    private CanteenDTO canteen(String name, String longitude, boolean enabled) {
        CanteenDTO dto = new CanteenDTO();
        dto.setName(name);
        dto.setAddress("Synthetic test address");
        dto.setLongitude(new BigDecimal(longitude));
        dto.setLatitude(BigDecimal.ZERO);
        dto.setEntranceInstructions("Synthetic entrance");
        dto.setEnabled(enabled);
        return dto;
    }

    private NearbyCanteenQuery query(int radius, int limit) {
        NearbyCanteenQuery query = new NearbyCanteenQuery();
        query.setLongitude(BigDecimal.ZERO);
        query.setLatitude(BigDecimal.ZERO);
        query.setRadiusMeters(radius);
        query.setLimit(limit);
        return query;
    }

    @Test
    void missingIndexFallsBackAndRebuiltIndexHasIdenticalFilteringOrderingAndDistances() {
        service.create(canteen("Disabled nearest", "0", false));
        Long near = service.create(canteen("Near", "0.001", true));
        Long sameDistance = service.create(canteen("Same distance", "0.001", true));
        Long far = service.create(canteen("Far", "0.002", true));
        service.create(canteen("Outside", "1", true));
        var fallback = nearby.nearby(query(500, 3));
        assertEquals(List.of(near, sameDistance, far), fallback.stream().map(value -> value.id()).toList());
        assertEquals("STRAIGHT_LINE", fallback.get(0).distanceType());
        assertEquals("WGS84", fallback.get(0).coordinateSystem());
        assertTrue(fallback.get(0).distanceMeters() > 100 && fallback.get(0).distanceMeters() < 120);
        index.rebuild();
        assertEquals(fallback, nearby.nearby(query(500, 3)));
        assertEquals(java.util.Set.of(near, sameDistance),
                new java.util.HashSet<>(index.search(mapper.revision(), 4, 0, 0, 120)));
        assertTrue(nearby.nearby(query(50, 3)).isEmpty());
    }

    @Test
    void pendingChangesAndDuplicateLateEventsNeverRestoreOldCoordinatesOrDeletedMembers() {
        Long id = service.create(canteen("Moving", "0.001", true));
        CanteenGeoConsumer consumer = new CanteenGeoConsumer(index);
        consumer.handle(id);
        assertEquals(1, nearby.nearby(query(500, 20)).size());
        service.update(id, canteen("Moved", "1", true));
        // Redis is still old, but the committed DB revision forces fallback.
        assertTrue(nearby.nearby(query(500, 20)).isEmpty());
        consumer.handle(id);
        consumer.handle(id);
        assertTrue(nearby.nearby(query(500, 20)).isEmpty());
        service.update(id, canteen("Disabled", "0.001", false));
        consumer.handle(id);
        assertTrue(nearby.nearby(query(500, 20)).isEmpty());
        service.update(id, canteen("Enabled again", "0.001", true));
        consumer.handle(id);
        service.delete(id);
        consumer.handle(id);
        consumer.handle(id); // Represents even an original create event delivered after deletion.
        assertNull(mapper.selectById(id));
        assertTrue(nearby.nearby(query(500, 20)).isEmpty());
        assertEquals(0L, redis.opsForZSet().zCard(CanteenGeoIndexService.GEO_KEY));
    }

    @Test
    void rollbackLeavesNoFormalLocationRevisionOrOutboxAndDoesNotTouchRedis() {
        index.rebuild();
        String ready = redis.opsForValue().get(CanteenGeoIndexService.READY_KEY);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.create(canteen("Rolled back", "0.001", true));
            status.setRollbackOnly();
        });
        assertTrue(mapper.selectAll().isEmpty());
        assertEquals(0, mapper.revision());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class));
        assertEquals(ready, redis.opsForValue().get(CanteenGeoIndexService.READY_KEY));
        assertTrue(nearby.nearby(query(500, 20)).isEmpty());
    }

    @Test
    void outboxFailureRollsBackTheBusinessWriteAndRevision() {
        db.execute("ALTER TABLE outbox_event ADD CONSTRAINT reject_geo CHECK(event_type <> 'CANTEEN_GEO_CHANGED')");
        try {
            assertThrows(RuntimeException.class, () -> service.create(canteen("Failure", "0.001", true)));
            assertTrue(mapper.selectAll().isEmpty());
            assertEquals(0, mapper.revision());
            assertFalse(Boolean.TRUE.equals(redis.hasKey(CanteenGeoIndexService.READY_KEY)));
        } finally {
            db.execute("ALTER TABLE outbox_event DROP CHECK reject_geo");
        }
    }

    @Test
    void lostGeoKeyCannotMasqueradeAsReadyAndQueriesWithDifferentOriginsAreNotCachedTogether() {
        Long id = service.create(canteen("Near", "0.001", true));
        index.rebuild();
        redis.delete(CanteenGeoIndexService.GEO_KEY); // Keep ready marker, simulate independent key eviction.
        assertNull(index.search(mapper.revision(), 1, 0, 0, 500));
        assertEquals(id, nearby.nearby(query(500, 1)).get(0).id());
        NearbyCanteenQuery otherOrigin = query(500, 1);
        otherOrigin.setLongitude(BigDecimal.ONE);
        assertTrue(nearby.nearby(otherOrigin).isEmpty());
        assertEquals("1", redis.opsForValue().get(CanteenGeoIndexService.READY_KEY));
    }

    @Test
    void redisWrongTypeFallsBackInsteadOfFailingBusinessQuery() {
        Long id = service.create(canteen("Near", "0.001", true));
        redis.opsForValue().set(CanteenGeoIndexService.READY_KEY, String.valueOf(mapper.revision()));
        redis.opsForValue().set(CanteenGeoIndexService.GEO_KEY, "not-a-geo-index");
        assertEquals(id, nearby.nearby(query(500, 1)).get(0).id());
        index.rebuild();
        assertEquals(id, nearby.nearby(query(500, 1)).get(0).id());
    }

    @Test
    void redisConnectionTimeoutStillReturnsDatabaseResults() {
        Long id = service.create(canteen("Near", "0.001", true));
        index.rebuild();
        redisContainer.getDockerClient().pauseContainerCmd(redisContainer.getContainerId()).exec();
        try {
            assertEquals(id, nearby.nearby(query(500, 1)).get(0).id());
        } finally {
            redisContainer.getDockerClient().unpauseContainerCmd(redisContainer.getContainerId()).exec();
        }
    }

    @Test
    void legacyStallsCanRemainUnassociatedAndSoftDeletionPreservesTheirAssociation() {
        assertNull(locations.get(1L).canteenId());
        Long id = service.create(canteen("Associated", "0.001", true));
        locations.bindCanteen(1L, id);
        RestaurantLocationDTO dto = new RestaurantLocationDTO();
        dto.setFloor("2F");
        dto.setWindowNumber("08");
        dto.setPickupInstructions("Use east stairs");
        locations.update(1L, dto);
        assertEquals("08", locations.get(1L).windowNumber());
        assertNull(locations.get(2L).windowNumber());
        service.delete(id);
        assertEquals(id, locations.get(1L).canteenId());
        assertFalse(locations.get(1L).canteenEnabled());
        assertThrows(BusinessException.class, () -> locations.bindCanteen(2L, id));
        locations.bindCanteen(1L, null);
        assertNull(locations.get(1L).canteenId());
    }

    @Test
    void concurrentRebuildAndUpdateConvergeToLatestDatabaseRevision() throws Exception {
        Long id = service.create(canteen("Original", "0.001", true));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> rebuild = pool.submit(index::rebuild);
            Future<?> update = pool.submit(() -> service.update(id, canteen("Latest", "1", true)));
            rebuild.get(15, TimeUnit.SECONDS);
            update.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        new CanteenGeoConsumer(index).handle(id);
        assertEquals(String.valueOf(mapper.revision()), redis.opsForValue().get(CanteenGeoIndexService.READY_KEY));
        assertTrue(nearby.nearby(query(500, 20)).isEmpty());
        assertEquals("Latest", mapper.selectById(id).getName());
    }

    @Test
    void coordinateLimitsAndDateLineHaveTheSameRadiusSemanticsWithAndWithoutGeo() {
        CanteenDTO edge = canteen("Redis latitude limit", "180", true);
        edge.setLatitude(new BigDecimal("85.05112878"));
        Long edgeId = service.create(edge);
        NearbyCanteenQuery edgeQuery = query(1, 20);
        edgeQuery.setLongitude(new BigDecimal("180"));
        edgeQuery.setLatitude(new BigDecimal("85.05112878"));
        var fallback = nearby.nearby(edgeQuery);
        assertEquals(edgeId, fallback.get(0).id());
        index.rebuild();
        assertEquals(fallback, nearby.nearby(edgeQuery));
        Long acrossDateLine = service.create(canteen("Across date line", "-179.99999999", true));
        NearbyCanteenQuery dateLineQuery = query(1, 20);
        dateLineQuery.setLongitude(new BigDecimal("180"));
        var dateLineFallback = nearby.nearby(dateLineQuery);
        assertEquals(acrossDateLine, dateLineFallback.get(0).id());
        index.rebuild();
        assertEquals(dateLineFallback, nearby.nearby(dateLineQuery));
    }

    @Test
    void redisRejectsAnOlderPublicationEvenIfItsNetworkCommandArrivesLate() {
        Long id = service.create(canteen("Old", "0.001", true));
        String oldRevision = String.valueOf(mapper.revision());
        service.update(id, canteen("New", "1", true));
        index.rebuild();
        // A timed-out publisher can leave a command in flight after its DB lock is released.
        Long applied = redis.execute(CanteenGeoIndexService.REBUILD,
                List.of(CanteenGeoIndexService.GEO_KEY, CanteenGeoIndexService.READY_KEY),
                oldRevision, "0.001", "0", String.valueOf(id));
        assertEquals(0L, applied);
        assertEquals(String.valueOf(mapper.revision()), redis.opsForValue().get(CanteenGeoIndexService.READY_KEY));
        assertTrue(index.search(mapper.revision(), 1, 0, 0, 500).isEmpty());
    }
}
