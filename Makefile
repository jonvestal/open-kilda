# 'make' will build the latest and try to run it.
default: build-latest run-dev

build-base:
	docker build -t kilda/base-ubuntu:latest base/kilda-base-ubuntu/
	docker build -t kilda/base-floodlight:latest base/base-floodlight/
	docker build -f services/zookeeper/Dockerfile -t kilda/zookeeper:latest .
	docker build -f services/kafka/Dockerfile -t kilda/kafka:latest .
	docker build -f services/hbase/Dockerfile -t kilda/hbase:latest .
	docker build -f services/mininet/Dockerfile -t kilda/mininet:latest .
	docker build -f services/neo4j/Dockerfile -t kilda/neo4j:latest .
	docker build -f services/storm/Dockerfile -t kilda/storm:latest .
	docker build -f services/opentsdb/Dockerfile -t kilda/opentsdb:latest .

build-latest: build-base
	docker-compose build

run-dev:
	docker-compose up

up-test-mode:
	OK_TESTS="DISABLE_LOGIN" docker-compose up -d

up-log-mode: up-test-mode
	docker-compose logs -f

# keeping run-test for backwards compatibility (documentation) .. should depracate
run-test: up-log-mode

clean-sources:
	$(MAKE) -C services/src clean

unit:
	$(MAKE) -C services/src

run-speaker:
	$(MAKE) -C services/src run-speaker

clean-test:
	docker-compose down
	docker-compose rm -fv
	docker volume list -q | grep kilda | xargs docker volume rm

# NB: To override the default (localhost) kilda location, you can make a call like this:
#		cd services/src/atdd && \
#		mvn "-Dtest=org.bitbucket.openkilda.atdd.*" \
#			-DargLine="-Dkilda.host=127.0.0.1" \
#			test


kilda := 127.0.0.1
# make atdd kilda=<kilda host ip address>

atdd:
	mvn -f services/src/atdd/pom.xml -P$@ test -Dkilda.host="$(kilda)"

smoke:
	mvn -f services/src/atdd/pom.xml -P$@ test -Dkilda.host="$(kilda)"

perf:
	mvn -f services/src/atdd/pom.xml -P$@ test -Dkilda.host="$(kilda)"

sec:
	mvn -f services/src/atdd/pom.xml -P$@ test -Dkilda.host="$(kilda)"

.PHONY: default run-dev build-latest build-base
.PHONY: up-test-mode up-log-mode run-test clean-test
.PHONY: atdd smoke perf sec
.PHONY: clean-sources unit run-speaker
