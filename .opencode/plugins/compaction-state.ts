import type { Plugin } from "@opencode-ai/plugin"
import { readdir, readFile } from "node:fs/promises"
import { join } from "node:path"

const TASK_DIR = "docs/agents/tasks"
const TEMPLATE = "_template.md"

export const LongHorizonStatePlugin: Plugin = async ({ directory, worktree }) => {
  return {
    "experimental.session.compacting": async (_input, output) => {
      const root = worktree ?? directory
      if (!root) return

      const taskDir = join(root, TASK_DIR)
      let files: string[]
      try {
        files = await readdir(taskDir)
      } catch {
        return
      }

      for (const file of files) {
        if (!file.endsWith(".md") || file === TEMPLATE) continue
        try {
          const body = await readFile(join(taskDir, file), "utf8")
          output.context.push(
            `## Repository task state: ${TASK_DIR}/${file}\n` +
              "The repository owns the current truth. Read the task-state file and Git before you act. Do not trust this summary over the file.\n\n" +
              body,
          )
        } catch {
          // Ignore an unreadable task file.
        }
      }
    },
  }
}
