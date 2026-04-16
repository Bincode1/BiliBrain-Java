package com.bin.bilibrain.config;

import com.bin.bilibrain.BilibrainApplication;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ProjectPathResolver {
    private final Path projectRoot;

    public ProjectPathResolver() {
        this(resolveApplicationLocation(), resolveFallbackWorkingDirectory());
    }

    public ProjectPathResolver(Path applicationLocation, Path fallbackWorkingDirectory) {
        this.projectRoot = resolveProjectRoot(applicationLocation, fallbackWorkingDirectory);
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public Path resolveFromProjectRoot(Path configuredPath) {
        if (configuredPath == null) {
            return projectRoot;
        }
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        return projectRoot.resolve(configuredPath).normalize();
    }

    private static Path resolveProjectRoot(Path applicationLocation, Path fallbackWorkingDirectory) {
        if (applicationLocation != null) {
            Path candidate = Files.isRegularFile(applicationLocation)
                ? applicationLocation.getParent()
                : applicationLocation;
            Path matched = findProjectRoot(candidate);
            if (matched != null) {
                return matched;
            }
            if (candidate != null) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        return fallbackWorkingDirectory.toAbsolutePath().normalize();
    }

    private static Path findProjectRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))) {
                return current.toAbsolutePath().normalize();
            }
            current = current.getParent();
        }
        return null;
    }

    private static Path resolveApplicationLocation() {
        try {
            return Paths.get(
                BilibrainApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException exception) {
            return null;
        }
    }

    private static Path resolveFallbackWorkingDirectory() {
        return Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
    }
}
