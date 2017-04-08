/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.example.rules.runtimestats;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.drools.core.io.impl.ReaderInputStream;
import org.kie.api.KieBase;
import org.kie.internal.concurrent.ExecutorProviderFactory;
import org.kie.internal.conf.ConstraintJittingThresholdOption;

import com.redhat.example.rules.runtimestats.RuleRuntimeCompareService.CommandsFactory;
import com.redhat.example.rules.runtimestats.RuleRuntimeStatsService.StatsType;

/**
 * Default Rule Simulator
 * @author okuniyas
 */
public class DefaultRuleSimulator implements RuleSimulator {
	public static final String[]  FILE_NAMES =
		{
			"rule_runtime_stats_base_rules",
			"rule_runtime_stats_working_rules",
			"rule_runtime_stats_comparison",
			"result_facts_base_rules",
			"result_facts_working_rules",
			"result_facts_comparison",
		};
	public static final String EXTENSION = "json";
	
	private KieBase baseRules = null;
	private KieBase workingRules = null;
	private String reportDir = null;
	private int warmupSeconds = 30;
	private int jittingThreads = -1;

	RuleRuntimeCompareService ruleCompare =
			RuleRuntimeCompareService.Factory.get();

	@Override
	public KieBase getBaseRules() {
		return baseRules;
	}

	@Override
	public KieBase getWorkingRules() {
		return workingRules;
	}

	@Override
	public String getReportDir() {
		return reportDir;
	}
	
	@Override
	public void executeAllStats(CommandsFactory commandsFactory) {
		if (!isValidParameters()) {
			return;
		}

		int warmupSeconds = getWarmupSeconds()/StatsType.values().length;
		// Warming up
		for (StatsType statsType : StatsType.values()) {
			warmup(commandsFactory, statsType, warmupSeconds);
		}

		// Skip warming up for each statsType
		warmupSeconds = getWarmupSeconds();
		setWarmupSeconds(-1);
		
		// Do Simulation
		for (StatsType statsType : StatsType.values()) {
			execute(commandsFactory, statsType);
		}
		
		// revert warmupSeconds
		setWarmupSeconds(warmupSeconds);
	}

	@Override
	public String[] execute(CommandsFactory commandsFactory, StatsType statsType) {
		if (!isValidParameters()) {
			return null;
		}

		// Warming up
		warmup(commandsFactory, statsType, getWarmupSeconds());

		// Execute comparison
		String[] stats =
				ruleCompare.compareExecution(
						getBaseRules(),
						getWorkingRules(),
						commandsFactory,
						statsType);
		
		// Write results
		File path = new File(getReportDir());
		for (int i=0; i<stats.length; i++) {
			File file;
			if (i < 3) { // stats
				file = new File(path,
						statsType.toString().toLowerCase() + "_" +
								FILE_NAMES[i] + "." + EXTENSION);
			} else { // facts
				file = new File(path,
						FILE_NAMES[i] + "." + EXTENSION);
				
			}
			FileWriter writer = null;
			try {
				writer = new FileWriter(file);
				writer.write(stats[i]);
			} catch (IOException e) {
				stats = null;
				e.printStackTrace();
			} finally {
				IOUtils.closeQuietly(writer);
			}
		}
		// Copy report resources
		copy_report_resources(path);
		// Exec diff
		execute_diff(path);

		return stats;
	}

	private void copy_report_resources(File path) {
		FileUtils.copyResourcesRecursively(super.getClass().getResource("/report/report.html"), path);
		FileUtils.copyResourcesRecursively(super.getClass().getResource("/report/mkdiff.sh"), path);
		FileUtils.copyResourcesRecursively(super.getClass().getResource("/report/lib"), path);
	}
	
	private void execute_diff(File path) {
		String[] command = {"sh", "-c", "cd " + path.getPath() + " && sh < mkdiff.sh" };
		try {
			Process p = Runtime.getRuntime().exec(command);
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void warmup(CommandsFactory commandsFactory, StatsType statsType, int warmupSeconds) {
		if (warmupSeconds <= 0) {
			return;
		}
		
		System.out.println("START Warming Up. stats: " + statsType.toString() +
				" for " + warmupSeconds + " seconds.");
		
		String jittingThreshold =
				System.getProperty(ConstraintJittingThresholdOption.PROPERTY_NAME);
		System.setProperty(ConstraintJittingThresholdOption.PROPERTY_NAME, "1");
		long endWarmupTime = System.currentTimeMillis() + warmupSeconds * 1000;
		while (System.currentTimeMillis() < endWarmupTime) {
			ruleCompare.compareExecutionForWarmup(
					getBaseRules(),
					getWorkingRules(),
					commandsFactory,
					statsType, endWarmupTime);
		}
		if (jittingThreshold == null) {
			System.clearProperty(ConstraintJittingThresholdOption.PROPERTY_NAME);
		} else {
			System.setProperty(ConstraintJittingThresholdOption.PROPERTY_NAME, jittingThreshold);
		}
		
		System.out.println("END   Warming Up. stats: " + statsType.toString() +
				" for " + warmupSeconds + " seconds.");		
	}

	@Override
	public boolean isValidParameters() {
		if (getBaseRules() == null) {
			System.err.println("ERROR: Base Rules is invalid");
			return false;
		}
		if (getWorkingRules() == null) {
			System.err.println("ERROR: Working Rules is invalid");
			return false;
		}
		// check the report directory
		File path = new File(getReportDir());
		if (! FileUtils.ensureDirectoriesExists(path)) {
			return false;
		}
		return true;
	}

	public void setBaseRules(KieBase baseRules) {
		this.baseRules = baseRules;
	}

	public void setWorkingRules(KieBase workingRules) {
		this.workingRules = workingRules;
	}

	public void setReportDir(String reportDir) {
		this.reportDir = reportDir;
	}

	public int getWarmupSeconds() {
		return warmupSeconds;
	}

	public void setWarmupSeconds(int warmupSeconds) {
		this.warmupSeconds = warmupSeconds;
	}

	public int getJittingThreads() {
		return jittingThreads;
	}

	public void setJittingThreads(int jittingThreads) {
		this.jittingThreads = jittingThreads;
		int n = jittingThreads > 0 ? jittingThreads : Runtime.getRuntime().availableProcessors();
		ThreadPoolExecutor ex =
				(ThreadPoolExecutor)ExecutorProviderFactory.getExecutorProvider().getExecutor();
		if (n != ex.getCorePoolSize()) {
			ex.setCorePoolSize(n);
			ex.setMaximumPoolSize(n);
		}
	}

	public static class FileUtils {
		public static boolean copyFile(final File toCopy, final File destFile) {
			try {
				return FileUtils.copyStream(new FileInputStream(toCopy),
						new FileOutputStream(destFile));
			} catch (final FileNotFoundException e) {
				e.printStackTrace();
			}
			return false;
		}

		private static boolean copyFilesRecusively(final File toCopy,
				final File destDir) {
			assert destDir.isDirectory();

			if (!toCopy.isDirectory()) {
				return FileUtils.copyFile(toCopy, new File(destDir, toCopy.getName()));
			} else {
				final File newDestDir = new File(destDir, toCopy.getName());
				if (!newDestDir.exists() && !newDestDir.mkdir()) {
					return false;
				}
				for (final File child : toCopy.listFiles()) {
					if (!FileUtils.copyFilesRecusively(child, newDestDir)) {
						return false;
					}
				}
			}
			return true;
		}

		public static boolean copyJarResourcesRecursively(final File destDir,
				final JarURLConnection jarConnection) throws IOException {

			final JarFile jarFile = jarConnection.getJarFile();
			boolean srcIsFile = false;

			for (final Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
				final JarEntry entry = e.nextElement();
				if (entry.getName().startsWith(jarConnection.getEntryName())) {
					// check if the source entry is file or not.
					if (entry.getName().equals(jarConnection.getEntryName())) {
						srcIsFile = ! entry.isDirectory();
					}

					final String filename = srcIsFile ?
							getLastOfPath(entry.getName()) :
								getLastOfPath(jarConnection.getEntryName()) + "/" +
								StringUtils.removeStart(entry.getName(), //
										jarConnection.getEntryName());
					
					final File f = new File(destDir, filename);
					if (!entry.isDirectory()) {
						final InputStream entryInputStream = jarFile.getInputStream(entry);
						if(!FileUtils.copyStream(entryInputStream, f)){
							return false;
						}
						entryInputStream.close();
					} else {
						if (!FileUtils.ensureDirectoryExists(f)) {
							throw new IOException("Could not create directory: "
									+ f.getAbsolutePath());
						}
					}
				}
			}
			return true;
		}

		private static String getLastOfPath(String name) {
			if (name.contains("/")) {
				return name.substring(name.lastIndexOf('/') + 1);
			} else {
				return name;
			}
		}

		public static boolean copyResourcesRecursively( //
				final URL originUrl, final File destination) {
			try {
				final URLConnection urlConnection = originUrl.openConnection();
				if (urlConnection instanceof JarURLConnection) {
					return FileUtils.copyJarResourcesRecursively(destination,
							(JarURLConnection) urlConnection);
				} else {
					return FileUtils.copyFilesRecusively(new File(originUrl.getPath()),
							destination);
				}
			} catch (final IOException e) {
				e.printStackTrace();
			}
			return false;
		}

		private static boolean copyStream(final InputStream is, final File f) {
			try {
				return FileUtils.copyStream(is, new FileOutputStream(f));
			} catch (final FileNotFoundException e) {
				e.printStackTrace();
			}
			return false;
		}

		private static boolean copyStream(final InputStream is, final OutputStream os) {
			try {
				final byte[] buf = new byte[1024];

				int len = 0;
				while ((len = is.read(buf)) > 0) {
					os.write(buf, 0, len);
				}
				is.close();
				os.close();
				return true;
			} catch (final IOException e) {
				e.printStackTrace();
			}
			return false;
		}

		private static boolean ensureDirectoryExists(final File f) {
			return f.exists() || f.mkdir();
		}
		
		private static boolean ensureDirectoriesExists(final File f) {
			if (! f.exists()) {
				boolean mkdirs = false;
				try {
					mkdirs = f.mkdirs();
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (!mkdirs) {
					System.err.println("ERROR: Report Directory ("
							+ f.toString()
							+ ") is invalid! Cannot create it");
					return false;
				}
			}
			return true;
		}

		private static String loadFileAsString(File dir, String filename) {
			try {
				StringWriter writer = new StringWriter();
				copyStream(new FileInputStream(new File(dir, filename)),
						new WriterOutputStream(writer));
				return writer.toString();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			return "";
		}
	}

	@Override
	public void aggregate(
			String baseReportPath, boolean useBase,
			String workingReportPath, boolean useWorking,
			String newReportPath) {
		File baseReportDir = new File(baseReportPath);
		if (!baseReportDir.exists()) {
			System.err.println("ERROR: baseReportPath ("
					+ baseReportPath
					+ ") is invalid!");
			return;
		}
		File workingReportDir = new File(workingReportPath);
		if (!workingReportDir.exists()) {
			System.err.println("ERROR: workingReportPath ("
					+ workingReportPath
					+ ") is invalid!");
			return;
		}
		File newReportDir = new File(newReportPath);
		if (! FileUtils.ensureDirectoriesExists(newReportDir)) {
			return;
		}
		// aggregate stats report
		for (StatsType statsType : RuleRuntimeStatsService.StatsType.values()) {
			String statsStr = statsType.name().toLowerCase();
			String stats[] = new String[3];
			if (useBase) {
				File orig = new File(baseReportDir, statsStr + "_" + FILE_NAMES[0] + "." + EXTENSION);
				if (!orig.exists()) {
					// no this statsType result
					continue;
				}
				FileUtils.copyFile(orig,
						new File(newReportDir, statsStr + "_" + FILE_NAMES[0] + "." + EXTENSION));
				stats[0] = FileUtils.loadFileAsString(baseReportDir, statsStr + "_" + FILE_NAMES[0] + "." + EXTENSION);
			} else {
				File orig = new File(baseReportDir, statsStr + "_" + FILE_NAMES[1] + "." + EXTENSION);
				if (!orig.exists()) {
					// no this statsType result
					continue;
				}
				FileUtils.copyFile(orig,
						new File(newReportDir, statsStr + "_" + FILE_NAMES[0] + "." + EXTENSION));
				stats[0] = FileUtils.loadFileAsString(baseReportDir, statsStr + "_" + FILE_NAMES[1] + "." + EXTENSION);
			}
			if (useWorking) {
				File orig = new File(workingReportDir, statsStr + "_" + FILE_NAMES[1] + "." + EXTENSION);
				if (!orig.exists()) {
					// no this statsType result
					continue;
				}
				FileUtils.copyFile(orig,
						new File(newReportDir, statsStr + "_" + FILE_NAMES[1] + "." + EXTENSION));
				stats[1] = FileUtils.loadFileAsString(workingReportDir, statsStr + "_" + FILE_NAMES[1] + "." + EXTENSION);
			} else {
				File orig = new File(workingReportDir, statsStr + "_" + FILE_NAMES[0] + "." + EXTENSION);
				if (!orig.exists()) {
					// no this statsType result
					continue;
				}
				FileUtils.copyFile(orig,
						new File(newReportDir, statsStr + "_" + FILE_NAMES[1] + "." + EXTENSION));
				stats[1] = FileUtils.loadFileAsString(workingReportDir, statsStr + "_" + FILE_NAMES[0] + "." + EXTENSION);
			}

			stats[2] = ruleCompare.compareStats(stats[0], stats[1]);
			FileUtils.copyStream(new ReaderInputStream(new StringReader(stats[2])),
					new File(newReportDir, statsStr + "_" + FILE_NAMES[2] + "." + EXTENSION));
		}

		// aggregate facts report
		String stats[] = new String[3];
		if (useBase) {
			FileUtils.copyFile(new File(baseReportDir, FILE_NAMES[3] + "." + EXTENSION),
					new File(newReportDir, FILE_NAMES[3] + "." + EXTENSION));
			stats[0] = FileUtils.loadFileAsString(baseReportDir, FILE_NAMES[3] + "." + EXTENSION);
		} else {
			FileUtils.copyFile(new File(baseReportDir, FILE_NAMES[4] + "." + EXTENSION),
					new File(newReportDir, FILE_NAMES[3] + "." + EXTENSION));
			stats[0] = FileUtils.loadFileAsString(baseReportDir, FILE_NAMES[4] + "." + EXTENSION);
		}
		if (useWorking) {
			FileUtils.copyFile(new File(workingReportDir, FILE_NAMES[4] + "." + EXTENSION),
					new File(newReportDir, FILE_NAMES[4] + "." + EXTENSION));
			stats[1] = FileUtils.loadFileAsString(workingReportDir, FILE_NAMES[4] + "." + EXTENSION);
		} else {
			FileUtils.copyFile(new File(workingReportDir, FILE_NAMES[3] + "." + EXTENSION),
					new File(newReportDir, FILE_NAMES[4] + "." + EXTENSION));
			stats[1] = FileUtils.loadFileAsString(workingReportDir, FILE_NAMES[3] + "." + EXTENSION);
		}
		stats[2] = ruleCompare.compareFacts(stats[0], stats[1]);
		FileUtils.copyStream(new ReaderInputStream(new StringReader(stats[2])),
				new File(newReportDir, FILE_NAMES[5] + "." + EXTENSION));
		
		// Copy report resources
		copy_report_resources(newReportDir);
		// Exec diff
		execute_diff(newReportDir);
	}
}
