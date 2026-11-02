FROM eclipse-temurin:21-jdk-jammy AS builder
COPY . /usr/src/openas2
WORKDIR /usr/src/openas2
# To test Locally builder environment:
# docker run --rm -it -v $(pwd):/usr/src/openas2 eclipse-temurin:21-jdk-jammy bash
RUN apt update
RUN apt-get install -y unzip
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


FROM eclipse-temurin:21-jre-jammy
ENV OPENAS2_BASE=/opt/openas2
ENV OPENAS2_HOME=/opt/openas2
ENV OPENAS2_TMPDIR=/opt/openas2/temp
COPY --from=builder /usr/src/openas2/Runtime/bin ${OPENAS2_BASE}/bin
COPY --from=builder /usr/src/openas2/Runtime/lib ${OPENAS2_BASE}/lib
COPY --from=builder /usr/src/openas2/Runtime/resources ${OPENAS2_BASE}/resources
COPY --from=builder /usr/src/openas2/Runtime/config_template ${OPENAS2_HOME}/config_template
# Run as an unprivileged user rather than root, since the server is usually exposed to the internet.
# The user is in group 0 and everything it writes is group writable, so platforms that run the
# container under an arbitrary user ID in group 0 work as well. The ID is numeric in USER so that
# Kubernetes can verify runAsNonRoot.
RUN useradd --uid 1000 --gid 0 --home-dir ${OPENAS2_HOME} --no-create-home --shell /usr/sbin/nologin openas2 \
    && mkdir -p ${OPENAS2_BASE}/config ${OPENAS2_BASE}/data ${OPENAS2_BASE}/logs ${OPENAS2_TMPDIR} \
    && chown -R 1000:0 ${OPENAS2_BASE} \
    && chmod -R g=u ${OPENAS2_BASE}
USER 1000
WORKDIR $OPENAS2_HOME
ENTRYPOINT ["/opt/openas2/bin/start-container.sh"]