# deploy/k8s

Deployment + Service manifests for the four TrainQueue services
(`api`, `scheduler`, `gateway`, `console`). Images are pulled from GHCR (pushed by
CI on `main`).

These assume the infra dependencies are reachable in-cluster by name:
`postgres:5432`, `kafka:9092`, `redis:6379`, `mongodb:27017`,
`elasticsearch:9200`, `localstack:4566`. Deploy infra into the same namespace
first (e.g. with Helm charts or your own manifests).

```bash
kubectl apply -f deploy/k8s/
kubectl get pods
minikube service console --url     # open the console
```

The scheduler runs with `LAUNCHER=k8s` and a ServiceAccount that can manage Jobs
and read Pod logs, so it launches each training job as its own K8s Job.
