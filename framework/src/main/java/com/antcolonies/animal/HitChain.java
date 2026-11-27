package com.antcolonies.animal;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * The hit boxes along an animal's drawn body (2026-11-22; the user: "my hits aren't really that accurate, is there no way to
 * sculpt a hit box cleverly"). Minecraft's boxes are square from above and never turn, so a body 5 or 7.5 blocks long
 * cannot have one box that fits it: the animal keeps its small walking box (routes, collisions, water) and carries a chain
 * of small boxes ({@link BodyPart}) set along its body like beads, each the size of that part (a head, a rump, the tail
 * thinning toward its tip), placed every tick on both sides by the way its body faces. A swing or an arrow that meets a
 * bead hits the animal; the server checks a player's reach against the bead struck (as for the Ender Dragon's parts); F3+B
 * shows them. Plants are pushed by the same beads.
 *
 * <p>The beads lie straight along the body: the server does not run the clips, so a swinging tail is covered where it
 * hangs at rest. Beads keep a small gap between them where the body is thin (a tail), so a hit landing exactly between two
 * may miss; that is the price of boxes that cannot turn.
 *
 * <p>An animal with a chain: makes it in its constructor and then claims its parts' ids
 * ({@code setId(ENTITY_COUNTER.getAndAdd(chain.size() + 1) + 1)}, so they follow its own, as the dragon's do), passes ids on
 * in {@code setId} ({@link #setIds}), answers {@code isMultipartEntity} and {@code getParts}, and calls {@link #place}
 * after its own tick has moved it.
 */
public final class HitChain {
    /** One bead: its middle {@code ahead} of the animal's position along its body (behind when below 0), its bottom {@code up} over the animal's feet, its size. All in blocks at the animal's size 1. */
    public record Link(String name, double ahead, double up, float width, float height) {
    }

    private final Mob owner;
    private final Link[] links;
    private final BodyPart[] parts;
    /** Whether the beads were set on the body yet (not before the animal's first tick). */
    private boolean placed;

    public HitChain(Mob owner, Link... links) {
        this.owner = owner;
        this.links = links;
        this.parts = new BodyPart[links.length];
        for (int i = 0; i < links.length; i++) {
            parts[i] = new BodyPart(owner, links[i].name(), links[i].width(), links[i].height());
        }
    }

    public int size() {
        return parts.length;
    }

    public PartEntity<?>[] parts() {
        return parts;
    }

    /** The beads take the ids right after the animal's own. */
    public void setIds(int ownerId) {
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(ownerId + i + 1);
        }
    }

    /**
     * The box Minecraft asks "is any of this animal in view" ({@code getBoundingBoxForCulling}): the walking box joined
     * with every bead, and {@code past} blocks more to the sides and {@code up} more above and below (at the animal's
     * size 1) for what is drawn past the beads (a snout's tip, a swinging tail, a body tilted on a bank). Without it an
     * animal far longer than its walking box blinks out at the screen's edge with much of it still in view (BUGS 271;
     * review R4-23). Before the beads are first placed: the walking box and the chain's whole reach round it.
     */
    public AABB cullingBox(double past, double up) {
        AABB box = owner.getBoundingBox();
        double scale = owner.getScale();
        if (!placed) {
            double reach = 0;
            for (Link link : links) {
                reach = Math.max(reach, Math.abs(link.ahead()) + link.width() / 2.0);
            }
            return box.inflate((reach + past) * scale, up * scale, (reach + past) * scale);
        }
        for (BodyPart part : parts) {
            box = box.minmax(part.getBoundingBox());
        }
        return box.inflate(past * scale, up * scale, past * scale);
    }

    /** Sets each bead on the body as it faces now (call after the animal moved, on both sides). */
    public void place() {
        placed = true;
        float yaw = owner.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), scale = owner.getScale();
        for (int i = 0; i < parts.length; i++) {
            BodyPart part = parts[i];
            Link link = links[i];
            part.sizeToOwner();
            double x = owner.getX() + fx * link.ahead() * scale, y = owner.getY() + link.up() * scale, z = owner.getZ() + fz * link.ahead() * scale;
            part.xo = part.xOld = part.getX();
            part.yo = part.yOld = part.getY();
            part.zo = part.zOld = part.getZ();
            part.setPos(x, y, z);
        }
    }
}
