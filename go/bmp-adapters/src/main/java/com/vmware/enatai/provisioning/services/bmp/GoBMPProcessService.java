/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */

package com.vmware.photon.provisioning.services.bmp;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.services.common.ProcessFactoryService;
import com.vmware.xenon.services.common.ProcessState;
import com.vmware.xenon.services.common.ServiceHostLogService;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class GoBMPProcessService extends StatefulService {
    public static String SELF_LINK = "/provisioning/go-bmp-process";
    public static final int GO_DCP_HOST_PORT = 8083;
    public static final String GO_DCP_HOST_PROCESS_NAME = "go-dcp-bmp";
    public static final String GO_BMP_PROCESS_LOG = ServiceUriPaths.CORE_MANAGEMENT + "/go-bmp-process-log";

    private static final String[] SEARCH_PATHS = new String[]{
            "./photon-bmp-adapters/src/main/resources", // When PWD equals "./"
            "./src/main/resources" // When PWD equals "./photon-provisioning"
    };

    private static final Path DEFAULT_DIRECTORY = Paths.get("bin");

    public static class GoBMPProcessState extends ServiceDocument {
        /**
         * TCP port the Go process should listen on.
         */
        public int httpPort;

        /**
         * Glog v level
         */
        public int glogVLevel;
    }

    public GoBMPProcessService() {
        super(GoBMPProcessState.class);
    }

    public void handleStart(Operation op) {
        int httpPort = 0;
        int gLogVLevel = 0;

        if (op.hasBody()) {
            GoBMPProcessState state = op.getBody(GoBMPProcessState.class);
            httpPort = state.httpPort;
            gLogVLevel = state.glogVLevel;
        }

        if (httpPort == 0) {
            httpPort = GO_DCP_HOST_PORT;
        }

        this.startProcess(op, httpPort, gLogVLevel);
    }

    public static String getBinaryBasename() {
        String os = System.getProperty("os.name");
        switch (os) {
        case "Mac OS X":
            os = "-darwin";
            break;
        case "Linux":
            os = "-linux";
            break;
        default:
            throw new RuntimeException("Unknown OS: " + os);
        }

        return String.format("%s%s", GO_DCP_HOST_PROCESS_NAME, os);
    }

    private List<Path> getBinarySearchPath() {
        List<Path> searchPath = new LinkedList<>();
        Path pwd = new File(System.getProperty("user.dir")).toPath();
        for (String path : SEARCH_PATHS) {
            searchPath.add(pwd.resolve(path));
        }
        return searchPath;
    }

    private Path getPath() throws Exception {
        URL url;
        Path relPath;
        Path absPath;

        // Find platform-specific binary (either on disk or in a JAR)
        String basename = getBinaryBasename();
        relPath = DEFAULT_DIRECTORY.resolve(basename);
        url = FileUtils.findResource(this.getBinarySearchPath(), relPath);
        if (url == null) {
            throw new FileNotFoundException("Unable to find " + basename);
        }

        // Copy binary to sandbox if it was found in a JAR
        absPath = this.getHost().copyResourceToSandbox(url, relPath);
        if (relPath == null) {
            throw new Exception("Unable to copy " + basename);
        }

        // Set executable bit if it was copied from a JAR
        File absFile = absPath.toFile();
        if (!absFile.canExecute()) {
            if (!absPath.toFile().setExecutable(true, true)) {
                throw new Exception("Unable to set executable bit on " + absPath.toString());
            }
        }

        return absPath;
    }

    private void startProcess(Operation op, int httpPort, int gLogVLevel) {
        String processPath;

        try {
            processPath = getPath().toString();
        } catch (Throwable e) {
            this.getHost().log(Level.WARNING, e.toString());
            op.fail(e);
            return;
        }

        URI hostURI = this.getHost().getUri();

        String[] arguments = new String[]{
                processPath,
                "-logtostderr",
                String.format("--v=%d", gLogVLevel),
                String.format("-dcp=%s:%s", hostURI.getHost(), hostURI.getPort()),
                String.format("-bind=127.0.0.1:%d", httpPort),
                String.format("-auth-token=%s", this.getSystemAuthorizationContext().getToken())
        };

        ProcessState state = new ProcessState();
        state.logFile = ServiceHostLogService.getDefaultGoDcpProcessLogName();
        state.arguments = arguments;
        state.logLink = GO_BMP_PROCESS_LOG;

        sendRequest(Operation
                .createPost(this, ProcessFactoryService.SELF_LINK)
                .setBody(state)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                this.getHost().log(Level.WARNING, "Failed to start go process: %s",
                                        e.toString());
                                op.fail(e);
                                return;
                            }

                            op.complete();
                        }));
    }
}
