# The lion pride (planned 2026-11-25; the design itself is written in step 0)

The user, 2026-11-25: "the lion is a very complex animal ... I definitely don't want 60 subagents, my quota is crying".
Animals are fun first, inspired by real life (CLAUDE.md, 2026-11-14). Neon's models and 47/47/41 clips are ready:
`docs/handoff/lion.md` (male in three looks, lioness in three, cub young and juvenile; face and claws overlays).

## Steps (my pick; each is one session-sized piece, built and tested before the next)
0. **Plan:** write the design here (pride make-up, day, temper, numbers), then the critics' workflow below.
1. **The pride in the game:** male, lioness and cub bodies; prides laid at worldgen near water (as the buffalo herds
   with pans); the lazy day (real lions rest most of the day), drinking, sitting, grooming, roaring at dusk and night,
   the face and claws overlays, the temper toward the player.
2. **The hunt:** lionesses stalk, freeze and leap on buffalo; the herd bunches and faces them (already in the buffalo);
   the kill, the pride eating, the remains of big land animals. **Waits for buffalo calves (buffalo step 2):** buffalo
   are only laid with new land and never bred, so a hunting pride would empty the savanna for good.
3. **Pride life:** cubs play, nuzzle and grow (the juvenile look, the size from 0.78 to 1.15), the lioness grooms them,
   pride greetings, male bluff charges.

## Agent budget (the user's quota; read before every workflow)
Agents are used only where an independent read is worth it; code is written by one mind (parallel writers clash and
lose the thread). Ultracode, when on, tells Claude cost is no limit, which is how a session reached 60 agents.
- **Step 0, design critics: 4 agents, once.** Minecraft coder, game balancer, looks and fun, fewer rules (the four
  that checked the buffalo plan). Each reads the plan and the code it names, returns changes; Claude merges them.
- **Building: 0 agents.** Claude writes the code; the game tests, `clip_check.py` and films are scripts, not agents.
- **Review after each step: at most 6 agents.** Three readers (bugs, Minecraft coder and lag, the house rules), then
  one skeptic each for the grade A findings only, at most 3. Small findings are judged by Claude, not checked again.
- **Never:** loop-until-dry, 3-vote juries, a review of a review, agents for searches Claude can do with a few reads.
- **Per step about 10 agents, the whole lion about 25 to 30** (against 60 for one session before). A turn that runs a
  workflow can carry a hard token cap (for example `+400k` in the message).
