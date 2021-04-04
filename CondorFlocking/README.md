# FlockingCondors

Clusters of computers often deal with idle machines, while simultaneously other machines will queue a batch of jobs to execute. To tackle this issue, the Condor protocol was introduced, which will spread the load between the available machines. In some cases it might be desirable to distribute the load between multiple clusters, creating a flock of Condors. This report describes the design and implementation of this concept, as introduced by Epema et al Furthermore, experimentation was conducted to evaluate the performance of the system. Based on the results of this experimentation it was concluded that creating flocks of Condors is a viable solution, that can increase workstation utilization by up to 3.57 times.

By:
- Gerard van Alphen
- Christiaan van Orlé
- Ruben Visser
- Marc Zwart

## Instructions

Project dependencies are managed with `sbt`. To run the application locally, edit the `resources/application.conf` configuration file and run the `Launcher.scala`
