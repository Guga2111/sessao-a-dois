FROM node:20

ARG TZ
ENV TZ="$TZ"

ARG CLAUDE_CODE_VERSION=latest

# Install basic development tools and iptables/ipset
RUN apt-get update && apt-get install -y --no-install-recommends \
  less \
  git \
  procps \
  sudo \
  fzf \
  zsh \
  man-db \
  unzip \
  gnupg2 \
  gh \
  iptables \
  ipset \
  iproute2 \
  dnsutils \
  aggregate \
  jq \
  nano \
  vim \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# Ensure default node user has access to /usr/local/share
RUN mkdir -p /usr/local/share/npm-global && \
  chown -R node:node /usr/local/share

ARG USERNAME=node

# Persist bash history.
RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
  && mkdir /commandhistory \
  && touch /commandhistory/.bash_history \
  && chown -R $USERNAME /commandhistory

# Set `DEVCONTAINER` environment variable to help with orientation
ENV DEVCONTAINER=true

# Create workspace and config directories and set permissions
RUN mkdir -p /workspace /home/node/.claude && \
  chown -R node:node /workspace /home/node/.claude

WORKDIR /workspace

ARG GIT_DELTA_VERSION=0.18.2
RUN ARCH=$(dpkg --print-architecture) && \
  wget "https://github.com/dandavison/delta/releases/download/${GIT_DELTA_VERSION}/git-delta_${GIT_DELTA_VERSION}_${ARCH}.deb" && \
  sudo dpkg -i "git-delta_${GIT_DELTA_VERSION}_${ARCH}.deb" && \
  rm "git-delta_${GIT_DELTA_VERSION}_${ARCH}.deb"

# ---------------------------------------------------------------------------
# Java toolchain (api/)
#
# Sem isto o agente nao consegue rodar `./mvnw compile`/`test` e acaba
# "verificando" o backend so por analise estatica -- foi exatamente o que
# aconteceu no epico Flyway (7 stories seguidas de 5 commits de fix manual).
#
# Temurin 21 para casar com o `api/Dockerfile` de producao (eclipse-temurin:21).
# Baixado de release do GitHub, mesmo padrao do git-delta acima e dominio que o
# init-firewall.sh ja libera. Debian bookworm so tem openjdk-17 no apt, por isso
# o tarball em vez de pacote.
#
# O Maven vem instalado explicitamente porque o wrapper do projeto usa
# `distributionType=only-script` (ver api/.mvn/wrapper/maven-wrapper.properties):
# ele NAO embute o Maven, baixa o zip no primeiro uso. Ter o `mvn` na imagem
# evita esse bootstrap; `./mvnw` tambem funciona, gracas a liberacao do
# repo.maven.apache.org no init-firewall.sh.
# ---------------------------------------------------------------------------
ARG JAVA_VERSION=21.0.9+10
ARG MAVEN_VERSION=3.9.16

ENV JAVA_HOME=/opt/java
ENV MAVEN_HOME=/opt/maven
ENV PATH=$JAVA_HOME/bin:$MAVEN_HOME/bin:$PATH

RUN ARCH=$(dpkg --print-architecture) && \
  case "${ARCH}" in \
    amd64) TEMURIN_ARCH=x64 ;; \
    arm64) TEMURIN_ARCH=aarch64 ;; \
    *) echo "Arquitetura nao suportada: ${ARCH}" >&2 && exit 1 ;; \
  esac && \
  JAVA_TAG_ENC="jdk-$(echo "${JAVA_VERSION}" | sed 's/+/%2B/')" && \
  JAVA_FILE="OpenJDK21U-jdk_${TEMURIN_ARCH}_linux_hotspot_$(echo "${JAVA_VERSION}" | tr '+' '_').tar.gz" && \
  mkdir -p "${JAVA_HOME}" && \
  wget -q "https://github.com/adoptium/temurin21-binaries/releases/download/${JAVA_TAG_ENC}/${JAVA_FILE}" -O /tmp/jdk.tar.gz && \
  tar -xzf /tmp/jdk.tar.gz -C "${JAVA_HOME}" --strip-components=1 && \
  rm /tmp/jdk.tar.gz && \
  mkdir -p "${MAVEN_HOME}" && \
  wget -q "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -O /tmp/maven.tar.gz && \
  tar -xzf /tmp/maven.tar.gz -C "${MAVEN_HOME}" --strip-components=1 && \
  rm /tmp/maven.tar.gz && \
  java -version && mvn -version

# Cache do Maven pertence ao usuario nao-root, senao o primeiro build roda como
# root e deixa ~/.m2 sem permissao de escrita para o `node`.
RUN mkdir -p /home/node/.m2 && chown -R node:node /home/node/.m2

# Set up non-root user
USER node

# Install global packages
ENV NPM_CONFIG_PREFIX=/usr/local/share/npm-global
ENV PATH=$PATH:/usr/local/share/npm-global/bin

# Set the default shell to zsh rather than sh
ENV SHELL=/bin/zsh

# Set the default editor and visual
ENV EDITOR=nano
ENV VISUAL=nano

# Default powerline10k theme
ARG ZSH_IN_DOCKER_VERSION=1.2.0
RUN sh -c "$(wget -O- https://github.com/deluan/zsh-in-docker/releases/download/v${ZSH_IN_DOCKER_VERSION}/zsh-in-docker.sh)" -- \
  -p git \
  -p fzf \
  -a "source /usr/share/doc/fzf/examples/key-bindings.zsh" \
  -a "source /usr/share/doc/fzf/examples/completion.zsh" \
  -a "export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
  -x

# Install Claude
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}


# Copy and set up firewall script
COPY init-firewall.sh /usr/local/bin/
USER root
RUN chmod +x /usr/local/bin/init-firewall.sh && \
  echo "node ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" > /etc/sudoers.d/node-firewall && \
  chmod 0440 /etc/sudoers.d/node-firewall
USER node
