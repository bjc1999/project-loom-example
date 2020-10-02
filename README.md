# Some Performance Test for Project Loom
This repo is created to collect the simple PoC we created for Project Loom
The JDK version we ran is JDK 13.

### Performance Monitor & Memory Usage:

With Project Loom Fibers:
![Image of fiber profiler](./img/Screenshot 2020-10-01 at 2.38.08 PM.png)
Memory Usage is 930M and CPU peaked at ~40%. 27 system threads created and completed in 7s.

With Java Thread:
![Image of thread profiler](./img/Screenshot 2020-10-01 at 2.42.34 PM.png)
The JVM crashed after created 4k threads.
