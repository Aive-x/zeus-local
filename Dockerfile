FROM zeus-base:1.0.0
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo "Asia/Shanghai" > /etc/timezone
COPY target/*.jar /zeus.jar
COPY target/classes/middleware-crd.yaml /usr/local/zeus-pv/middleware-crd.yaml
COPY image-build/zeus-pv /usr/local/zeus-pv/
ENTRYPOINT [ "sh", "-c", "java -jar $JAVA_OPTS /zeus.jar" ]