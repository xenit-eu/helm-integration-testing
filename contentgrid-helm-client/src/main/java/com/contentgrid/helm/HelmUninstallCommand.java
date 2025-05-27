package com.contentgrid.helm;

public interface HelmUninstallCommand {

    /**
     * Uninstall helm chart
     *
     * @param name of the helm release
     * @param options uninstall flags
     */
    UninstallResult uninstall(String name, UninstallOption... options);


    interface UninstallOption {
        void apply(UninstallOptionsHandler handler);

        /**
         * Untyped arguments appended to the command. Main use case is adding less-common flags.
         */
        static UninstallOption arguments(String... args) {
            return handler -> handler.arguments(args);
        }

        /**
         * Simulate an uninstall, implying '--dry-run=client', no cluster connections will be attempted.
         */
        static UninstallOption dryRun() {
            return handler -> handler.dryRun();
        }

        /**
         * Kubernetes namespace scope for this request
         * @param namespace kubernetes namespace
         */
        static UninstallOption namespace(String namespace) {
            return handler -> handler.namespace(namespace);
        }

        /**
         * Time to wait for any individual Kubernetes operation. (default "5m0s")
         * @param duration timeout expression
         */
        static UninstallOption timeout(String duration) {
            return handler -> handler.timeout(duration);
        }
    }

    interface UninstallOptionsHandler {
        void namespace(String namespace);
        void timeout(String duration);
        void dryRun();
        void arguments(String ... args);
    }

    interface UninstallResult {

    }
}
