/*
 * $Id: MLSensorConfig.java 102 2016-06-29 07:59:40Z costing $
 * Created on Oct 16, 2010
 */
package mlsensor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.AppConfigChangeListener;

/**
 * @author ramiro
 */
public class MLSensorConfig {

	private static final Logger logger = Logger.getLogger(MLSensor.class.getName());

	private static final Map<String, ModuleConfig> defaultConfig = new HashMap<>();

	static {
		defaultConfig.put("monProcIO", new ModuleConfig("monProcIO", 30, TimeUnit.SECONDS));
		defaultConfig.put("monProcLoad", new ModuleConfig("monProcLoad", 30, TimeUnit.SECONDS));
		defaultConfig.put("monProcStat", new ModuleConfig("monProcStat", 30, TimeUnit.SECONDS));
		defaultConfig.put("monDiskIOStat", new ModuleConfig("monDiskIOStat", 30, TimeUnit.SECONDS));
		defaultConfig.put("ProcessesStatus", new ModuleConfig("ProcessesStatus", 60, TimeUnit.SECONDS));
		defaultConfig.put("Netstat", new ModuleConfig("Netstat", 60, TimeUnit.SECONDS));
		defaultConfig.put("monLMSensors", new ModuleConfig("monLMSensors", 120, TimeUnit.SECONDS));
		defaultConfig.put("monIPMI", new ModuleConfig("monIPMI", 120, TimeUnit.SECONDS));
		defaultConfig.put("DiskDF", new ModuleConfig("DiskDF", 300, TimeUnit.SECONDS));
		defaultConfig.put("monIPAddresses", new ModuleConfig("monIPAddresses", 300, TimeUnit.SECONDS));
		defaultConfig.put("SysInfo", new ModuleConfig("SysInfo", 300, TimeUnit.SECONDS));
		defaultConfig.put("MemInfo", new ModuleConfig("MemInfo", 300, TimeUnit.SECONDS));
		defaultConfig.put("NetworkConfiguration", new ModuleConfig("NetworkConfiguration", 300, TimeUnit.SECONDS));

		AppConfig.addNotifier(new AppConfigChangeListener() {

			@Override
			public void notifyAppConfigChanged() {
				reloadConfig();
			}
		});
	}

	/**
	 * Cache of the node name
	 */
	static AtomicReference<String> nodeNameRef = new AtomicReference<>();

	/**
	 * Reload configuration on change
	 */
	static void reloadConfig() {
		nodeNameRef.set(null);
	}

	/**
	 * @return ApMon destinations
	 */
	static final Vector<String> getDestinations() {
		return new Vector<>(Arrays.asList(AppConfig.getVectorProperty("mlsensor.apmon.destinations")));
	}

	/**
	 * @return the modules that should be instantiated
	 */
	static final List<String> getMonitoringModules() {
		final boolean defaultModules = AppConfig.getb("MLSensor.default_modules", true);

		final List<String> ret = defaultModules ? new LinkedList<>(defaultConfig.keySet()) : new LinkedList<>();

		final String[] extraModules = AppConfig.getVectorProperty("mlsensor.modules");

		if (extraModules == null || extraModules.length == 0)
			return ret;

		for (final String sModule : extraModules) {
			if (!ret.contains(sModule))
				ret.add(sModule);
		}

		return ret;
	}

	/**
	 * @param moduleName
	 * @return running configuration of this module
	 */
	static final ModuleConfig getModuleConfig(final String moduleName) {
		if (moduleName == null) {
			throw new NullPointerException("Null module name");
		}

		final long confInitialDelay = AppConfig.getl(moduleName + ".initialDelay", -1);
		final long confExecDelay = AppConfig.getl(moduleName + ".execDelay", -1);
		if (confInitialDelay > 0 || confExecDelay > 0) {
			final long defInitDelaySeconds = MLSensorConstants.getDefaultInitialDelay(TimeUnit.SECONDS);
			final long defDelaySeconds = MLSensorConstants.getDefaultRepeatDelay(TimeUnit.SECONDS);
			return new ModuleConfig(moduleName, (confInitialDelay < 0) ? defInitDelaySeconds : confInitialDelay, (confExecDelay < 0) ? defDelaySeconds : confExecDelay, TimeUnit.SECONDS);
		}

		final ModuleConfig mConfig = defaultConfig.get(moduleName);
		if (mConfig != null) {
			return mConfig;
		}

		return defaultModuleConfig(moduleName);
	}

	/**
	 * @param moduleName
	 * @return default module configuration
	 */
	public static final ModuleConfig defaultModuleConfig(final String moduleName) {
		return new ModuleConfig(moduleName, MLSensorConstants.getDefaultInitialDelay(TimeUnit.NANOSECONDS), MLSensorConstants.getDefaultRepeatDelay(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
	}

	/**
	 * @return Cluster name, default is "MLSensor"
	 */
	static final String getMLSensorClusterName() {
		return AppConfig.getProperty("cluster.name", "MLSensor");
	}

	/**
	 * @param moduleName
	 *            some modules can be mapped to different clusters
	 * @return Cluster suffix, defaults to "_Nodes"
	 */
	static final String getMLSensorClusterSuffix(final String moduleName) {
		// AliEnFilter summarizes automatically such cluster names

		if (AppConfig.getb("cluster.name.suffix.enabled", true)) {
			final String defaultSuffix = AppConfig.getProperty("cluster.name.suffix", "_Nodes");

			if (moduleName != null && moduleName.length() > 0)
				return AppConfig.getProperty("cluster.name.suffix." + moduleName, defaultSuffix);

			return defaultSuffix;
		}

		return null;
	}

	/**
	 * @return Node name, default is FQDN of the machine
	 */
	static final String getMLSensorNodeName() {
		final String cNodeName = nodeNameRef.get();
		if (cNodeName != null) {
			return cNodeName;
		}

		String sName = AppConfig.getProperty("node.name");

		if (sName != null && sName.trim().length() > 0) {
			logger.log(Level.INFO, " Setting node name from configuration : " + sName);
			nodeNameRef.set(sName);
			return sName;
		}

		try {
			final String hName = java.net.InetAddress.getLocalHost().getCanonicalHostName();
			if (hName != null) {
				nodeNameRef.set(hName);
				logger.log(Level.INFO, " Setting node name from : " + hName);
				return hName;
			}

			logger.log(Level.INFO, " Unable to determine local hostname");
		} catch (Throwable t) {
			logger.log(Level.WARNING, " Unable to determine the hostname Cause: ", t);
		}

		logger.log(Level.INFO, " Node name still not determined. returning localhost");

		return "localhost";
	}

}
