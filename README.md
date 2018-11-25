# RxPlayer
[![Build Status](https://travis-ci.org/yuriykulikov/rxplayer.svg?branch=master)](https://travis-ci.org/yuriykulikov/rxplayer)
[![codecov](https://codecov.io/gh/yuriykulikov/rxplayer/branch/master/graph/badge.svg)](https://codecov.io/gh/yuriykulikov/rxplayer)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/09e529f2f858484d914bd734f7337bf0)](https://app.codacy.com/app/yuriy.kulikov.87/rxplayer?utm_source=github.com&utm_medium=referral&utm_content=yuriykulikov/rxplayer&utm_campaign=Badge_Grade_Dashboard)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[ ![Download](https://api.bintray.com/packages/yuriykulikov/rxplayer/entertainment-lib/images/download.svg) ](https://bintray.com/yuriykulikov/rxplayer/entertainment-lib/_latestVersion)
## Wait, what?

Congratulations! You have found the RxPlayer source code.
Depending on why have you found it, there are a few options:
```kotlin
when {
  you.`are a mainainter`() -> `do something useful`()
  you.`used the library and found this project`() -> `read the docs, check out the tests or contribute`()
  // just stumbled upon it in the Internet
  else -> `probably this is not interesting for you, but you are welcome to use it or to contribute to it`()
}
```
## Howto
### Gradle
```groovy
repositories {
    maven {
        url "https://dl.bintray.com/yuriykulikov/rxplayer"
    }
}

dependencies {
    implementation 'yuriykulikov.rxplayer:entertainment-lib:$version'
}
```
### Entertainment
Entertainment is a facade for multiple underlying subsystems. Subsystems are interconnected.
```java
// create the instance of the entertainment
Entertainment entertainment =  new EntertainmentService(Schedulers.single());
```
### Audio
```java
// get the audio
entertainment.audio()
  // start a connection
  .start(Audio.Connection.USB)
  // don't forget to subscribe
  .subscribe()
```
### Player
Player plays tracks if audio is available.
### Browser
Can be used to look up artists and albums. Watch out - it is slow, so think about caching the results.
