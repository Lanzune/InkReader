# Contributing to InkReader

InkReader is licensed under **AGPL-3.0-only**. By contributing, you agree
that your contribution is licensed under the same terms (inbound =
outbound).

## Developer Certificate of Origin

Every commit must be signed off under the Developer Certificate of Origin
1.1:

```text
Signed-off-by: Your Name <you@example.com>
```

Use:

```powershell
git commit -s -m "your message"
```

Do not contribute code you cannot license under AGPL-3.0-only.

## Before submitting

- Run `.\gradlew.bat test`.
- Keep source files' SPDX headers intact.
- Update the relevant `docs/` page when behavior changes.
- Do not add network permissions or telemetry without explicit discussion.
- Plugin contributions must state their license and remain AGPL-compatible
  when distributed with InkReader.

## Security and privacy

- Never commit signing keys, passwords, user books, backups, or device data.
- Report security issues privately to the project owner before public
  disclosure.

## Trademark

The AGPL grants copyright permissions, not trademark rights. See
`TRADEMARK.md`.
