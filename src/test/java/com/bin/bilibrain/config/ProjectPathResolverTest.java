package com.bin.bilibrain.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectPathResolverTest {

    @Test
    void resolvesRelativePathsAgainstMavenProjectRootWhenRunningFromTargetClasses() {
        ProjectPathResolver resolver = new ProjectPathResolver(
            Path.of("D:/learnProjects/bilibrain/target/classes"),
            Path.of("D:/learnProjects")
        );

        assertThat(resolver.getProjectRoot()).isEqualTo(Path.of("D:/learnProjects/bilibrain"));
        assertThat(resolver.resolveFromProjectRoot(Path.of("./skills")))
            .isEqualTo(Path.of("D:/learnProjects/bilibrain/skills"));
    }

    @Test
    void keepsAbsolutePathsUntouched() {
        ProjectPathResolver resolver = new ProjectPathResolver(
            Path.of("D:/learnProjects/bilibrain/target/classes"),
            Path.of("D:/learnProjects")
        );

        assertThat(resolver.resolveFromProjectRoot(Path.of("D:/custom/skills")))
            .isEqualTo(Path.of("D:/custom/skills"));
    }
}
