image:
	docker build -t chubby-server ./

network:
	docker network create chubby-cell-network

run-server:
	docker run -it --network "chubby-cell-network" chubby-server server

run-client:
	docker run -it --network "chubby-cell-network" chubby-server client