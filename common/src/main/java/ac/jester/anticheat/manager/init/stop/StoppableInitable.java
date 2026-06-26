package ac.jester.anticheat.manager.init.stop;

import ac.jester.anticheat.manager.init.Initable;

public interface StoppableInitable extends Initable {
    void stop();
}
