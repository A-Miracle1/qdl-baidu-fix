FROM ubuntu:22.04

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/build-tools/30.0.3

# 安装基础工具
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    wget \
    unzip \
    git \
    && rm -rf /var/lib/apt/lists/*

# 下载并安装 Android SDK Command Line Tools
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip -q commandlinetools-linux-*.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm commandlinetools-linux-*.zip

# 接受许可并安装必要的 SDK 组件
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-23" "build-tools;30.0.3"

# 下载 R8/D8
RUN mkdir -p /root/build && \
    wget -q https://maven.google.com/com/android/tools/r8/8.2.42/r8-8.2.42.jar -O /root/build/r8.jar

# 设置工作目录
WORKDIR /workspace

# 复制构建脚本
COPY source/ /workspace/source/

# 默认命令
CMD ["bash", "-c", "cd /workspace/source && bash build.sh && echo '构建完成！APK 位于 /workspace/source/app.apk'"]
