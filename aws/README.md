# Notes on memory allocation

The amount of memory available to YARN is less than the amount of memory available in each node, which is less than the EC2 instance memory b/c of system needs and the like. See [this AWS doc](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/emr-hadoop-task-config.html) for the exact values.

What's _not_ explained there, and poorly explained pretty much everywhere else, is that we can't fully allocate even the lesser amount available to YARN as the spark executor memory, because one of the nodes _also_ needs an extra 1 GB of memory for the Yarn Application Master. In the future I may allocate a Core node for this purpose and switch to yarn-cluster mode, but for now I'm running in yarn-client mode so the spark driver remains separate from the Yarn Application Master and runs on the Master node.
