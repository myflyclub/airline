FROM ubuntu:22.04 AS base

RUN apt-get update \
    && apt-get upgrade --yes \
    && apt-get install --yes openjdk-21-jdk \
        curl \
        screen \
        nano \
        sudo

RUN groupadd -r -g 1000 airline \
    && useradd -u 1000 -r -g 1000 -d /home/airline airline \
    && usermod -aG sudo airline \
    && echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers
RUN mkdir -p /home/airline/bin \
    && chown -vR airline:airline /home/airline \
    && chmod -vR 755 /home/airline
USER airline
ENV PATH="$PATH:/home/airline/bin"

RUN cd /tmp \
    && curl -fL https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz | gzip -d > cs \
    && chmod +x cs \
    && ./cs setup --yes --install-dir /home/airline/bin \
    && ./cs version \
    && sbt --version

WORKDIR /home/airline

ENTRYPOINT ["tail", "-f", "/dev/null"]