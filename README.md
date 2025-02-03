# Helm Integration Testing

This module provides a set of utilities for testing Helm charts in a Kubernetes cluster with Junit.
The module is designed to be used in integration tests to verify that a Helm chart can be installed, upgraded, and uninstalled successfully.
Each submodule has it's own readme and can be used independently:
- [contentgrid-helm-client](contentgrid-helm-client/README.md): Java wrapper around the [helm](https://helm.sh/) CLI tool
- [contentgrid-junit-jupiter-k8s](contentgrid-junit-jupiter-k8s/README.md): Junit 5 extensions
- [contentgrid-testcontainers-k3s-cilium](contentgrid-testcontainers-k3s-cilium/README.md): Testcontainers module for running a K3s cluster with Cilium CNI
- [contentgrid-testcontainers-registry](contentgrid-testcontainers-registry/README.md): Testcontainers module for running a container registry