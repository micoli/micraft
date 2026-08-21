FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update && \
    apt-get install -y curl gnupg && \
    curl -fsSL https://deb.nodesource.com/setup_26.x | bash - && \
    apt-get install -y nodejs chromium-browser inotify-tools blender && \
    rm -rf /var/lib/apt/lists/*

RUN wget https://github.com/jdx/pitchfork/releases/download/v2.16.0/pitchfork-aarch64-unknown-linux-gnu.tar.gz && \
    tar xvfz pitchfork-aarch64-unknown-linux-gnu.tar.gz && \
    mv pitchfork /usr/local/bin/pitchfork

RUN npx playwright install chromium --with-deps

WORKDIR /workspace

COPY .docker/dev/start.sh /start.sh

CMD ["/start.sh"]
