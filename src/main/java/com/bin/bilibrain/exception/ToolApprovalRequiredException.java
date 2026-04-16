package com.bin.bilibrain.exception;

import java.util.Map;

public class ToolApprovalRequiredException extends RuntimeException {
    private final String toolName;
    private final Map<String, Object> arguments;

    public ToolApprovalRequiredException(String toolName, Map<String, Object> arguments) {
        super("Tool [" + toolName + "] requires approval before execution");
        this.toolName = toolName;
        this.arguments = arguments;
    }

    public String getToolName() {
        return toolName;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }
}
