# Testcontainers K3s Cilium
This module provides a [Testcontainers](https://www.testcontainers.org/) module for running a [K3s](https://k3s.io/) cluster with [Cilium](https://cilium.io/) CNI.

## Troubleshooting

### Common Issues

1. **Error:**
   ```
   path /sys/fs/bpf is mounted on /sys/fs/bpf but it is not a shared or slave mount
   ```
   **Solution**: Ensure Docker is not installed via Snap. Reinstall Docker using a supported package manager or
   distribution method.