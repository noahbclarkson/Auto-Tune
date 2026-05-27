# Design

## System Intent

Auto-Tune should look like a compact technical product site for server operators. It can be dark by default because admins often inspect dashboards, consoles, and docs while running servers, but the interface should stay restrained and readable rather than cinematic.

## Color

Use tinted dark neutrals with emerald as the primary action and status color. Use amber for caution, rose for failure or risk, sky for informational network state, and violet only for developer references. Avoid one-note emerald surfaces, decorative gradient washes, and color used only as ornament.

## Typography

Use the existing system sans stack. Keep headings direct and modest on tool pages. Use tabular or monospace text only for values, commands, code, and IDs. Body copy should stay below 75 characters where possible.

## Layout

The public site uses one global header and footer. Navigation is grouped by user intent: Get Started, Learn, Tools, Network, and Project. Content pages should have a clear page header, short lead copy, and task-oriented sections. Tools can be denser than marketing pages, but controls should remain predictable.

## Components

Use a consistent vocabulary:

- Primary action: emerald filled button.
- Secondary action: bordered neutral button.
- Inputs: dark neutral field, visible focus ring, stable height.
- Cards: only for grouped content, repeated items, or tool panels.
- Alerts: full bordered panels with semantic color, concise copy.
- Tables: horizontal overflow on small screens, clear empty states.

## Data Honesty

Live API data, simulated output, static examples, and future roadmap items need separate labels. Empty states should explain what is missing and what an admin can do next.
