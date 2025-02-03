package com.contentgrid.helm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.Value;

public interface HelmListCommand {

    List<HelmRelease> releases(ListOption... options);

    interface ListOption {

    }

    interface HelmRelease {

        String appVersion();

        String chart();

        String name();

        String namespace();

        String revision();

        String status();

        ZonedDateTime updated();
    }
}
