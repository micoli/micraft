FROM eclipse-temurin:21-jdk-jammy@sha256:ce5767b7222312d42395f5bab033cd91f09e44032a2f21bdfd7b5b912dbe1e77

ARG TARGETARCH
ARG PITCHFORK_VERSION=2.16.0

RUN apt-get update && \
    apt-get install -y curl gnupg ca-certificates && \
    curl -fsSL https://deb.nodesource.com/setup_26.x | bash - && \
    apt-get install -y nodejs chromium-browser inotify-tools blender \
      python3 python3-venv python3-pip && \
    rm -rf /var/lib/apt/lists/*

# MkDocs toolchain for the documentation site (isolated venv, pinned deps)
COPY docs/requirements.txt /tmp/docs-requirements.txt
RUN python3 -m venv /opt/mkdocs && \
    /opt/mkdocs/bin/pip install --no-cache-dir -r /tmp/docs-requirements.txt && \
    ln -s /opt/mkdocs/bin/mkdocs /usr/local/bin/mkdocs

# pitchfork — pinned version, checksum-verified, arch-aware
RUN set -eux; \
    case "${TARGETARCH:-$(dpkg --print-architecture)}" in \
      arm64|aarch64) PF_ARCH=aarch64; PF_SHA=b1af238e6f590ea9367af97db2b171dc8162aa3d685cef483526b1310c8d5b3a ;; \
      amd64|x86_64)  PF_ARCH=x86_64;  PF_SHA=c4d725410aa554169678a28af30e0b42d71a1078f9f59f196566107b22a878dc ;; \
      *) echo "unsupported arch: ${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    curl -fsSL -o pitchfork.tar.gz \
      "https://github.com/jdx/pitchfork/releases/download/v${PITCHFORK_VERSION}/pitchfork-${PF_ARCH}-unknown-linux-gnu.tar.gz"; \
    echo "${PF_SHA}  pitchfork.tar.gz" | sha256sum -c -; \
    tar xzf pitchfork.tar.gz; \
    mv pitchfork /usr/local/bin/pitchfork; \
    rm pitchfork.tar.gz

RUN npx playwright install chromium --with-deps

WORKDIR /workspace

COPY .docker/dev/start.sh /start.sh

CMD ["/start.sh"]
