/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
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
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.runtime.standalone.server;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.fabric3.api.annotation.monitor.Info;
import org.fabric3.api.annotation.monitor.Severe;
import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.api.host.classloader.MaskingClassLoader;
import org.fabric3.api.host.contribution.ContributionSource;
import org.fabric3.api.host.monitor.DelegatingDestinationRouter;
import org.fabric3.api.host.monitor.MonitorProxyService;
import org.fabric3.api.host.runtime.BootConfiguration;
import org.fabric3.api.host.runtime.BootstrapFactory;
import org.fabric3.api.host.runtime.BootstrapHelper;
import org.fabric3.api.host.runtime.BootstrapService;
import org.fabric3.api.host.runtime.ComponentRegistration;
import org.fabric3.api.host.runtime.Fabric3Runtime;
import org.fabric3.api.host.runtime.HiddenPackages;
import org.fabric3.api.host.runtime.HostInfo;
import org.fabric3.api.host.runtime.RuntimeConfiguration;
import org.fabric3.api.host.runtime.RuntimeCoordinator;
import org.fabric3.api.host.runtime.RuntimeService;
import org.fabric3.api.host.util.FileHelper;
import org.fabric3.api.model.type.RuntimeMode;
import org.w3c.dom.Document;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static org.fabric3.api.host.Names.MONITOR_FACTORY_URI;

/**
 * This class provides the command line interface for starting the Fabric3 standalone server.
 */
public class Fabric3Server implements Fabric3ServerMBean {
    private static final String DOMAIN = "fabric3";
    private static final String RUNTIME_MBEAN = "fabric3:SubDomain=runtime, type=component, name=RuntimeMBean";

    private RuntimeCoordinator coordinator;
    private ServerMonitor monitor;
    private CountDownLatch latch;
    private String productName;

    private volatile boolean shutdown;

    /**
     * Main method.
     *
     * @param args command line arguments.
     * @throws Fabric3Exception if there is a catastrophic problem starting the runtime
     */
    public static void main(String[] args) throws Fabric3Exception {
        Params params = parse(args);
        Fabric3Server server = new Fabric3Server();
        server.start(params);
        System.exit(0);
    }

    /**
     * Starts the runtime in a blocking fashion and only returns after it has been released from another thread.
     *
     * @param params the runtime parameters
     * @throws Fabric3Exception if catastrophic exception was encountered leaving the runtime in an unstable state
     */
    public void start(Params params) throws Fabric3Exception {

        DelegatingDestinationRouter router = new DelegatingDestinationRouter();

        try {
            //  calculate config directories based on the mode the runtime is booted in
            File installDirectory = BootstrapHelper.getInstallDirectory(Fabric3Server.class);
            File extensionsDir = new File(installDirectory, "extensions");
            File runtimeDir = getRuntimeDirectory(params, installDirectory);

            File configDir = BootstrapHelper.getDirectory(runtimeDir, "config");
            File bootDir = BootstrapHelper.getDirectory(installDirectory, "boot");
            File hostDir = BootstrapHelper.getDirectory(installDirectory, "host");

            // create the classloaders for booting the runtime
            ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            ClassLoader maskingClassLoader = new MaskingClassLoader(systemClassLoader, HiddenPackages.getPackages());
            ClassLoader hostLoader = BootstrapHelper.createClassLoader(maskingClassLoader, hostDir);
            ClassLoader bootLoader = BootstrapHelper.createClassLoader(hostLoader, bootDir);

            BootstrapService bootstrapService = BootstrapFactory.getService(bootLoader);

            // load the system configuration
            Document systemConfig = bootstrapService.loadSystemConfig(configDir);

            URI domainName = params.domain != null ? new URI("fabric3://" + params.domain) : bootstrapService.parseDomainName(systemConfig);

            RuntimeMode mode = bootstrapService.parseRuntimeMode(systemConfig);

            String environment = bootstrapService.parseEnvironment(systemConfig);

            String zoneName = bootstrapService.parseZoneName(systemConfig, mode);

            productName = bootstrapService.parseProductName(systemConfig);

            String runtimeName = bootstrapService.getRuntimeName(domainName, zoneName, params.name, mode);

            List<File> deployDirs = bootstrapService.parseDeployDirectories(systemConfig);

            // create the HostInfo and runtime
            HostInfo hostInfo = BootstrapHelper.createHostInfo(runtimeName,
                                                               zoneName,
                                                               mode,
                                                               domainName,
                                                               environment,
                                                               runtimeDir,
                                                               extensionsDir,
                                                               deployDirs,
                                                               false);

            File shutdownFile = new File(hostInfo.getDataDir(), "f3.shutdown");
            if (shutdownFile.exists()) {
                shutdownFile.delete();
            }

            // clear out the tmp directory
            FileHelper.cleanDirectory(hostInfo.getTempDir());

            // clean if set
            if (params.clean) {
                File dataDir = BootstrapHelper.getDirectory(runtimeDir, "data");
                FileHelper.cleanDirectory(dataDir);
            }

            MBeanServer mbServer = MBeanServerFactory.createMBeanServer(DOMAIN);

            RuntimeConfiguration runtimeConfig = new RuntimeConfiguration(hostInfo, mbServer, router);

            Fabric3Runtime runtime = bootstrapService.createDefaultRuntime(runtimeConfig);

            List<ContributionSource> extensions = bootstrapService.getExtensions(hostInfo);

            BootConfiguration configuration = new BootConfiguration();
            configuration.setRuntime(runtime);
            configuration.setHostClassLoader(hostLoader);
            configuration.setBootClassLoader(bootLoader);
            configuration.setSystemConfig(systemConfig);
            configuration.setExtensionContributions(extensions);

            List<ComponentRegistration> registrations = bootstrapService.createDefaultRegistrations(runtime);
            ComponentRegistration runtimeService = new ComponentRegistration("RuntimeService", RuntimeService.class, this::shutdownRuntime, false);
            registrations.add(runtimeService);
            configuration.addRegistrations(registrations);

            // start the runtime
            coordinator = bootstrapService.createCoordinator(configuration);
            coordinator.start();

            // register the runtime with the MBean server
            ObjectName objectName = new ObjectName(RUNTIME_MBEAN);
            mbServer.registerMBean(this, objectName);

            // create the shutdown daemon
            latch = new CountDownLatch(1);

            MonitorProxyService monitorService = runtime.getComponent(MonitorProxyService.class, MONITOR_FACTORY_URI);
            monitor = monitorService.createMonitor(ServerMonitor.class);

            if (mode == RuntimeMode.NODE) {
                monitor.started(productName, runtimeName, domainName.getAuthority(), zoneName, mode.toString(), environment);
            } else {
                monitor.started(productName, mode.toString(), environment);
            }

            // register shutdown hook to catch SIGTERM events
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownRuntime));

            // start watcher for the f3.shutdown file
            Thread t = new Thread(() -> watch(hostInfo));
            t.setDaemon(true);
            t.start();

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (Exception ex) {
            router.flush(System.out);
            shutdown();
            handleStartException(ex);
        }
    }

    public void shutdownRuntime() {
        if (shutdown) {
            return;  // may be called multiple times from the watcher and the shutdown hook
        }
        shutdown();
        shutdown = true;
        latch.countDown();
    }

    private void shutdown() {
        try {
            if (coordinator != null) {
                if (monitor != null) {
                    monitor.shutdown(productName);
                }
                coordinator.shutdown();
            }
        } catch (Fabric3Exception ex) {
            if (monitor != null) {
                monitor.shutdownError(ex);
            } else {
                ex.printStackTrace();
            }
        }
    }

    private File getRuntimeDirectory(Params params, File installDirectory) throws Fabric3Exception {
        File rootRuntimeDir;
        if (params.directory != null) {
            rootRuntimeDir = params.directory;
        } else {
            rootRuntimeDir = new File(installDirectory, "runtimes");
        }

        File runtimeDir;
        if (params.runtimeDirName != null) {
            runtimeDir = new File(rootRuntimeDir, params.runtimeDirName);
        } else {
            runtimeDir = new File(rootRuntimeDir, params.name);
        }

        if (!runtimeDir.exists()) {
            if (params.clone != null) {
                File templateDir = BootstrapHelper.getDirectory(rootRuntimeDir, params.clone);
                File configDir = BootstrapHelper.getDirectory(templateDir, "config");
                if (!configDir.exists()) {
                    throw new Fabric3Exception("Unable to create runtime directory: " + runtimeDir);
                }
                BootstrapHelper.cloneRuntimeImage(configDir, runtimeDir);
            } else {
                throw new IllegalArgumentException("Runtime directory does not exist: " + runtimeDir);
            }
        }
        return runtimeDir;
    }

    private void handleStartException(Exception ex) {
        if (monitor != null) {
            // there could have been an error initializing the monitor
            monitor.exited(ex);
        } else {
            ex.printStackTrace();
        }
    }

    /**
     * Watches the runtime/data directory for a {@code f3.shutdown} file.
     *
     * @param hostInfo the host info
     */
    @SuppressWarnings("unchecked")
    private void watch(HostInfo hostInfo) {
        try {
            Path dataDir = hostInfo.getDataDir().toPath();
            FileSystem fileSystem = FileSystems.getDefault();

            WatchService watcher = fileSystem.newWatchService();
            dataDir.register(watcher, ENTRY_CREATE);

            while (true) {
                WatchKey key = watcher.take();
                for (WatchEvent<?> watchEvent : key.pollEvents()) {
                    WatchEvent.Kind kind = watchEvent.kind();
                    if (ENTRY_CREATE == kind) {
                        Path newPath = ((WatchEvent<Path>) watchEvent).context();
                        if (newPath.endsWith("f3.shutdown")) {
                            shutdownRuntime();
                            try {
                                Files.delete(newPath);
                            } catch (NoSuchFileException e) {
                                // ignore
                            }
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            monitor.shutdownError(e);
            shutdown();
            handleStartException(e);
        }

    }

    private static Params parse(String[] args) {
        Params params = new Params();
        for (String arg : args) {
            if (arg.startsWith("domain:")) {
                params.domain = arg.substring(7);
                if (params.name.trim().length() == 0) {
                    throw new IllegalArgumentException("Domain name not specified: " + arg);
                }
            } else if (arg.startsWith("name:")) {
                params.name = arg.substring(5);
                if (params.name.trim().length() == 0) {
                    throw new IllegalArgumentException("Runtime name not specified: " + arg);
                }
            } else if (arg.startsWith("runtime-dir:")) {
                params.runtimeDirName = arg.substring(12);
            } else if (arg.startsWith("dir:")) {
                params.directory = new File(arg.substring(4));
            } else if (arg.startsWith("clone:")) {
                params.clone = arg.substring(6);
            } else if (arg.equals("clean")) {
                params.clean = true;
            } else if (!arg.contains(":")) {
                // assume this is the runtime name
                params.name = arg;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (params.name == null) {
            // default to VM
            params.name = "vm";
        }
        return params;
    }

    private static class Params {
        String domain;
        String name;
        File directory;
        String runtimeDirName;
        String clone;
        public boolean clean;
    }

    public interface ServerMonitor {

        @Severe("Shutdown error")
        void shutdownError(Exception e);

        @Info("{0} ready [Name: {1}, Domain: {2}, Zone: {3}, Mode:{4}, Environment: {5}]")
        void started(String product, String name, String domain, String zone, String mode, String environment);

        @Info("{0} ready [Mode:{1}, Environment: {2}]")
        void started(String product, String mode, String environment);

        @Info("{0} shutting down")
        void shutdown(String productName);

        @Info("Runtime exited abnormally, Caused by")
        void exited(Exception e);

    }

}
