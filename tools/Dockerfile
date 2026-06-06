# ============================================================================
# Dockerfile para builds reprodutíveis do AulaLogger.
#
# Mesmo SDK, NDK, Node, JDK pinados garantem que `gradlew assembleRelease`
# produza APK byte-exato a partir do mesmo source — requisito do F-Droid.
#
# Uso:
#   docker build -t aulalogger-build -f tools/Dockerfile .
#   docker run --rm -v $(pwd):/workspace aulalogger-build \
#     bash -c "cd /workspace/app && npm ci && npx expo prebuild --platform android --no-install && cd android && ./gradlew assembleRelease"
# ============================================================================

FROM eclipse-temurin:17-jdk-jammy

ENV DEBIAN_FRONTEND=noninteractive
ENV NODE_VERSION=20.18.1
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Pacotes básicos
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip git wget cmake ninja-build python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Node.js (versão pinada)
RUN curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
        -o /tmp/node.tar.xz \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 \
    && rm /tmp/node.tar.xz \
    && node --version \
    && npm --version

# Android command line tools (versão pinada)
ARG CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
        -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d $ANDROID_HOME/cmdline-tools && \
    mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# Aceita licenças e instala SDK
RUN yes | sdkmanager --licenses > /dev/null && \
    sdkmanager --install \
        "platform-tools" \
        "platforms;android-34" \
        "build-tools;34.0.0" \
        "ndk;27.0.11718014" \
        "cmake;3.22.1" \
    > /dev/null

# Variáveis úteis
ENV GRADLE_OPTS="-Xmx4g -Dorg.gradle.daemon=false"

WORKDIR /workspace
