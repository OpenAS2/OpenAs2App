FROM openjdk:9
COPY . /usr/src/openas2
WORKDIR /usr/src/openas2
RUN ./mvnw clean package
RUN mkdir ./Runtime && unzip Server/dist/OpenAS2Server-*.zip -d Runtime
RUN ./mvnw clean
CMD ["bash", "/usr/src/openas2/Runtime/bin/start-openas2.sh"]
VOLUME ["/usr/src/openas2/Runtime/data","/usr/src/openas2/Runtime/config","/usr/src/openas2/Runtime/logs"]
