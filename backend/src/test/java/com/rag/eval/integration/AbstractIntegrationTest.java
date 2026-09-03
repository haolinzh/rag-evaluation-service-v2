package com.rag.eval.integration;

import com.rag.eval.service.BochaSearchProvider;
import com.rag.eval.service.DashScopeService;
import com.rag.eval.service.WebFetcher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @MockitoBean
    protected DashScopeService dashScopeService;

    @MockitoBean
    protected BochaSearchProvider bochaSearchProvider;

    @MockitoBean
    protected WebFetcher webFetcher;

    // 单例容器：整个测试套件（JVM）生命周期只启动一次，跨测试类共享，避免
    // per-test-class 容器生命周期与 Spring context 缓存冲突（首类 afterAll 停容器，
    // 后续类复用 context 却连不上）。
    static final PostgreSQLContainer<?> postgres;
    static final ElasticsearchContainer elasticsearch;
    static final GenericContainer<?> redis;

    static {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("rag_eval")
            .withUsername("rag")
            .withPassword("rag123");
        postgres.start();

        elasticsearch = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");
        elasticsearch.start();

        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
        redis.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("elasticsearch.host", elasticsearch::getHost);
        registry.add("elasticsearch.port", () -> elasticsearch.getMappedPort(9200));
        registry.add("redis.host", redis::getHost);
        registry.add("redis.port", () -> redis.getMappedPort(6379));
    }
}
