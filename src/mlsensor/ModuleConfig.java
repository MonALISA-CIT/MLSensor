/*
 * Created on Feb 14, 2011
 */
package mlsensor;

import java.util.concurrent.TimeUnit;


/**
 * Config class for modules.
 * 
 * @author ramiro
 */
public class ModuleConfig {

    /**
     * Module name
     */
    final String moduleName;
    
    /**
     * exec delay in nanoseconds
     */
    final long execDelayNanos;
    
    /**
     * exec delay in nanoseconds
     */
    final long initialDelayNanos;
    
    /**
     * 
     * @param moduleName
     * @param execDelay 
     * @param unit 
     * 
     * @throws NullPointerException if moduleName is null
     */
    public ModuleConfig(final String moduleName, final long execDelay, final TimeUnit unit) {
        this(moduleName, MLSensorConstants.getDefaultInitialDelay(unit), execDelay, unit);
    }
    
    /**
     * 
     * @param moduleName
     * @param initialDelay 
     * @param execDelay 
     * @param unit 
     * 
     * @throws NullPointerException if moduleName is null
     */
    public ModuleConfig(final String moduleName, final long initialDelay, final long execDelay, final TimeUnit unit) {
        if(moduleName == null) {
            throw new NullPointerException("moduleName cannot be null");
        }
        this.moduleName = moduleName;
        final long pExecDelayNanos = TimeUnit.NANOSECONDS.convert(execDelay, unit); 
        if(pExecDelayNanos > TimeUnit.SECONDS.toNanos(1)) {
            this.execDelayNanos = pExecDelayNanos;
        } else {
            this.execDelayNanos = MLSensorConstants.DEFAULT_REPEAT_DELAY;
        }
        final long pInitialDelayNanos = TimeUnit.NANOSECONDS.convert(initialDelay, unit); 
        if(pInitialDelayNanos > 0) {
            this.initialDelayNanos = pInitialDelayNanos;
        } else {
            this.initialDelayNanos = MLSensorConstants.DEFAULT_INITIAL_DELAY;
        }
    }

    /**
     * @return module name
     */
    public final String moduleName() {
        return this.moduleName;
    }
    
    /**
     * @param unit
     * @return how soon to call the module after the application is started
     */
    final long getInitialDelay(final TimeUnit unit) {
        return unit.convert(this.initialDelayNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * @param unit
     * @return module repeat interval
     */
    final long getExecDelay(final TimeUnit unit) {
        return unit.convert(this.execDelayNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("ModuleConfig [moduleName=");
        builder.append(moduleName).append(", execDelayNanos=").append(execDelayNanos).
        	append(", initialDelayNanos=").append(initialDelayNanos).append("]");
        return builder.toString();
    }
    
}
