package com.contentgrid.helm;

import java.net.URI;
import java.util.List;

public interface HelmRepositoryCommand {

    List<HelmRepository> list();

    void add(String name, URI repository);

    default void add(String name, String repository) {
        this.add(name, URI.create(repository));
    }

    default void add(URI repository) {
        add(repository.getAuthority(), repository);
    }




    interface HelmRepository {

        String name();

        String url();
    }
}

