package com.contentgrid.helm;

import java.time.ZonedDateTime;
import java.util.List;

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
