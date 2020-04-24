## Globe
Evaluation of replication mechanism in Globe distributed system

### src
Contains the source code as well as the automated tests that run the experiments. These automated tests cannot be split off into the `experiments` folder. This is a multi-project Gradle project where the `src` folder should be considered the root of the Gradle project. 

### experiments
This contains the measurements and results that are used in our presentation and final paper. It also contains instructions on how to perform the evaluation. It does not contain the actual experiments since those are part of the source code, implemented as automated tests.
