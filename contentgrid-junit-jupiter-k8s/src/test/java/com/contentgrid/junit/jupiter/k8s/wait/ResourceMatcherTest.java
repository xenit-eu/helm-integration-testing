package com.contentgrid.junit.jupiter.k8s.wait;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResourceMatcherTest {

    private HasMetadata createResource(String name, Map<String, String> labels, Map<String, String> annotations) {
        return new HasMetadata() {
            private final ObjectMeta metadataInstance = new ObjectMeta();

            {
                metadataInstance.setName(name);
                metadataInstance.setLabels(labels == null ? Collections.emptyMap() : labels);
                metadataInstance.setAnnotations(annotations == null ? Collections.emptyMap() : annotations);
            }

            @Override
            public ObjectMeta getMetadata() {
                return metadataInstance;
            }

            @Override
            public void setMetadata(ObjectMeta metadata) {
            }

            @Override
            public String getKind() {
                return "TestResource";
            }

            @Override
            public String getApiVersion() {
                return "v1test";
            }

            @Override
            public void setApiVersion(String s) {

            }
        };
    }

    @Nested
    class LabelledMatcherTests {

        @Test
        void matchWhenAllRequiredLabelsPresentAndCorrect() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.labelled(Map.of("app", "nginx", "env", "prod"));
            HasMetadata resource = createResource("my-pod", Map.of("app", "nginx", "env", "prod"), null);
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void matchWhenAllRequiredAndExtraLabelsPresent() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.labelled(Map.of("app", "nginx"));
            HasMetadata resource = createResource("my-pod", Map.of("app", "nginx", "env", "prod", "tier", "frontend"), null);
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void noMatchWhenRequiredLabelMissing() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.labelled(Map.of("app", "nginx", "env", "prod"));
            HasMetadata resource = createResource("my-pod", Map.of("app", "nginx"), null); // "env" is missing
            assertThat(matcher.test(resource)).isFalse();
        }

        @Test
        void noMatchWhenRequiredLabelHasDifferentValue() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.labelled(Map.of("app", "nginx", "env", "prod"));
            HasMetadata resource = createResource("my-pod", Map.of("app", "nginx", "env", "dev"), null);
            assertThat(matcher.test(resource)).isFalse();
        }

        @Test
        void matchWhenRequiredLabelsEmptyAndResourceHasLabels() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.labelled(Collections.emptyMap());
            HasMetadata resource = createResource("my-pod", Map.of("app", "nginx"), null);
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void matchWhenRequiredLabelsEmptyAndResourceHasNoLabels() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.labelled(Collections.emptyMap());
            HasMetadata resource = createResource("my-pod", Collections.emptyMap(), null);
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void noMatchWhenResourceHasNoLabelsButLabelsRequired() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.labelled(Map.of("app", "nginx"));
            HasMetadata resource = createResource("my-pod", Collections.emptyMap(), null);
            assertThat(matcher.test(resource)).isFalse();
        }
    }

    @Nested
    class AnnotatedMatcherTests {

        @Test
        void matchWhenAllRequiredAnnotationsPresentAndCorrect() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.annotated(Map.of("owner", "team-a", "contact", "admin@example.com"));
            HasMetadata resource = createResource("my-config", null, Map.of("owner", "team-a", "contact", "admin@example.com"));
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void matchWhenAllRequiredAndExtraAnnotationsPresent() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.annotated(Map.of("owner", "team-a"));
            HasMetadata resource = createResource("my-config", null, Map.of("owner", "team-a", "version", "1.0"));
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void noMatchWhenRequiredAnnotationMissing() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.annotated(Map.of("owner", "team-a", "contact", "admin@example.com"));
            HasMetadata resource = createResource("my-config", null, Map.of("owner", "team-a")); // "contact" is missing
            assertThat(matcher.test(resource)).isFalse();
        }

        @Test
        void noMatchWhenRequiredAnnotationHasDifferentValue() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.annotated(Map.of("owner", "team-a"));
            HasMetadata resource = createResource("my-config", null, Map.of("owner", "team-b"));
            assertThat(matcher.test(resource)).isFalse();
        }

        @Test
        void matchWhenRequiredAnnotationsEmptyAndResourceHasAnnotations() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.annotated(Collections.emptyMap());
            HasMetadata resource = createResource("my-config", null, Map.of("owner", "team-a"));
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void matchWhenRequiredAnnotationsEmptyAndResourceHasNoAnnotations() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.annotated(Collections.emptyMap());
            HasMetadata resource = createResource("my-config", null, Collections.emptyMap());
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void noMatchWhenResourceHasNoAnnotationsButAnnotationsRequired() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.annotated(Map.of("owner", "team-a"));
            HasMetadata resource = createResource("my-config", null, Collections.emptyMap());
            assertThat(matcher.test(resource)).isFalse();
        }
    }

    @Nested
    class NamedMatcherTests {

        @Test
        void matchWhenNameInSet() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.named("pod-a", "pod-b", "pod-c");
            HasMetadata resource = createResource("pod-b", null, null);
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void noMatchWhenNameNotInSet() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.named("pod-a", "pod-c");
            HasMetadata resource = createResource("pod-b", null, null);
            assertThat(matcher.test(resource)).isFalse();
        }

        @Test
        void matchWithSingleName() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.named("pod-a");
            HasMetadata resource = createResource("pod-a", null, null);
            assertThat(matcher.test(resource)).isTrue();
        }

        @Test
        void noMatchWithSingleNameDifferent() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.named("pod-a");
            HasMetadata resource = createResource("pod-b", null, null);
            assertThat(matcher.test(resource)).isFalse();
        }

        @Test
        void noMatchWithEmptyNameSet() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.named(); // Empty varargs
            HasMetadata resource = createResource("pod-a", null, null);
            assertThat(matcher.test(resource)).isFalse();
        }

        @Test
        void caseSensitiveNameMatching() {
            ResourceMatcher<HasMetadata> matcher = ResourceMatcher.named("Pod-A");
            HasMetadata resourceLowerCase = createResource("pod-a", null, null);
            assertThat(matcher.test(resourceLowerCase)).isFalse();

            HasMetadata resourceCorrectCase = createResource("Pod-A", null, null);
            assertThat(matcher.test(resourceCorrectCase)).isTrue();
        }
    }
}