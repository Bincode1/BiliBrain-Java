---
name: obsidian-cli
description: Interact with Obsidian vaults using the Obsidian CLI to read, create, search, and manage notes, tasks, properties, and more. Also supports plugin and theme development with commands to reload plugins, run JavaScript, capture errors, take screenshots, and inspect the DOM. Use when the user asks to interact with their Obsidian vault, manage notes, search vault content, perform vault operations from the command line, or develop and debug Obsidian plugins and themes.
---

# Obsidian CLI

Use the `obsidian` CLI to interact with a running Obsidian instance. Requires Obsidian to be open.

When calling the generic command tool, prefer `run_process`:

```json
{
  "executable": "obsidian",
  "args": ["create", "name=My Note", "content=# Hello", "silent"]
}
```

Do not place long Markdown, multiline text, or quote-heavy content inside a single shell command string on Windows. Put the full note body into one `args` item such as `content=...`.

Important on Windows when using `run_process`:

- If the `content=` value begins immediately with a `key: value` line such as `title: ...`, `source: ...`, `date: ...`, or other frontmatter-like text, `obsidian` may silently exit with `-1` and no stderr/stdout.
- To avoid that bug, make the content start with either:
  - a leading newline: `content=\n title: ...`
  - a Markdown heading first: `content=# Title\n...`
  - or real frontmatter delimiters: `content=---\ntitle: ...\n---\n...`
- If `run_process` returns `exit_code = -1` with empty stdout/stderr for `obsidian create`, do not speculate that Obsidian is closed. First retry with a safe content prefix as above. If needed, retry with `run_shell_command`.

## Command reference

Run `obsidian help` to see all available commands. This is always up to date. Full docs: https://help.obsidian.md/cli

## Syntax

**Parameters** take a value with `=`. Quote values with spaces:

```bash
obsidian create name="My Note" content="Hello world"
```

**Flags** are boolean switches with no value:

```bash
obsidian create name="My Note" silent overwrite
```

For multiline content use `\n` for newline and `\t` for tab.

Recommended safe pattern for note bodies that start with metadata:

```json
{
  "executable": "obsidian",
  "args": [
    "create",
    "name=My Note",
    "content=---\ntitle: My Note\nsource: demo\ndate: 2026-01-04\n---\n\n## Summary\nBody text here",
    "silent"
  ]
}
```

## File targeting

Many commands accept `file` or `path` to target a file. Without either, the active file is used.

- `file=<name>` - resolves like a wikilink (name only, no path or extension needed)
- `path=<path>` - exact path from vault root, e.g. `folder/note.md`

## Vault targeting

Commands target the most recently focused vault by default. Use `vault=<name>` as the first parameter to target a specific vault:

```bash
obsidian vault="My Vault" search query="test"
```

## Common patterns

```bash
obsidian read file="My Note"
obsidian create name="New Note" content="# Hello" template="Template" silent
obsidian append file="My Note" content="New line"
obsidian search query="search term" limit=10
obsidian daily:read
obsidian daily:append content="- [ ] New task"
obsidian property:set name="status" value="done" file="My Note"
obsidian tasks daily todo
obsidian tags sort=count counts
obsidian backlinks file="My Note"
```

Use `--copy` on any command to copy output to clipboard. Use `silent` to prevent files from opening. Use `total` on list commands to get a count.

## Plugin development

### Develop/test cycle

After making code changes to a plugin or theme, follow this workflow:

1. **Reload** the plugin to pick up changes:
   ```bash
   obsidian plugin:reload id=my-plugin
   ```
2. **Check for errors** - if errors appear, fix and repeat from step 1:
   ```bash
   obsidian dev:errors
   ```
3. **Verify visually** with a screenshot or DOM inspection:
   ```bash
   obsidian dev:screenshot path=screenshot.png
   obsidian dev:dom selector=".workspace-leaf" text
   ```
4. **Check console output** for warnings or unexpected logs:
   ```bash
   obsidian dev:console level=error
   ```

### Additional developer commands

Run JavaScript in the app context:

```bash
obsidian eval code="app.vault.getFiles().length"
```

Inspect CSS values:

```bash
obsidian dev:css selector=".workspace-leaf" prop=background-color
```

Toggle mobile emulation:

```bash
obsidian dev:mobile on
```

Run `obsidian help` to see additional developer commands including CDP and debugger controls.
