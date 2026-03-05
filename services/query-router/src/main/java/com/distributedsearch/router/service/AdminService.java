package com.distributedsearch.router.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;

/**
 * Uses the docker-java library (Apache HttpClient 5 transport) to stop/start
 * search-node containers via the Docker Engine Unix socket.
 *
 * Replaces the hand-rolled Unix socket HTTP approach which was returning 400
 * due to subtle differences in how Docker validates its own wire protocol.
 */
@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
    private static final String DOCKER_HOST = "unix:///var/run/docker.sock";

    private DockerClient dockerClient;

    @PostConstruct
    public void init() {
        try {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(DOCKER_HOST)
                    .build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(URI.create(DOCKER_HOST))
                    .maxConnections(10)
                    .connectionTimeout(Duration.ofSeconds(5))
                    .responseTimeout(Duration.ofSeconds(10))
                    .build();

            dockerClient = DockerClientImpl.getInstance(config, httpClient);
            logger.info("[Docker] AdminService initialized — Docker socket: {}", DOCKER_HOST);
        } catch (Exception e) {
            logger.error("[Docker] Failed to initialize Docker client: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                logger.warn("[Docker] Error closing Docker client: {}", e.getMessage());
            }
        }
    }

    /**
     * Stop a container by name. Equivalent to: docker stop <name>
     */
    public boolean stopNode(String containerName) {
        if (dockerClient == null) {
            logger.error("[Docker] Docker client not initialized");
            return false;
        }
        try {
            logger.info("[Docker] Stopping container: {}", containerName);
            dockerClient.stopContainerCmd(containerName).exec();
            logger.info("[Docker] ✓ Container stopped: {}", containerName);
            return true;
        } catch (com.github.dockerjava.api.exception.NotModifiedException e) {
            logger.info("[Docker] ✓ Container already stopped: {}", containerName);
            return true;
        } catch (Exception e) {
            logger.error("[Docker] ✗ Failed to stop container {}: {}", containerName, e.getMessage());
            return false;
        }
    }

    /**
     * Start a previously stopped container. Equivalent to: docker start <name>
     */
    public boolean startNode(String containerName) {
        if (dockerClient == null) {
            logger.error("[Docker] Docker client not initialized");
            return false;
        }
        try {
            logger.info("[Docker] Starting container: {}", containerName);
            dockerClient.startContainerCmd(containerName).exec();
            logger.info("[Docker] ✓ Container started: {}", containerName);
            return true;
        } catch (com.github.dockerjava.api.exception.NotModifiedException e) {
            logger.info("[Docker] ✓ Container already started: {}", containerName);
            return true;
        } catch (Exception e) {
            logger.error("[Docker] ✗ Failed to start container {}: {}", containerName, e.getMessage());
            return false;
        }
    }
    /**
     * Get container status (e.g. "running", "exited").
     */
    public String getNodeStatus(String containerName) {
        if (dockerClient == null) {
            return "unknown";
        }
        try {
            return dockerClient.inspectContainerCmd(containerName).exec().getState().getStatus();
        } catch (Exception e) {
            logger.warn("[Docker] Failed to get status for {}: {}", containerName, e.getMessage());
            return "not-found";
        }
    }
}
