/*
 * Created on Feb 14, 2011
 */
package mlsensor;

import java.util.concurrent.TimeUnit;


/**
 *
 * @author ramiro
 */
public final class MLSensorConstants {
    
    /**
     * Default repeat delay in nanoseconds
     */
    public static final long DEFAULT_REPEAT_DELAY = TimeUnit.SECONDS.toNanos(30);

    /**
     * Default intial delay in nanoseconds
     */
    public static final long DEFAULT_INITIAL_DELAY = TimeUnit.SECONDS.toNanos(10);
    
    /**
     * @param unit
     * @return default module repeat interval
     */
    public static final long getDefaultRepeatDelay(final TimeUnit unit) {
        return unit.convert(DEFAULT_REPEAT_DELAY, TimeUnit.NANOSECONDS);
    }

    /**
     * @param unit
     * @return how soon to call the module after the application is started
     */
    public static final long getDefaultInitialDelay(final TimeUnit unit) {
        return unit.convert(DEFAULT_INITIAL_DELAY, TimeUnit.NANOSECONDS);
    }
    
}
