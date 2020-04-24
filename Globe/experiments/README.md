## Globe Experiments
Evaluation of replication mechanism in Globe distributed system

### Required Configuration
In order to deploy an EC2 instance with Gradle, the following need to be configured:
- Your credentials must be available from a [default location](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/credentials.html) (e.g. ~/.aws/credentials) with a profile named "default"
- You must have `ssh` and `scp` installed on your local machine
- The public key for your local SSH instance must be [imported into AWS EC2 configuration](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html#how-to-generate-your-own-key-and-import-it-to-aws) as KeyPair with the name "globeReplicator"
- An EC2 Security Group with name "default" must be available and configured to allow inbound TCP traffic on port 22 (for SSH) and port 8080 (for RPC communication)

Since we use system tools with Gradle, this will work best on a Linux system with these tools installed.

### Usage
Since the experiments are implemented as automated tests using ScalaTest, they are part of the source code in this repository. So the evaluation needs to be performed from the `src/GlobeReplicator` folder, not the `experiments` folder.

To start an evaluation run, run `./evaluateSystem.sh` (make sure you have completed the required configuration, as described above). After every run, make sure to copy the 3 newly created .csv files to the `Evalution/Run x` folder (with x being the number of the run, e.g. "Run 1"). It's recommended to capture the output of the `./evaluateSystem.sh` command and copy that to the Run folders as well. 

Run the evaluation 3 times, then use `./processMeasurements.py` to process the measurements. This will create a file with the aggregated measurements for all runs for each type of measurement. It will validate the amount of measurements and output the result in `measurement_validation.txt`. Then it will plot each type of measurement and output it to a .png file. 

It's also recommended to verify that the .csv files contain the correct amount of measurements (20) for the correct system configurations (2, 3, 5 and 7 replicas). You can also check if the captured output contains any "Build Failed" errors though this will likely also be reflected in an incorrect amount of measurements and/or incorrect or missing system configurations.

### Results
Both the raw measurements for each run and the processed results are provided in the `experiments/Results` folder. These are the measurements and results used in the final report and the presentation. 
