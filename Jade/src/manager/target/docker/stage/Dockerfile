FROM openjdk:8 as stage0
WORKDIR /opt/docker
COPY opt /opt
USER root
RUN ["chmod", "-R", "u=rX,g=rX", "/opt/docker"]
RUN ["chmod", "u+x,g+x", "/opt/docker/bin/manager"]

FROM openjdk:8
USER root
RUN id -u demiourgos728 2> /dev/null || useradd --system --create-home --uid 1001 --gid 0 demiourgos728
WORKDIR /opt/docker
COPY --from=stage0 --chown=demiourgos728:root /opt/docker /opt/docker
EXPOSE 2551
USER 1001
ENTRYPOINT ["/opt/docker/bin/manager"]
CMD []
