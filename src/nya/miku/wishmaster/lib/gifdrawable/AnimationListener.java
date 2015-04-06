package nya.miku.wishmaster.lib.gifdrawable;

/**
 * Interface which can be used to run some code when particular animation event occurs.
 */
public interface AnimationListener {
    /**
     * Called when a single loop of the animation is completed.
     */
    public void onAnimationCompleted();
}
