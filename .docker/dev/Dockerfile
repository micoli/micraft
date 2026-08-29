FROM eclipse-temurin:25-jdk-jammy@sha256:89565961a318534f01c971c7b1d030e60713c66995b887c94010cef938dbc53e

ARG TARGETARCH
ARG PITCHFORK_VERSION=2.16.0

RUN apt-get update && \
    apt-get install -y curl gnupg ca-certificates && \
    curl -fsSL https://deb.nodesource.com/setup_26.x | bash - && \
    apt-get install -y nodejs chromium-browser inotify-tools blender && \
    rm -rf /var/lib/apt/lists/*

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
