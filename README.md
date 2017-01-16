# gradle-maven-sync-plugin
Sync your Gradle build with the dependencies from pom.xml

## Why?

Say you have a Maven project, and you want to use Gradle in parallel, just to try it.
Or you're a maintainer of OSS project and want to provide both Maven and Gradle.

Of course, you can run `gradle init`, and it will generate you a project. 
**BUT** you will have to synchronize your dependencies with `pom.xml` files until you fully migrate to Gradle.

Not funny, huh?

This plugin makes it possible to use `pom.xml` file as a single source of truth for your dependencies,
so you can use Gradle to build, run, test, do whatever you want with your project, but keep it in Maven.

## Prerequisites
First, do yourself a favor, use Gradle >= 2.1 and Java 8. Then make sure that `mvn` can be called from the command line. Yes, this plugin calls Maven. Suprise suprise!

## QuickStart
So... how to start?


The easiest way to setup your project from an existing Maven project - run `gradle init` (it will generate the project structure for you).
    
Then remove all `dependencies` blocks. Yes. All of them.

Now add these magical lines to `build.gradle` inside your root folder:
```gradle
plugins {
    // Add next line if you use `provided` scope
    // id 'nebula.provided-base' version '3.0.3'
    id 'com.github.bsideup.maven-sync' version '1.0.5' apply false
}

allprojects {
    apply plugin: 'java' // should be applied before
    // Add next line if you use `provided` scope
    // apply plugin: 'nebula.provided-base'
    apply plugin: 'com.github.bsideup.maven-sync'
}
```

Well, wasn't hard, right?

Now your Gradle build will use the dependencies from `pom.xml` files.

## How it works
First, we generate an effective model from Maven by calling `mvn help:effective-pom`. **It takes some time, ~1-2s**. It's Maven after all, wasn't designed to be fast.

Then we translate it to Gradle's dependency model.

What [was tested](src/test/resources/features) and should work:
- Excludes
- Multi-module builds (including cross-module references with `project(":some:module:path")`)
- Scopes

What **will not work**:
- Plugins configuration - no, not now. Dependencies only.
- Maven Wrapper. Yes, it's cool, will be supported soon.
- Optional dependencies - not yet implemented
- Classifiers - not yet implemented


## License
MIT License

Copyright (c) 2017 Sergei Egorov

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
