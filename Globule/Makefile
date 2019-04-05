GLOBULE=~/.globule

setup:
	mkdir -p ${GLOBULE}/data
	touch ${GLOBULE}/peers.conf
	cp src/main/resources/GeoLite2-City.mmdb ${GLOBULE}

iptables:
	sudo iptables -A PREROUTING -t nat -p tcp --dport 80 -j REDIRECT --to-ports 8080

compile:
	sbt stage

pull:
	git pull

run:
	nohup ./target/universal/stage/bin/master-server > master.log &
	nohup ./target/universal/stage/bin/slave-server > slave.log &

kill:
	kill -9 $$(ps aux | grep masterServer | grep java | awk '{print $$2}' | head -n 1)
	kill -9 $$(ps aux | grep slaveServer | grep java | awk '{print $$2}' | head -n 1)

update: kill pull compile run

restart: kill run
