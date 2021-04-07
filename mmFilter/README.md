# mmFilter - Edge Computing

This folder contains the project files for the Distributed Systems course, 2020/2021 Q3 edition.

This project has been created by group 5. The team members are:

- Erik Sennema
- Zhewen Hu
- Yujin Zhu
- Jiaming Xu

This repository contains a scala implementation of the mmFilter. mmFilter is an algorithm that reduces the needed bandwith to the cloud, by using preposessing at the egde of the network. mmFilter was first proposed in https://doi.org/10.1145/3429357.3430518.

## Running the project
You need to first build and run docker containers.
* Build the docker images: 
	* For cloud server and scala part of the edge server, simply run `sbt docker` in the sbt console. The docker file and dependencies will appear in the "./target/docker" directory.
	* For edge device and python part of the edge server, their docker files are in "edge_device" and "pretrained_model" directories. To build corresponding docker images, run `docker image build --tag username/imagename .` inside the directory.
* Run the docker containers: 
	* With all the images already built, run the containers by using `docker run -dt --name containername --network host username/imagename`.
	* Except for the cloud server, you need to specify the port by `docker run -dt --name containername --network host -p 8080:80 username/imagename`.

