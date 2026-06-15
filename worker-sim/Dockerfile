FROM python:3.11-slim
WORKDIR /app
COPY train.py .
# don't write .pyc (keeps a read-only rootfs happy) and run as a non-root user
ENV PYTHONDONTWRITEBYTECODE=1
RUN useradd --uid 10001 --create-home worker && mkdir -p /output && chown worker /output
USER 10001
ENTRYPOINT ["python", "-u", "train.py"]
