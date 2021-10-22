package org.togetherjava.tjbot;

/**
 * A bootstrap launcher with minimal dependencies that sets up needed parts and workarounds for the
 * main logic to take over.
 */
public enum BootstrapLauncher {
    ;

    /**
     * Starts the main application.
     * 
     * @param args arguments are forwarded, see {@link Application#main(String[])}
     */
    public static void main(String[] args) {
        setSystemProperties();

        Application.main(args);
    }


    /**
     * Sets any system-properties before anything else is touched.
     */
    @SuppressWarnings("squid:S106") // we can not afford any dependencies, even on a logger
    private static void setSystemProperties() {
        final int cores = Runtime.getRuntime().availableProcessors();
        if (cores <= 1) {
            // If we are in a docker container, we officially might just have 1 core
            // and Java would then set the parallelism of the common ForkJoinPool to 0.
            // And 0 means no workers, so JDA cannot function, no Callback's on REST-Requests
            // are executed
            // NOTE This will likely be fixed with Java 18 or newer, remove afterwards (see
            // https://bugs.openjdk.java.net/browse/JDK-8274349 and
            // https://github.com/openjdk/jdk/pull/5784)
            // noinspection UseOfSystemOutOrSystemErr
            System.out.println("Available Cores \"" + cores + "\", setting Parallelism Flag");
            // noinspection AccessOfSystemProperties
            System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1");
        }
    }
}
