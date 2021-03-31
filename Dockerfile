FROM openjdk:11 AS builder
COPY . /usr/src/openas2
WORKDIR /usr/src/openas2
RUN rm -f Server/dist/*
RUN rm -f Remote/dist/*
RUN rm -f Bundle/dist/*
RUN ./mvnw clean package
RUN mkdir ./Runtime && unzip Server/dist/OpenAS2Server-*.zip -d Runtime
RUN ./mvnw clean
COPY start-container.sh /usr/src/openas2/Runtime/bin/
RUN cd /usr/src/openas2/Runtime/bin && \
    chmod 755 *.sh && \
    cd /usr/src/openas2/Runtime && \
    mv config config_template

FROM openjdk:11-jre-slim
COPY --from=builder /usr/src/openas2/Runtime /opt/openas2
WORKDIR /opt/openas2
ENTRYPOINT /opt/openas2/bin/start-container.sh

