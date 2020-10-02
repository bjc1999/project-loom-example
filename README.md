# Some Performance Test for Project Loom
This repo is created to collect the simple PoC we created for Project Loom
The JDK version we ran is JDK 13.

## Build Docker Image
```
cd jdk-docker-image
docker build -t loom-jdk:13 .
cd ../jdk-maven-docker-image
docker build -t maven-loom:3.6.3-jdk-13 .
cd ../
docker build -t loom-test:v1 .
```
### Performance Monitor & Memory Usage:

With Project Loom Fibers:
![Image of fiber profiler](https://raw.githubusercontent.com/jiuntian/project-loom-example/fix/readme-image/img/Screenshot%202020-10-01%20at%202.38.08%20PM.png)
Memory Usage is 930M and CPU peaked at ~40%. 27 system threads created and completed in 7s.

With Java Thread:
![Image of thread profiler](https://raw.githubusercontent.com/jiuntian/project-loom-example/fix/readme-image/img/Screenshot%202020-10-01%20at%202.42.34%20PM.png)
The JVM crashed after created 4k threads.
