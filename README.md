# RxPlayer
[![Build Status](https://travis-ci.org/yuriykulikov/rxplayer.svg?branch=master)](https://travis-ci.org/yuriykulikov/rxplayer)
[![codecov](https://codecov.io/gh/yuriykulikov/rxplayer/branch/master/graph/badge.svg)](https://codecov.io/gh/yuriykulikov/rxplayer)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/09e529f2f858484d914bd734f7337bf0)](https://app.codacy.com/app/yuriy.kulikov.87/rxplayer?utm_source=github.com&utm_medium=referral&utm_content=yuriykulikov/rxplayer&utm_campaign=Badge_Grade_Dashboard)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[ ![Download](https://api.bintray.com/packages/yuriykulikov/rxplayer/entertainment-lib/images/download.svg) ](https://bintray.com/yuriykulikov/rxplayer/entertainment-lib/_latestVersion)
## Wait, what?
This is a learning project. You can use it to learn about dependency injection, testing and RxJava
in a near project similar set up. This means all the data is accesses from a external client over http
and passed to a java business logic which processed the request and passed the response to the client.
You must implement the business logic.

## Setup the environment
* Install git https://git-scm.com/)
* Install IntelliJ community edition (https://www.jetbrains.com/idea/download/)
* Install postman (https://www.getpostman.com/)
* Clone the git repository from the server in your working directory
```
git clone <Repo>
```
* Import the project as gradle project into IntelliJ

## Git Basics 

* Use always the git console to improve your knowledge 
* Creates a branch with you changes
```
git branch -b [username]/[branchname]
```
* Pushes the branch to the repo server
```
git push origin [username]/[branchname]
```

## Contents
This is gradle multiproject containing:
* entertainment-lib
* vertx-server
* vertx-example

### entertainment-lib
Simulates a simple audio and radio player. Create it by calling the EntertainmentService constructor.
```java
// create the instance of the entertainment
Entertainment entertainment =  new EntertainmentService(Schedulers.single());
```
Entertainment is a facade for multiple underlying subsystems. Subsystems are interconnected. Subsystems can be accessed
by calling corresponding methods.
```java
// get the audio
entertainment.audio()
  // start a connection
  .start(Audio.Connection.USB)
  // don't forget to subscribe
  .subscribe()
```

### vertx-server
Starts a simple http and websocket server. Create it by calling the VertxServer constructor. Server itself does not
implement any
logic, but it can be extended by supplying request handlers. See VertxServer javadoc for details.

### vertx-exampl
This is simple example with a launcher class. You can run it by calling
```bash
./gradlew vertx-example:run
```
To test the http interface you use postman.
You can access the vertx webserver on port 7780.


## Goal for the training
Depending on how much time you want to invest, different tasks can be accomplished. 
For a 4 day training the following tasks must be fullfilled:
- Try to run the example project (vertex-exmaple)
- Creates interface and code to handle the source switch between media and radio sources
- Creates interface and code to provide the current playing station or track
- Extends the current playing track or station with additional meta informations
- Introduce Dagger 2 for the code
- Write Unit tests for the services
- Write integration tests 

### New subproject
Create a new subproject (similar to vertx-example). Make sure to apply gradle application plugin. Start with a "Hello world"
application.
You will have to adjust gradle.settings for that.

### Implement an adapter between entertainment-lib and vertx-server
In your launcher, bootstrap entertainment-lib, vertx-server and an adapter, which connects both. Start small and add more
features as you go.

### Use dependency injection
Use Dagger2 to bootstrap the application. You can get rid of the EntertainmentService and instantiate it's parts in a
Dagger2 module.

### Tests
Create unit and medium (integration) tests for the whole application and it's parts.