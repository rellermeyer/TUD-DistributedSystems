
/* usage:
run this file in nodejs with the following 2 arguments:
  - list of bucketsizes, this is recursive, like "[2, 4, 2]" results in 2 buckets having each 4 buckets having each 2 OSD nodes, resulting in 2*4*2=16 OSD nodes
  - list of selections for the placementrule, like "[1, 3, 1]"

The gen.conf file has the resulting configuration crushmap and the rules and has to be copied into the src/main/resources/application.conf and .../reference.conf file
The docker.yaml has the docker configuration and this has to be copied to docker/docker-compose.yaml file

More than 200 nodes is untested and could result in docker complaining about IP addresses
The bucket types are all straw except the lowest level of buckets, they are uniform
*/
const fs = require('fs');
const path = require('path');

if (process.argv.length !== 4) {
  console.log(
`CRUSH generator usage:
run this file in nodejs with the following 2 arguments:
  - list of bucketsizes, this is recursive, like "[2, 4, 2]" results in 2 buckets having each 4 buckets having each 2 OSD nodes, resulting in 2*4*2=16 OSD nodes
  - list of selections for the placementrule, like "[1, 3, 1]"

The gen.conf file has the resulting configuration crushmap and the rules and has to be copied into the src/main/application.conf and .../reference.conf file
The docker.yaml has the docker configuration and this has to be copied to docker/docker-compose.yaml file

More than 200 nodes is untested and could result in docker complaining about IP addresses
The bucket types are all straw except the lowest level of buckets, they are uniform
`
  );
  process.exit(0);
}

const buckets = JSON.parse(process.argv[2]);
const ruleArg = JSON.parse(process.argv[3]);
if (buckets.length !== ruleArg.length) {
  console.error('different lengths of buckets and rule');
  process.exit(1);
}
for (let i = 0; i < buckets.length; i++) {
  if (buckets[i] === ruleArg[i]) {
    console.log(`selecting all from all buckets, possible but kinda weird. At index ${i} bucket size ${buckets[i]}`);
  }
  if (buckets[i] === 0) {
    console.error('bucket cannot be 0. At index ', i);
    process.exit(1);
  }
  if (ruleArg[i] === 0) {
    console.log('cannot select 0 out of a bucket. At index ', i);
    process.exit(1);
  }
  if (ruleArg[i] > buckets[i]) {
    console.error('cannot select more than in the bucket. At index ', i);
    process.exit(1);
  }
}

function generateBucket (bucketSizes, osdIDGenerator, osdWeightGenerator) {
  if (bucketSizes.length === 1) {
    const finalBucket = { bucketType: 'uniform', items: [], id: osdIDGenerator() }; // uniform bucket at lowest level
    for (let i = 0; i < bucketSizes[0]; i++) {
      finalBucket.items.push({ bucketType: 'osd', id: osdIDGenerator(), weight: osdWeightGenerator() });
    }
    return finalBucket;
  }
  const bucketCopy = JSON.parse(JSON.stringify(bucketSizes)); // pass by reference fix
  const amount = bucketCopy.shift();
  const bucket = { bucketType: 'straw', items: [] }; // straw on any level with buckets as children
  for (let i = 0; i < amount; i++) {
    bucket.items.push(generateBucket(bucketCopy, osdIDGenerator, osdWeightGenerator));
  }
  return bucket;
}
let ids = -1; // <= this number amount of osds need to be made in docker config
function idGen () {
  ids++;
  return ids;
}
function weightGen () { // can be changed to have a distribution
  return 2000;
}

function generateRule (selectList) {
  const rule = [];
  for (let i = 0; i < selectList.length; i++) {
    rule.push({ type: 'select', amount: selectList[i] }); // select the amount of the list
  }
  rule.push({ type: 'emit' }); // and finally emit
  return rule;
}

let preGen = fs.readFileSync(path.join(__dirname, 'preGen.conf'), { encoding: 'utf8' });
preGen = preGen.replace('_MAPREPLACE_', JSON.stringify(generateBucket(buckets, idGen, weightGen)));
preGen = preGen.replace('_RULEREPLACE_', JSON.stringify(generateRule(ruleArg)));
fs.writeFileSync(path.join(__dirname, 'gen.conf'), preGen); // write it to the output file

// docker compose file generation
function dockerGenerate (num, spaceGenerator) {
  return `
  osd${num}:
    depends_on: [seed]
    tty: false
    image: "crush:0.1"
    entrypoint: "/opt/docker/bin/main"
    command: "osd ${num - 1} ${spaceGenerator(num)}"
    environment:
        CLUSTER_IP: \${CLUSTER_IP:-172.18.0.${num + 20}}
        CLUSTER_PORT: 25525
        CLUSTER_SEED_IP: 172.18.0.20
    networks:
    akka-network:
        ipv4_address: \${CLUSTER_IP:-172.18.0.${num + 20}}

`;
}
function spaceGen () {
  return 10;
}

let docker = [];
for (let i = 0; i <= ids; i++) { // for every osd node add an entry to the configuration
  docker.push(dockerGenerate(i + 1, spaceGen));
}
docker = docker.join('\n');
let preDock = fs.readFileSync(path.join(__dirname, 'preDocker.yaml'), { encoding: 'utf8' });
preDock = preDock.replace('_OSDS_', docker);

// N.B. the ID's are zero indexed, as such, add 1 to the `ids` variable (used by idGen)
preDock = preDock.replace('_ROOTCOMMAND_', `"root ${ids + 1}"`);
fs.writeFileSync(path.join(__dirname, 'docker.yaml'), preDock);
console.log(`done generating, written ${ids + 1} osd nodes`);
