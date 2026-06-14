package com.trainqueue.scheduler.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "LAUNCHER", havingValue = "k8s")
public class KubernetesConfig {

    @Bean(destroyMethod = "close")
    KubernetesClient kubernetesClient() {
        // picks up the ambient kubeconfig (e.g. minikube)
        return new KubernetesClientBuilder().build();
    }
}
