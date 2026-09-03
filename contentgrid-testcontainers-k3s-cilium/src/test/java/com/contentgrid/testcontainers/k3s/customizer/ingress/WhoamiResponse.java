package com.contentgrid.testcontainers.k3s.customizer.ingress;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.SneakyThrows;

@Data
public class WhoamiResponse {
    private String hostname;
    private List<String> ip;
    private Map<String, List<String>> headers;
    private String url;
    private String host;
    private String method;
    private String remoteAddr;

    @SneakyThrows
    public static WhoamiResponse read(InputStream inputStream) {
        var om = new ObjectMapper();
        return om.readValue(inputStream, WhoamiResponse.class);
    }
}
