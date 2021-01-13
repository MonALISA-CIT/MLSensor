/*
 * $Id: ApMonSender.java 102 2016-06-29 07:59:40Z costing $
 * Created on Oct 16, 2010
 */
package mlsensor;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import apmon.ApMon;
import apmon.ApMonException;
import lia.Monitor.monitor.AppConfig;
import lia.Monitor.monitor.Result;
import lia.Monitor.monitor.eResult;

/**
 *
 * @author ramiro
 *
 */
public class ApMonSender {
	/**
	 * logging facility
	 */
	static final Logger logger = Logger.getLogger(ApMonSender.class.getName());

	private static final AtomicReference<ApMonSender> apMonRef = new AtomicReference<>();

	private static final long START_TIME_NANOS = System.nanoTime();

	private final ApMon apMon;

	private static ScheduledExecutorService senderSched = Executors.newSingleThreadScheduledExecutor();

	// 20 msgs per second - every 50 ms.
	private static final long MIN_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

	private final AtomicLong nextSendNanos = new AtomicLong(nanoNow());

	private static final class SenderTask implements Runnable {
		final Object o;
		final ApMon apMon;
		final boolean rewriteParameterNames;

		private static String getPerlParameterName(final String name) {
			if (name.startsWith("eth")) {
				if ((name.endsWith("_IN") || name.endsWith("_OUT") || name.endsWith("_ERRS")))
					return name.toLowerCase();

				if (name.endsWith("_IPv4"))
					return name.substring(0, name.length() - 5) + "_ip";

				return null;
			}

			if (name.startsWith("Load") || name.startsWith("CPU_"))
				return name.toLowerCase();

			if (name.equals("TOTAL_ReadMBps"))
				return "blocks_in_R";

			if (name.equals("TOTAL_WriteMBps"))
				return "blocks_out_R";

			return null;
		}

		/**
		 * @param r
		 * @param apMon
		 */
		SenderTask(final Object r, final ApMon apMon) {
			this.o = r;
			this.apMon = apMon;
			this.rewriteParameterNames = AppConfig.getb("rewrite.parameter.names", false);
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.lang.Runnable#run()
		 */

		@Override
		public void run() {
			try {
				// a la Doug Lea
				final Object localo = this.o;
				final ApMon localapMon = this.apMon;

				if (localo == null || localapMon == null)
					return;

				int len = 0;
				String sResultClusterName = null;
				String sModuleName = null;
				String sNodeName = null;

				Result r = null;
				eResult er = null;

				boolean result = true;

				if (localo instanceof Result) {
					r = (Result) localo;

					len = r.param.length;
					sModuleName = r.Module;
					sNodeName = r.NodeName;
					sResultClusterName = r.ClusterName;
				}
				else
					if (localo instanceof eResult) {
						er = (eResult) localo;

						len = er.param.length;
						sModuleName = er.Module;
						sNodeName = er.NodeName;
						sResultClusterName = er.ClusterName;

						result = false;
					}
					else
						return;

				final List<Object> paramsValue = new ArrayList<>(len);
				final List<String> paramsName = new ArrayList<>(len);

				for (int i = 0; i < len; i++) {
					// ignore the null warnings in the following lines

					@SuppressWarnings("null")
					final String paramName = result ? r.param_name[i] : er.param_name[i];

					paramsName.add(paramName);

					@SuppressWarnings("null")
					final Object value = result ? Double.valueOf(r.param[i]) : er.param[i];

					paramsValue.add(value);

					if (rewriteParameterNames) {
						final String perlParamName = getPerlParameterName(paramName);

						if (perlParamName != null) {
							paramsName.add(perlParamName);

							Object perlValue = value;

							if (perlParamName.startsWith("blocks_") && perlParamName.endsWith("_R"))
								perlValue = Double.valueOf(((Double) value).doubleValue() * 1024);
							else
								if (perlParamName.startsWith("eth") && (perlParamName.endsWith("_in") || perlParamName.endsWith("_out")))
									perlValue = Double.valueOf(((Double) value).doubleValue() * 1000 / 8);

							paramsValue.add(perlValue);
						}
					}
				}

				if (logger.isLoggable(Level.FINER))
					logger.log(Level.FINER, "[ MLSensor ] [ ApMonSender ] [ SenderTask ] sending " + r);

				String clusterSuffix = null;

				final boolean dynamicCluster = AppConfig.getb("cluster.name.dynamic", true);

				if (dynamicCluster && sModuleName != null)
					if (sModuleName.equalsIgnoreCase("monProcIO") || sModuleName.equalsIgnoreCase("monIPAddresses") || sModuleName.equals("Netstat") || sModuleName.equals("NetworkConfiguration"))
						clusterSuffix = "_SysNetIO";
					else
						if (sModuleName.equalsIgnoreCase("monProcStat") || sModuleName.equalsIgnoreCase("monProcLoad") || sModuleName.equals("ProcessesStatus") || sModuleName.equals("MemInfo")
								|| sModuleName.equals("SysInfo"))
							clusterSuffix = "_SysStat";
						else
							if (sModuleName.equalsIgnoreCase("monDiskIOStat"))
								clusterSuffix = "_SysDiskIO";
							else
								if (sModuleName.equals("DiskDF"))
									clusterSuffix = "_SysDiskDF";
								else
									if (sModuleName.equals("monLMSensors"))
										clusterSuffix = "_Sensors";
									else
										if (sModuleName.equals("monIPMI"))
											clusterSuffix = "_IPMI";

				String sClusterName = MLSensorConfig.getMLSensorClusterName();

				if (clusterSuffix != null && clusterSuffix.length() > 0)
					sClusterName += clusterSuffix;

				if (dynamicCluster && "monIPMI".equals(sModuleName) && sResultClusterName != null) {
					final String origClusterName = MLSensorConfig.getMLSensorClusterName();
					if (sResultClusterName.startsWith(origClusterName))
						sResultClusterName = sResultClusterName.substring(origClusterName.length());

					sClusterName += sResultClusterName;
				}

				final String sExtraSuffix = MLSensorConfig.getMLSensorClusterSuffix(sModuleName);

				if (sExtraSuffix != null && sExtraSuffix.length() > 0)
					sClusterName += sExtraSuffix;

				if (sModuleName != null && sModuleName.equalsIgnoreCase("monXrdSpace") && sNodeName != null)
					sNodeName = sNodeName.replace("localhost", MLSensorConfig.getMLSensorNodeName());
				else
					sNodeName = MLSensorConfig.getMLSensorNodeName();

				localapMon.sendParameters(sClusterName, sNodeName, paramsName.size(), new Vector<>(paramsName), new Vector<>(paramsValue));
			}
			catch (final Throwable t) {
				logger.log(Level.WARNING, "[ SenderTask ] Unable to publish result: " + o + " with apmon. Cause: ", t);
			}

		}
	}

	private ApMonSender() throws SocketException, ApMonException, IOException {
		this.apMon = new ApMon(MLSensorConfig.getDestinations());
	}

	/**
	 * @return the sender
	 * @throws IOException
	 * @throws ApMonException
	 *             in case ApMon cannot be inited
	 * @throws SocketException
	 */
	public static final ApMonSender getInstance() throws SocketException, ApMonException, IOException {
		final ApMonSender apms = apMonRef.get();
		if (apms != null)
			return apms;
		final ApMonSender newAPMS = new ApMonSender();
		apMonRef.compareAndSet(null, newAPMS);
		return apMonRef.get();
	}

	private final static long nanoNow() {
		return System.nanoTime() - START_TIME_NANOS;
	}

	/**
	 * @param result
	 *            the values to send (Result or eResult)
	 */
	public void sendResult(final Object result) {
		final boolean scheduled = false;
		long delay = 0L;
		while (!scheduled) {
			final long now = nanoNow();
			final long nextSend = nextSendNanos.get();
			if (nextSend < now) {
				if (nextSendNanos.compareAndSet(nextSend, now))
					break;

				// other thread succeeded
				continue;
			}

			final long wSendNanos = nextSend + MIN_DELAY_NANOS;
			if (nextSendNanos.compareAndSet(nextSend, wSendNanos)) {
				delay = wSendNanos - now;
				break;
			}
		}

		if (delay > 0) {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "[ ApMonSender ] Send with delay=" + TimeUnit.NANOSECONDS.toMillis(delay) + " millis for result: " + result);
			senderSched.schedule(new SenderTask(result, this.apMon), delay, TimeUnit.NANOSECONDS);
		}
		else {
			if (logger.isLoggable(Level.FINEST))
				logger.log(Level.FINEST, "[ ApMonSender ] No delay in sending for result: " + result);
			senderSched.submit(new SenderTask(result, this.apMon));
		}
	}
}
