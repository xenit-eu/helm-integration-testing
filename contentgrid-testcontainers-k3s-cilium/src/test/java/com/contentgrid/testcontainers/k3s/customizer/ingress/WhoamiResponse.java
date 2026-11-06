package com.contentgrid.testcontainers.k3s.customizer.ingress;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.Data;
import tools.jackson.databind.json.JsonMapper;

@Data
public class WhoamiResponse {
    private String hostname;
    private List<String> ip;
    private Map<String, List<String>> headers;
    private String url;
    private String host;
    private String method;
    private String remoteAddr;

    public static WhoamiResponse read(InputStream inputStream) {
        var mapper = JsonMapper.builder().build();
        return mapper.readValue(inputStream, WhoamiResponse.class);
    }
}
