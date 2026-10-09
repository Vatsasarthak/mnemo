package dev.vatsa.mnemo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts a throwaway Postgres in Docker for tests. {@code @ServiceConnection} tells Spring Boot
 * to point the DataSource at this container, overriding the URL in application.yml.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Same major version as docker-compose.yml, so tests match local dev.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

}
