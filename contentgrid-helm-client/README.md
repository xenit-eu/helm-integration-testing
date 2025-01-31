## Helm Client

Java wrapper around the [helm](https://helm.sh/) CLI tool

## Usage

Prerequisites: `helm` should be available in `$PATH`

```java
var helm = Helm.builder()
        .withKubeConfig(kubeconfig)
        .build();

var result = helm.install()
        .chart("nginx", "oci://registry-1.docker.io/bitnamicharts/nginx",
                InstallOption.namespace("my-nginx"),
                InstallOption.createNamespace(),
                InstallOption.version("16.0.6")
        );
```