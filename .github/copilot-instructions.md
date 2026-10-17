# Project Guidelines

## Terminal usage

- Reuse an existing terminal for sequential commands.
- Check existing terminals before creating another.
- Wait for a command to finish before issuing another command in that terminal.
- Create a separate terminal only for a long-running process or necessary parallel work.
- Do not interrupt or close terminals running servers or other active processes.
- Avoid creating a new terminal just because a command failed; diagnose and retry in the existing terminal.
- Use synchronous terminal execution for short commands when supported.
- Do not use async/background mode for file inspection, Git commands, or ordinary builds/tests.
- Reserve async/background execution for servers, watchers, or necessary parallel tasks.