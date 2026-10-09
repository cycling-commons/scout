Rider-facing help is bundled into the app at build time. Forks customise it
without touching Swift.

## Files

| File | In git? | Purpose |
| --- | --- | --- |
| `help.example.json` | yes | Generic operational help |
| `help.json` | **no** | Your instance copy |
| `legal.example.json` | yes | Empty legal overlay (`sections: []`) |
| `legal.json` | **no** | Safety / policy links — **your machine only** |

Copy the JSON you want into `Scout/Resources/help.json` and
`Scout/Resources/legal.json` (the example is already bundled as a fallback).

Schema matches [Android/help/README.md](../../Android/help/README.md).
