# cg-patches

This fork of [CodinGame/codingame-game-engine](https://github.com/CodinGame/codingame-game-engine)
adds environment-variable overrides for SDK-internal time / quota caps that are
hard-coded in upstream. Used by
[CodingameTemplate](https://github.com/hashiryo/CodingameTemplate) for local
referee builds where you want to run the bot at higher per-turn budgets than
the public judge (e.g. for speed-strength experiments or to absorb local
worker noise on `psyleague`).

## Branch / version policy

- `cg-patched-v4.7.7` — based on upstream `v4.7.7`, version pinned to `4.7.7-cg-patched.1`
  (or `.2`, `.3` ... if patches are added)
- New SDK release: branch off the new upstream tag, rebase patches, bump tag

Patches live as commits on each branch (no patch files), so the diff against
upstream is `git log v4.7.7..cg-patched-v4.7.7`.

## Coordinates after `mvn install`

- `com.codingame.gameengine:core:4.7.7-cg-patched.1`
- `com.codingame.gameengine:runner:4.7.7-cg-patched.1`
- `com.codingame.gameengine:module-endscreen:4.7.7-cg-patched.1`

The groupId stays `com.codingame.gameengine` (so upstream referee poms only need
to bump `<gamengine.version>`); the version suffix `-cg-patched.N` ensures we
never collide with the official artifact in `~/.m2/`.

## Environment variables

All read at JVM startup (`System.getenv`) by `GameManager`. Empty / unset →
upstream default.

### `GameManager` cap constants ([GameManager.java](engine/core/src/main/java/com/codingame/gameengine/core/GameManager.java))

| Env var | Default | Effect |
|---|---|---|
| `CG_GAME_DURATION_HARD_QUOTA` | `30000` | Per-game total compute hard cap (ms). Game throws `RuntimeException` past this. |
| `CG_GAME_DURATION_SOFT_QUOTA` | `25000` | Per-game total compute soft cap (ms). Triggers warning + ignored move past this. |
| `CG_MAX_TURN_TIME` | `25000` | Upper bound for `setTurnMaxTime` / `setFirstTurnMaxTime` arguments validated by SDK. |

Typical use:

```bash
# Allow a 10-minute total budget for local 200ms-per-turn experiments
CG_GAME_DURATION_HARD_QUOTA=600000 \
CG_GAME_DURATION_SOFT_QUOTA=500000 \
CG_MAX_TURN_TIME=200000 \
./play_game.py bot1 bot2
```

`MIN_TURN_TIME` is intentionally NOT env-overridable (changes here are
interpreted as bug fixes upstream, not local experiments).

### `Referee` per-game settings (set on each contest's referee, not in this repo)

These three are env-ized by [`tools/referee_patch/patch_referee_envint.py`](https://github.com/hashiryo/CodingameTemplate/blob/main/tools/referee_patch/patch_referee_envint.py)
on the per-contest referee fork (not here):

- `CG_MAX_TURNS` — overrides `gameManager.setMaxTurns(N)`
- `CG_TURN_MAX_TIME` — overrides `gameManager.setTurnMaxTime(N)`
- `CG_FIRST_TURN_MAX_TIME` — overrides `gameManager.setFirstTurnMaxTime(N)`

The SDK-side caps in this repo are the **upper bound** that those validate
against. Raise both layers in tandem.

## Local install (used by CodingameTemplate)

```bash
git clone https://github.com/hashiryo/codingame-game-engine
cd codingame-game-engine
git checkout cg-patched-v4.7.7
mvn install -DskipTests -Dgpg.skip=true \
    -pl :core,:runner,:module-endscreen -am
```

After install, any contest referee whose pom asks for
`<gamengine.version>4.7.7-cg-patched.1</gamengine.version>` will pick up the
patched jars from `~/.m2/`.
