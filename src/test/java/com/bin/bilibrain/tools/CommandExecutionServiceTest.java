package com.bin.bilibrain.tools;

import com.bin.bilibrain.config.AppProperties;
import com.bin.bilibrain.exception.BusinessException;
import com.bin.bilibrain.service.tools.CommandExecutionService;
import com.bin.bilibrain.service.tools.WorkspacePathResolver;
import com.bin.bilibrain.service.tools.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CommandExecutionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void runShellCommandExecutesInsideToolsWorkspaceRoot() throws Exception {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setToolsWorkspaceRoot(tempDir);
        Files.writeString(tempDir.resolve("hello.txt"), "hello");

        CommandExecutionService service = new CommandExecutionService(
            mock(WorkspaceService.class),
            new WorkspacePathResolver(),
            appProperties
        );

        String command = isWindows()
            ? "Get-ChildItem . | Select-Object -ExpandProperty Name"
            : "ls .";

        Map<String, Object> result = service.runShellCommand(null, command, ".", 10);

        assertThat(result).containsEntry("success", true);
        assertThat(String.valueOf(result.get("stdout"))).contains("hello.txt");
    }

    @Test
    void runShellCommandRejectsDangerousCommand() {
        AppProperties appProperties = new AppProperties();
        appProperties.getStorage().setToolsWorkspaceRoot(tempDir);

        CommandExecutionService service = new CommandExecutionService(
            mock(WorkspaceService.class),
            new WorkspacePathResolver(),
            appProperties
        );

        assertThatThrownBy(() -> service.runShellCommand(null, "rm -rf /", ".", 10))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("危险");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
