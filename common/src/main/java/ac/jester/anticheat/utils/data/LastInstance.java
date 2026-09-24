package ac.jester.anticheat.utils.data;

import ac.jester.anticheat.player.GrimPlayer;

public class LastInstance {
    private int lastInstance = 100;

    public LastInstance(GrimPlayer player) {
        player.lastInstanceManager.addInstance(this);
    }

    public boolean hasOccurredSince(int time) {
        return lastInstance <= time;
    }

    public void reset() {
        lastInstance = 0;
    }

    public void tick() {
        if (lastInstance == Integer.MAX_VALUE) lastInstance = 100;
        lastInstance++;
    }
}
