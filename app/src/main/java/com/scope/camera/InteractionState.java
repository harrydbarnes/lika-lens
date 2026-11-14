package com.scope.camera;

/** Independent camera input, lock and detection lifecycles; each has one owner. */
final class InteractionState {
    static final class Gesture {
        enum Mode { IDLE, PINCH, ZOOM_ROW, ZOOM_SLIDER, EV, SHADOWS }
        private Mode mode=Mode.IDLE;
        boolean is(Mode expected) { return mode==expected; }
        boolean begin(Mode requested) {
            if(mode!=Mode.IDLE && mode!=requested && requested!=Mode.PINCH) return false;
            mode=requested;return true;
        }
        void end(Mode owner) { if(mode==owner) mode=Mode.IDLE; }
        void cancel() { mode=Mode.IDLE; }
    }
    static final class Lock {
        enum Mode { UNLOCKED, LOCKED, HOLDING }
        private Mode mode=Mode.UNLOCKED;
        private long heldAt;
        boolean locked() { return mode!=Mode.UNLOCKED; }
        boolean holding() { return mode==Mode.HOLDING; }
        void set(boolean locked) { mode=locked?Mode.LOCKED:Mode.UNLOCKED; }
        void hold(long now) { if(locked()) { mode=Mode.HOLDING;heldAt=now; } }
        void cancelHold() { if(holding()) mode=Mode.LOCKED; }
        long remaining(long now) { return Math.max(0,3000-(now-heldAt)); }
    }
    static final class Motion {
        enum Mode { MOVING, SETTLING, STILL }
        private Mode mode=Mode.MOVING;
        private long since;
        void sample(float score,long now) {
            if(score>=.14f) mode=Mode.MOVING;
            else if(mode==Mode.MOVING) { mode=Mode.SETTLING;since=now; }
            else if(mode==Mode.SETTLING && now-since>1200) mode=Mode.STILL;
        }
        boolean stationary() { return mode==Mode.STILL; }
        void reset() { mode=Mode.MOVING; }
    }
    static final class Detection {
        enum Mode { IDLE, TRACKING, COOLDOWN }
        private Mode mode=Mode.IDLE;
        private int count;
        private final int threshold;
        private final long cooldown;
        private long until;
        Detection(int threshold,long cooldown) { this.threshold=threshold;this.cooldown=cooldown; }
        boolean cooling(long now) { if(mode==Mode.COOLDOWN && now>=until) mode=Mode.IDLE;return mode==Mode.COOLDOWN; }
        boolean hit(long now) {
            if(cooling(now)) return false;
            mode=Mode.TRACKING;
            if(++count<threshold) return false;
            count=0;mode=Mode.COOLDOWN;until=now+cooldown;return true;
        }
        void miss() { count=Math.max(0,count-1);if(count==0 && mode==Mode.TRACKING) mode=Mode.IDLE; }
        void resetTracking() { count=0;if(mode==Mode.TRACKING) mode=Mode.IDLE; }
    }
}
