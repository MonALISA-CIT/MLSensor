/*
 * $Id: MonitorTask.java 83 2011-12-08 13:03:34Z costing $
 *
 * Created on Oct 16, 2010
 *
 */
package mlsensor.monitor;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;
import lia.util.DynamicThreadPoll.SchJob;
import mlsensor.ApMonSender;



/**
 *
 * @author ramiro
 *
 */
public class MonitorTask implements Runnable {
    private static final Logger logger = Logger.getLogger(MonitorTask.class.getName());

    private final SchJob myJob;
    
    /**
     * @param myJob
     */
    public MonitorTask(final SchJob myJob) {
        super();
        this.myJob = myJob;
    }

    /* (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        
        try {
            ApMonSender sender = null;
            try {
                sender = ApMonSender.getInstance();
            }catch(Throwable t) {
                logger.log(Level.WARNING, "[ MonitorTask ] Unable init ApMonSender. Cause: ", t);
            }
            
            if(sender == null) {
                return;
            }
            
            final Object jobResult = myJob.doProcess();
            if(logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "[ MonitorTask ] internal monitoring module returned: " + jobResult);
            }
            
            if(jobResult == null) {
                return;
            }
            
            if(jobResult instanceof Result) {
                sender.sendResult(jobResult);
                return;
            }
            
            if (jobResult instanceof eResult){
            	sender.sendResult(jobResult);
            }
            
            if(jobResult instanceof Collection<?>) {
                final Collection<?> resultsCollection = (Collection<?>)jobResult;
                for(final Object o: resultsCollection) {
                	sender.sendResult(o);
                }
            }
        } catch(Throwable t) {
            logger.log(Level.WARNING, "[ MonitorTask ] Exception executing/sending result from " + myJob.getClass().getName() + " . Cause: ", t);
        }
    }

}
