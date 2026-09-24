package ac.jester.anticheat.utils.team;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams.CollisionRule;
import lombok.experimental.UtilityClass;

@UtilityClass
public final class EntityPredicates {
    public static boolean canBePushedBy(EntityTeam entityTeam, EntityTeam playersTeam) {
        CollisionRule entityCollisionRule = entityTeam == null ? CollisionRule.ALWAYS : entityTeam.getCollisionRule();
        if (entityCollisionRule == CollisionRule.NEVER) return false;

        CollisionRule playerCollisionRule = playersTeam == null ? CollisionRule.ALWAYS : playersTeam.getCollisionRule();
        if (playerCollisionRule == CollisionRule.NEVER) return false;

        final boolean isSameTeam = entityTeam != null && entityTeam.equals(playersTeam);
        return (!isSameTeam || (entityCollisionRule != CollisionRule.PUSH_OWN_TEAM && playerCollisionRule != CollisionRule.PUSH_OWN_TEAM))
                && (entityCollisionRule != CollisionRule.PUSH_OTHER_TEAMS && playerCollisionRule != CollisionRule.PUSH_OTHER_TEAMS || isSameTeam);
    }
}
