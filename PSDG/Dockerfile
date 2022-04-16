# Run Ubuntu 20.04 image
FROM ubuntu:20.04 AS runner

# LABEL about the custom image
LABEL version="0.1"
LABEL description="This is Docker Image for used for a PSDG implementation."

# Disable Prompt during packages installation
ARG DEBIAN_FRONTEND=noninteractive

# Update Ubuntu software repository
RUN apt-get update

# Install Java
RUN apt-get -y install git openjdk-17-jre-headless

# Clone repository from github
RUN mkdir /root/git_repo
WORKDIR /root/git_repo
RUN git clone https://github.com/tomasherq/DS-G4-Project.git


# Compile code with maven
FROM maven:3.8.4-openjdk-17-slim AS build
COPY src ./src
COPY pom.xml .
RUN mvn -f ./pom.xml clean compile package

# Copy compiled jar file to run folder
FROM runner AS cont_runner
WORKDIR /root/git_repo/DS-G4-Project
COPY --from=build ./target ./target
