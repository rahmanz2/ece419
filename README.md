# ece419
This is distributed key value store written complete in java. There were multiple milestones for the course. However, all of the work for milestone 3 and 4 was just extension of milestone two. This is why there are only two folders, one for milestone 1 and one for milestone 2.

The key value store consists of a command line client => app_kvClient => KVClient.java
KVClient.java makes use of KVStore.java which can be found in the client folder

There is also an external configuration service which is used to manage the storage service from command line. This logic can be found in app_kvECS.

Finally, there is the kvServer. This is the scalable storage server. All of the logic can be found in app_kvServer.
