/*
 * $Id: MLSensor.java 102 2016-06-29 07:59:40Z costing $
 * Created on Oct 16, 2010
 */
package mlsensor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.MCluster;
import lia.Monitor.monitor.MFarm;
import lia.Monitor.monitor.MNode;
import lia.Monitor.monitor.MonitoringModule;
import lia.util.DynamicThreadPoll.SchJob;
import lia.util.threads.MLScheduledThreadPoolExecutor;
import mlsensor.monitor.MonitorTask;

/**
 * @author ramiro
 */
public class MLSensor {

    private static final Logger logger = Logger.getLogger(MLSensor.class.getName());

    private static final Set<String> singleArgsSet = new TreeSet<>(Arrays.asList(new String[] {
            "-help", "--help", "-h", "--version", "-version", "-V"
    }));

    /**
     * Version string, filled by build.xml
     */
    public static final String VERSION = "@program_full_version@";

    /**
     * @return the executor that has this module scheduled for running
     */
    static final MLScheduledThreadPoolExecutor initAndStartMonitoring() {
        if (AppConfig.setPropertyIfAbsent("lia.util.process.MAX_POOL_THREADS_COUNT", "10") == null) {
            logger.log(Level.INFO, "Setting default value for lia.util.process.MAX_POOL_THREADS_COUNT to " + AppConfig.getProperty("lia.util.process.MAX_POOL_THREADS_COUNT"));
        } else {
            logger.log(Level.INFO, "Using predefined value for lia.util.process.MAX_POOL_THREADS_COUNT to " + AppConfig.getProperty("lia.util.process.MAX_POOL_THREADS_COUNT"));
        }

        final MLScheduledThreadPoolExecutor monitoringService = new MLScheduledThreadPoolExecutor("lia.Monitor.modules", 5, 5, TimeUnit.MINUTES);

        final List<String> enabledModulesName = MLSensorConfig.getMonitoringModules();
        MFarm mfarm = new MFarm("MLSensor");
        MCluster mcluster = new MCluster(MLSensorConfig.getMLSensorClusterName(), mfarm);
        MNode mnode = new MNode(MLSensorConfig.getMLSensorNodeName(), mcluster, mfarm);
        final Random randomInitialDelay = new Random();

        StringBuilder sb = new StringBuilder(8192);
        for (final String propName : enabledModulesName) {
            SchJob job = null;
            try {
                final String className = propName.indexOf('.') < 0 ? "lia.Monitor.modules." + propName : propName;

                @SuppressWarnings("unchecked")
                Class<SchJob> cjob = (Class<SchJob>) Class.forName(className);
                job = cjob.newInstance();

                final String moduleArgs = AppConfig.getProperty(className + ".args");

                ((MonitoringModule) job).init(mnode, moduleArgs);
                sb.append("\nLoaded monitoring module: ").append(job.getClass().getName()).append(" with ").append(((moduleArgs == null) ? "no" : "'" + moduleArgs + "'")).append(" arguments");
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[ MLSensor ] The module " + propName + " failed to load. Cause: ", t);
                continue;
            }

            final ModuleConfig tConfig = MLSensorConfig.getModuleConfig(propName);
            final ModuleConfig config = (tConfig == null) ? MLSensorConfig.defaultModuleConfig(propName) : tConfig;

            long initialDelayNanos = 0L;
            do {
                initialDelayNanos = randomInitialDelay.nextLong() % config.getInitialDelay(TimeUnit.NANOSECONDS);
            } while (initialDelayNanos < 0L || initialDelayNanos > MLSensorConstants.DEFAULT_REPEAT_DELAY);

            final long initDelayMillis = TimeUnit.NANOSECONDS.toMillis(initialDelayNanos);
            final long repeatDelaySeconds = config.getExecDelay(TimeUnit.SECONDS);
            sb.append("\nScheduled monitoring module: ").append(job.getClass().getName()).append(" initDelay=").append(initDelayMillis).append(" millis and subsequent repeatDelay=").append(repeatDelaySeconds).append(" seconds");
            monitoringService.scheduleWithFixedDelay(new MonitorTask(job), initialDelayNanos, config.getExecDelay(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
        }
        logger.log(Level.INFO, "Finished loading and scheduling modules. Status:\n" + sb.toString() + "\n");
        return monitoringService;

    }

    /**
     * @param args
     *            command line arguments
     * @return the arguments map
     * @throws IllegalArgumentException
     *             if a parameter expects
     */
    static final Map<String, String> parseArgs(final String args[]) {
        final Map<String, String> retMap = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            if (singleArgsSet.contains(arg)) {
                retMap.put(arg, "");
            } else {
                if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
                    throw new IllegalArgumentException("The arg: " + arg + " expects a value");
                }
                retMap.put(arg, args[++i]);
            }
        }

        if (retMap.size() == 0) {
            return Collections.emptyMap();
        }
        return retMap;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        final Map<String, String> argsMap = parseArgs(args);
        if (argsMap.containsKey("-version") || argsMap.containsKey("-V") || argsMap.containsKey("--version")) {
            System.out.println(MLSensor.VERSION);
            System.exit(0);
        }

        if (argsMap.containsKey("-help") || argsMap.containsKey("--help") || argsMap.containsKey("-h")) {
            System.out.println("No help available yet.");
            System.exit(0);
        }

        final String apMonDestinations = argsMap.get("-apmonDest");
        if (apMonDestinations != null && !apMonDestinations.isEmpty()) {
            AppConfig.setProperty("mlsensors.apmon.destinations", apMonDestinations);
        }

        final MLScheduledThreadPoolExecutor execService = initAndStartMonitoring();
        logger.log(Level.INFO, "Monitoring service intited and started. Core pool size = " + execService.getCorePoolSize());
        for (;;) {
            try {
                final boolean finished = execService.awaitTermination(10, TimeUnit.MINUTES);
                if (finished) {
                    System.out.println("Monitoring service finished all the jobs. Core pool size = " + execService.getCorePoolSize() + " ... will exit now!");
                    logger.log(Level.WARNING, "Monitoring service finished all the jobs. Core pool size = " + execService.getCorePoolSize() + " ... will exit now!");
                    break;
                }
                if (logger.isLoggable(Level.FINER)) {
                    logger.log(Level.FINER, "[MLSensor] Monitoring service still running. Core pool size = " + execService.getCorePoolSize());
                }
            } catch (@SuppressWarnings("unused") InterruptedException ie) {
                logger.log(Level.WARNING, "[MLSensor] interrupted exception");
                Thread.interrupted();
                try {
                    Thread.sleep(1000);
                } catch (@SuppressWarnings("unused") Throwable ignore) {
                    // ignore
                }
            } catch (Throwable t) {
                logger.log(Level.WARNING, "[MLSensor] got exception. Cause: ", t);
                try {
                    Thread.sleep(1000);
                } catch (@SuppressWarnings("unused") Throwable ignore) {
                    // ignore
                }
            }
        }
    }

}
